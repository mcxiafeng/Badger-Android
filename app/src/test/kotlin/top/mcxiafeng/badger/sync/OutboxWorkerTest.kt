package top.mcxiafeng.badger.sync

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive
import top.mcxiafeng.badger.network.BadgerJson
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.network.LocalHttpServer
import top.mcxiafeng.badger.network.OkHttpServerApi
import okhttp3.OkHttpClient

/**
 * OutboxWorker 端到端重放测试（真实 Room outbox + 真实 ServerApi → LocalHttpServer）。
 *
 * 覆盖 Checkpoint 2 场景：离线改名再改 bio，Worker 重放的 HTTP body 保留已排队 name。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OutboxWorkerTest {
    private lateinit var database: AppDatabase
    private lateinit var store: OutboxStore
    private lateinit var server: LocalHttpServer
    private lateinit var worker: OutboxWorker

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = OutboxStore(database)
        server = LocalHttpServer().also { it.start() }
        val api = OkHttpServerApi(
            baseUrl = server.baseUrl,
            http = OkHttpClient(),
            tokenProvider = { null },
            outboxStore = store,
            outboxScheduler = mockk(relaxed = true),
        )
        runCatching { GlobalContext.stopKoin() }
        GlobalContext.startKoin {
            modules(module {
                single { store }
                single { api }
                single {
                    // [T16a] OutboxWorker 委托 SyncEngine.pushOnce；pull 侧 DAO 在本测试不触网
                    SyncEngine(
                        serverApi = api,
                        outboxStore = store,
                        syncCursorDao = database.syncCursorDao(),
                        contactCacheDao = database.contactCacheDao(),
                        contactPlatformCacheDao = database.contactPlatformCacheDao(),
                        tagCacheDao = database.tagCacheDao(),
                        cardCollectionCacheDao = database.cardCollectionCacheDao(),
                        contactTagCacheDao = database.contactTagCacheDao(),
                        personProfileCacheDao = database.personProfileCacheDao(),
                    )
                }
            })
        }
        worker = OutboxWorker(RuntimeEnvironment.getApplication(), mockk(relaxed = true))
    }

    @After
    fun tearDown() {
        database.close()
        server.stop()
        GlobalContext.stopKoin()
    }

    @Test
    fun replay_mergedPartialPuts_keepsQueuedName(): Unit = runBlocking {
        // 离线先改名（name 非空、profile 缺省），再改 bio（name=null 的半载 PATCH）
        store.enqueue(
            EntityKind.PERSON, 7L, "p-uuid", OutboxOpType.PATCH,
            buildJsonObject { put("name", "新名字") },
        )
        store.enqueue(
            EntityKind.PERSON, 7L, "p-uuid", OutboxOpType.PATCH,
            buildJsonObject {
                put("profile", buildJsonObject { put("description", "P") })
            },
        )
        server.enqueue(200, """{"code":200,"data":null}""")

        val result = worker.doWork()

        // 失败诊断优先断言：若有残留行，消息会带出 lastError
        val leftover = store.getReady(now = System.currentTimeMillis() + OutboxStore.MAX_BACKOFF_MILLIS)
        assertThat(leftover.map { it.lastError }).isEmpty()
        assertThat(result).isEqualTo(androidx.work.ListenableWorker.Result.success())
        assertThat(server.lastPath.get()).isEqualTo("/api/user/persons/p-uuid")
        val body = BadgerJson.parseToJsonElement(server.lastBody.get()) as JsonObject
        assertThat(body["name"]?.jsonPrimitive?.content).isEqualTo("新名字")
        assertThat(top.mcxiafeng.badger.network.stringOrNull(body["profile"] as JsonObject, "description")).isEqualTo("P")
    }

    @Test
    fun replay_dispatchesTagPatchByKind(): Unit = runBlocking {
        store.enqueue(
            EntityKind.TAG, 3L, "t-uuid", OutboxOpType.PATCH,
            buildJsonObject { put("name", "新标签") },
        )
        server.enqueue(200, """{"code":200,"data":null}""")

        val result = worker.doWork()

        assertThat(result).isEqualTo(androidx.work.ListenableWorker.Result.success())
        assertThat(server.lastPath.get()).isEqualTo("/api/user/tags/t-uuid")
        val body = BadgerJson.parseToJsonElement(server.lastBody.get()) as JsonObject
        assertThat(body["name"]?.jsonPrimitive?.content).isEqualTo("新标签")
        assertThat(store.getReady()).isEmpty()
    }

    @Test
    fun replay_failure_keepsRowPendingWithAttempt(): Unit = runBlocking {
        store.enqueue(
            EntityKind.PERSON, 7L, "p-uuid", OutboxOpType.PATCH,
            buildJsonObject { put("name", "新名字") },
        )
        server.enqueue(500, """{"code":500,"message":"boom"}""")

        val result = worker.doWork()

        assertThat(result).isEqualTo(androidx.work.ListenableWorker.Result.retry())
        val rows = store.getReady(now = System.currentTimeMillis() + OutboxStore.MAX_BACKOFF_MILLIS)
        assertThat(rows).hasSize(1)
        assertThat(rows.single().attempts).isEqualTo(1)
        assertThat(rows.single().payload["name"]?.jsonPrimitive?.content).isEqualTo("新名字")
    }
}
