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
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters
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
import top.mcxiafeng.badger.data.repository.UserProfileRepositoryImpl
import top.mcxiafeng.badger.network.LocalHttpServer
import top.mcxiafeng.badger.network.OkHttpServerApi
import okhttp3.OkHttpClient

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
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
        val api = OkHttpServerApi(
            baseUrl = server.baseUrl,
            http = OkHttpClient(),
            tokenProvider = { null },
            outboxStore = store,
            outboxScheduler = mockk(relaxed = true),
        )
        engine = SyncEngine(
            serverApi = api,
            outboxStore = store,
            db = database,
            syncCursorDao = database.syncCursorDao(),
            contactCacheDao = database.contactCacheDao(),
            contactPlatformCacheDao = database.contactPlatformCacheDao(),
            tagCacheDao = database.tagCacheDao(),
            cardCollectionCacheDao = database.cardCollectionCacheDao(),
            contactTagCacheDao = database.contactTagCacheDao(),
            personProfileCacheDao = database.personProfileCacheDao(),
            userProfileRepository = UserProfileRepositoryImpl(
                userProfileCacheDao = database.userProfileCacheDao(),
                serverApi = api,
            ),
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
        BadgerJson.parseToJsonElement(server.requestBodies[index]) as JsonObject

    

    @Test
    fun createOnPush_failureThenRetry_reusesSameClientUuid() = runBlocking {
        val pending = insertPendingPerson(uuid = "client-a")
        
        store.enqueue(EntityKind.PERSON, pending.id, "client-a", OutboxOpType.CREATE, JsonObject(emptyMap()))
        assertThat(store.enqueue(EntityKind.PERSON, pending.id, "client-a", OutboxOpType.CREATE, JsonObject(emptyMap())))
            .isEqualTo(OutboxEnqueueResult.IgnoredDuplicateCreate)
        server.enqueue(500, """{"code":500,"message":"boom"}""")

        val first = engine.pushOnce()
        assertThat(first.failedOps).isEqualTo(1)

        
        server.enqueue(200, """{"code":200,"data":{"uuid":"srv-a"}}""")
        server.enqueue(200, """{"code":200,"data":{"version":0,"changes":[],"hasMore":false}}""")
        val second = engine.syncOnce()

        assertThat(second.pushedOps).isEqualTo(1)
        
        assertThat(server.requestPaths).containsExactly(
            "/api/user/persons", "/api/user/persons", "/api/user/sync?since=0&limit=500"
        ).inOrder()
        
        assertThat(bodyOf(0)["uuid"]?.jsonPrimitive?.content).isEqualTo("client-a")
        assertThat(bodyOf(1)["uuid"]?.jsonPrimitive?.content).isEqualTo("client-a")
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
        store.enqueue(EntityKind.TAG, id, "client-t", OutboxOpType.CREATE, JsonObject(emptyMap()))
        server.enqueue(400, """{"code":400,"message":"unknown field uuid"}""")
        server.enqueue(200, """{"code":200,"data":{"uuid":"srv-t"}}""")

        val outcome = engine.pushOnce()

        assertThat(outcome.pushedOps).isEqualTo(1)
        
        assertThat(server.requestPaths).containsExactly("/api/user/tags", "/api/user/tags").inOrder()
        assertThat(bodyOf(0)["uuid"]?.jsonPrimitive?.content).isEqualTo("client-t")
        assertThat(bodyOf(1).get("uuid")).isNull()
        assertThat(bodyOf(1)["name"]?.jsonPrimitive?.content).isEqualTo("同事")
        val synced = database.tagCacheDao().getTagById(id)!!
        assertThat(synced.serverId).isEqualTo("srv-t")
        assertThat(synced.isLocalOnly).isFalse()
    }

    @Test
    fun createOnPush_syncedEntity_skipsPost() = runBlocking {
        val pending = insertPendingPerson(uuid = "client-a")
        database.contactCacheDao().updateContact(pending.copy(serverId = "srv-a", isLocalOnly = false))
        store.enqueue(EntityKind.PERSON, pending.id, "client-a", OutboxOpType.CREATE, JsonObject(emptyMap()))

        val outcome = engine.pushOnce()

        assertThat(outcome.pushedOps).isEqualTo(1)
        assertThat(server.requestCount.get()).isEqualTo(0)
        assertThat(store.getReady()).isEmpty()
    }

    

    @Test
    fun pushOnce_replaysCreateBeforePatch_andBackfillsPatchRemoteId() = runBlocking {
        val pending = insertPendingPerson(uuid = "client-a")
        
        store.enqueue(
            EntityKind.PERSON, pending.id, "client-a", OutboxOpType.PATCH,
            buildJsonObject { put("name", "新名字") },
        )
        store.enqueue(EntityKind.PERSON, pending.id, "client-a", OutboxOpType.CREATE, JsonObject(emptyMap()))
        server.enqueue(200, """{"code":200,"data":{"uuid":"srv-a"}}""")
        server.enqueue(200, """{"code":200,"data":null}""")

        val outcome = engine.pushOnce()

        assertThat(outcome.pushedOps).isEqualTo(2)
        assertThat(server.requestPaths).containsExactly("/api/user/persons", "/api/user/persons/srv-a").inOrder()
        assertThat(bodyOf(1)["name"]?.jsonPrimitive?.content).isEqualTo("新名字")
        assertThat(store.getReady()).isEmpty()
    }

    @Test
    fun pushOnce_createFails_patchBlockedWithoutAttemptsPenalty() = runBlocking {
        val pending = insertPendingPerson(uuid = "client-a")
        store.enqueue(EntityKind.PERSON, pending.id, "client-a", OutboxOpType.CREATE, JsonObject(emptyMap()))
        store.enqueue(
            EntityKind.PERSON, pending.id, "client-a", OutboxOpType.PATCH,
            buildJsonObject { put("name", "新名字") },
        )
        server.enqueue(500, """{"code":500,"message":"boom"}""")

        val outcome = engine.pushOnce()

        assertThat(outcome.failedOps).isEqualTo(1)
        val rows = store.getReady(now = System.currentTimeMillis() + OutboxStore.MAX_BACKOFF_MILLIS)
        val byOp = rows.associateBy { it.op }
        assertThat(byOp.getValue(OutboxOpType.CREATE).attempts).isEqualTo(1)
        
        assertThat(byOp.getValue(OutboxOpType.PATCH).attempts).isEqualTo(0)
    }

    @Test
    fun pushOnce_memberPayloadPersonUuid_backfilledAfterPersonCreate() = runBlocking {
        val pending = insertPendingPerson(uuid = "client-a")
        val tag = insertSyncedTag(uuid = "srv-t")
        store.enqueue(EntityKind.PERSON, pending.id, "client-a", OutboxOpType.CREATE, JsonObject(emptyMap()))
        store.enqueue(
            EntityKind.TAG, tag.id, "srv-t", OutboxOpType.MEMBER_ADD,
            buildJsonObject { put("personUuid", "client-a") },
        )
        server.enqueue(200, """{"code":200,"data":{"uuid":"server-a"}}""")
        server.enqueue(200, """{"code":200,"data":null}""")

        val outcome = engine.pushOnce()

        assertThat(outcome.pushedOps).isEqualTo(2)
        
        assertThat(server.requestPaths)
            .containsExactly("/api/user/persons", "/api/user/tags/srv-t/members/server-a")
            .inOrder()
        assertThat(store.getReady()).isEmpty()
    }

    

    @Test
    fun syncOnce_backfillsLocalOnlyRows_andPushesThemUp() = runBlocking {
        
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

        
        server.enqueue(200, """{"code":200,"data":{"version":0,"changes":[],"hasMore":false}}""")
        val again = engine.syncOnce()
        assertThat(again.pushedOps).isEqualTo(0)
        assertThat(server.requestCount.get()).isEqualTo(5)
    }
}
