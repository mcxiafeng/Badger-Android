package top.mcxiafeng.badger.sync

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity
import top.mcxiafeng.badger.network.LocalHttpServer
import top.mcxiafeng.badger.network.ServerApi
import okhttp3.OkHttpClient

/**
 * [T14/T16a/T16c] SyncEngine 端到端测试（真实 Room + 真实 ServerApi → LocalHttpServer）。
 *
 * 覆盖 T14 验收：
 * - 三种实体离线创建入队 CREATE 并被 pushOnce POST；
 * - clientUuid 复用（同实体重试用同一 uuid，禁止重新生成）；
 * - Tag POST 400 降级去 uuid 重试一次；
 * - 已 Synced 实体不 POST；
 * - 失败/未知结局保留 PendingCreate（无 FAILED_PERMANENT）。
 *
 * 覆盖 Checkpoint 3 验收：离线建联系人/名片夹，不编辑，syncOnce 能推上去（T16c 回填闭环）；
 * MEMBER 行 payload 的 personUuid 在 Person CREATE 兑现新 uuid 后被回填。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SyncEngineTest {
    private lateinit var database: AppDatabase
    private lateinit var store: OutboxStore
    private lateinit var server: LocalHttpServer
    private lateinit var engine: SyncEngine

    @Before
    fun setUp(): Unit = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = OutboxStore(database)
        server = LocalHttpServer().also { it.start() }
        val api = ServerApi(
            baseUrl = server.baseUrl,
            http = OkHttpClient(),
            tokenProvider = { null },
            outboxStore = store,
            outboxScheduler = mockk(relaxed = true),
        )
        engine = SyncEngine(
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
        runCatching { GlobalContext.stopKoin() }
        GlobalContext.startKoin {
            modules(module {
                single { store }
                single { api }
                single { engine }
            })
        }
    }

    @After
    fun tearDown() {
        database.close()
        server.stop()
        GlobalContext.stopKoin()
    }

    private suspend fun insertPendingPerson(
        uuid: String = "client-a",
        name: String = "张三",
    ): ContactCacheEntity {
        val id = database.contactCacheDao().insertContact(
            ContactCacheEntity(
                id = 0L, serverId = uuid, name = name,
                createTime = 1L, updateTime = 1L, isLocalOnly = true,
            )
        )
        return ContactCacheEntity(
            id = id, serverId = uuid, name = name,
            createTime = 1L, updateTime = 1L, isLocalOnly = true,
        )
    }

    private suspend fun insertSyncedTag(uuid: String = "srv-t"): TagCacheEntity {
        val entity = TagCacheEntity(
            id = 0L, serverId = uuid, name = "朋友", createTime = 1L, isLocalOnly = false,
        )
        val id = database.tagCacheDao().insertTag(entity)
        return entity.copy(id = id)
    }

    private fun bodyOf(index: Int): JsonObject =
        JsonParser.parseString(server.requestBodies[index]).asJsonObject

    // ============ T14：CREATE 重放 + uuid 生命周期 ============

    @Test
    fun createOnPush_failureThenRetry_reusesSameClientUuid() = runBlocking {
        val pending = insertPendingPerson(uuid = "client-a")
        // 同实体重复入队 CREATE：mergeKey 幂等忽略
        store.enqueue(EntityKind.PERSON, pending.id, "client-a", OutboxOpType.CREATE, JsonObject())
        assertThat(store.enqueue(EntityKind.PERSON, pending.id, "client-a", OutboxOpType.CREATE, JsonObject()))
            .isEqualTo(OutboxEnqueueResult.IgnoredDuplicateCreate)
        server.enqueue(500, """{"code":500,"message":"boom"}""")

        val first = engine.pushOnce()
        assertThat(first.failedOps).isEqualTo(1)

        // 重试走 syncOnce（「立即同步」语义：无视退避窗口立即重试）；末尾附带一次空 pull
        server.enqueue(200, """{"code":200,"data":{"uuid":"srv-a"}}""")
        server.enqueue(200, """{"code":200,"data":{"version":0,"changes":[],"hasMore":false}}""")
        val second = engine.syncOnce()

        assertThat(second.pushedOps).isEqualTo(1)
        // 前两次 POST persons：push 部分；末尾 GET sync：syncOnce 的 pull 部分
        assertThat(server.requestPaths).containsExactly(
            "/api/user/persons", "/api/user/persons", "/api/user/sync?since=0&limit=500"
        ).inOrder()
        // 两次重放（失败重试）必须复用同一个 clientUuid，禁止重新生成
        assertThat(bodyOf(0).get("uuid").asString).isEqualTo("client-a")
        assertThat(bodyOf(1).get("uuid").asString).isEqualTo("client-a")
        val synced = database.contactCacheDao().getContactById(pending.id)!!
        assertThat(synced.serverId).isEqualTo("srv-a")
        assertThat(synced.isLocalOnly).isFalse()
        assertThat(store.getReady(now = System.currentTimeMillis() + OutboxStore.MAX_BACKOFF_MILLIS)).isEmpty()
    }

    @Test
    fun createOnPush_tag400_downgradesOnceWithoutUuid() = runBlocking {
        val id = database.tagCacheDao().insertTag(
            TagCacheEntity(
                id = 0L, serverId = "client-t", name = "同事",
                colorHash = "0xFF1976D2", createTime = 1L, isLocalOnly = true,
            )
        )
        store.enqueue(EntityKind.TAG, id, "client-t", OutboxOpType.CREATE, JsonObject())
        server.enqueue(400, """{"code":400,"message":"unknown field uuid"}""")
        server.enqueue(200, """{"code":200,"data":{"uuid":"srv-t"}}""")

        val outcome = engine.pushOnce()

        assertThat(outcome.pushedOps).isEqualTo(1)
        // 400 降级只发生一次：第 1 个请求带 uuid，第 2 个不带
        assertThat(server.requestPaths).containsExactly("/api/user/tags", "/api/user/tags").inOrder()
        assertThat(bodyOf(0).get("uuid").asString).isEqualTo("client-t")
        assertThat(bodyOf(1).get("uuid")).isNull()
        assertThat(bodyOf(1).get("name").asString).isEqualTo("同事")
        val synced = database.tagCacheDao().getTagById(id)!!
        assertThat(synced.serverId).isEqualTo("srv-t")
        assertThat(synced.isLocalOnly).isFalse()
    }

    @Test
    fun createOnPush_syncedEntity_skipsPost() = runBlocking {
        val pending = insertPendingPerson(uuid = "client-a")
        database.contactCacheDao().updateContact(pending.copy(serverId = "srv-a", isLocalOnly = false))
        store.enqueue(EntityKind.PERSON, pending.id, "client-a", OutboxOpType.CREATE, JsonObject())

        val outcome = engine.pushOnce()

        assertThat(outcome.pushedOps).isEqualTo(1)
        assertThat(server.requestCount.get()).isEqualTo(0)
        assertThat(store.getReady()).isEmpty()
    }

    // ============ T16a：顺序 + BlockedOnCreate ============

    @Test
    fun pushOnce_replaysCreateBeforePatch_andBackfillsPatchRemoteId() = runBlocking {
        val pending = insertPendingPerson(uuid = "client-a")
        // PATCH 先入队（FIFO 在前），CREATE 后入队：优先级必须让 CREATE 先重放
        store.enqueue(
            EntityKind.PERSON, pending.id, "client-a", OutboxOpType.PATCH,
            JsonObject().apply { addProperty("name", "新名字") },
        )
        store.enqueue(EntityKind.PERSON, pending.id, "client-a", OutboxOpType.CREATE, JsonObject())
        server.enqueue(200, """{"code":200,"data":{"uuid":"srv-a"}}""")
        server.enqueue(200, """{"code":200,"data":null}""")

        val outcome = engine.pushOnce()

        assertThat(outcome.pushedOps).isEqualTo(2)
        assertThat(server.requestPaths).containsExactly("/api/user/persons", "/api/user/persons/srv-a").inOrder()
        assertThat(bodyOf(1).get("name").asString).isEqualTo("新名字")
        assertThat(store.getReady()).isEmpty()
    }

    @Test
    fun pushOnce_createFails_patchBlockedWithoutAttemptsPenalty() = runBlocking {
        val pending = insertPendingPerson(uuid = "client-a")
        store.enqueue(EntityKind.PERSON, pending.id, "client-a", OutboxOpType.CREATE, JsonObject())
        store.enqueue(
            EntityKind.PERSON, pending.id, "client-a", OutboxOpType.PATCH,
            JsonObject().apply { addProperty("name", "新名字") },
        )
        server.enqueue(500, """{"code":500,"message":"boom"}""")

        val outcome = engine.pushOnce()

        assertThat(outcome.failedOps).isEqualTo(1)
        val rows = store.getReady(now = System.currentTimeMillis() + OutboxStore.MAX_BACKOFF_MILLIS)
        val byOp = rows.associateBy { it.op }
        assertThat(byOp.getValue(OutboxOpType.CREATE).attempts).isEqualTo(1)
        // PATCH 不是失败，只是等 CREATE 先兑现：不记 attempts
        assertThat(byOp.getValue(OutboxOpType.PATCH).attempts).isEqualTo(0)
    }

    @Test
    fun pushOnce_memberPayloadPersonUuid_backfilledAfterPersonCreate() = runBlocking {
        val pending = insertPendingPerson(uuid = "client-a")
        val tag = insertSyncedTag(uuid = "srv-t")
        store.enqueue(EntityKind.PERSON, pending.id, "client-a", OutboxOpType.CREATE, JsonObject())
        store.enqueue(
            EntityKind.TAG, tag.id, "srv-t", OutboxOpType.MEMBER_ADD,
            JsonObject().apply { addProperty("personUuid", "client-a") },
        )
        server.enqueue(200, """{"code":200,"data":{"uuid":"server-a"}}""")
        server.enqueue(200, """{"code":200,"data":null}""")

        val outcome = engine.pushOnce()

        assertThat(outcome.pushedOps).isEqualTo(2)
        // 成员子接口路径里的 personUuid 必须是 CREATE 兑现后的 server uuid，不再是 clientUuid
        assertThat(server.requestPaths)
            .containsExactly("/api/user/persons", "/api/user/tags/srv-t/members/server-a")
            .inOrder()
        assertThat(store.getReady()).isEmpty()
    }

    // ============ T16c + Checkpoint 3 验收：离线创建 → 立即同步 → 推上去 ============

    @Test
    fun syncOnce_backfillsLocalOnlyRows_andPushesThemUp() = runBlocking {
        // 离线创建（含历史遗留的 serverId=NULL 行），完全没有 outbox 行
        val personId = database.contactCacheDao().insertContact(
            ContactCacheEntity(
                id = 0L, serverId = null, name = "离线联系人",
                createTime = 1L, updateTime = 1L, isLocalOnly = true,
            )
        )
        val tagId = database.tagCacheDao().insertTag(
            TagCacheEntity(id = 0L, serverId = null, name = "离线标签", createTime = 1L, isLocalOnly = true)
        )
        val collectionId = database.cardCollectionCacheDao().insertCollection(
            CardCollectionCacheEntity(
                id = 0L, serverId = null, name = "离线名片夹", createTime = 1L, isLocalOnly = true,
            )
        )
        server.enqueue(200, """{"code":200,"data":{"uuid":"srv-p"}}""")
        server.enqueue(200, """{"code":200,"data":{"uuid":"srv-t"}}""")
        server.enqueue(200, """{"code":200,"data":{"uuid":"srv-c"}}""")
        server.enqueue(200, """{"code":200,"data":{"version":0,"changes":[],"hasMore":false}}""")

        val result = engine.syncOnce()

        assertThat(result.pushedOps).isEqualTo(3)
        assertThat(server.requestPaths)
            .containsExactly(
                "/api/user/persons",
                "/api/user/tags",
                "/api/user/collections",
                "/api/user/sync?since=0&limit=500",
            ).inOrder()
        assertThat(database.contactCacheDao().getContactById(personId)!!.serverId).isEqualTo("srv-p")
        assertThat(database.tagCacheDao().getTagById(tagId)!!.serverId).isEqualTo("srv-t")
        assertThat(database.cardCollectionCacheDao().getCollectionById(collectionId)!!.serverId).isEqualTo("srv-c")

        // 回填幂等：再来一轮不重复入队（只有一次 sync pull 请求）
        server.enqueue(200, """{"code":200,"data":{"version":0,"changes":[],"hasMore":false}}""")
        val again = engine.syncOnce()
        assertThat(again.pushedOps).isEqualTo(0)
        assertThat(server.requestCount.get()).isEqualTo(5)
    }
}
