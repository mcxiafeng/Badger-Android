package top.mcxiafeng.badger.sync

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import top.mcxiafeng.badger.data.cache.dao.CardCollectionCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactPlatformCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactTagCacheDao
import top.mcxiafeng.badger.data.cache.dao.PersonProfileCacheDao
import top.mcxiafeng.badger.data.cache.dao.SyncCursorDao
import top.mcxiafeng.badger.data.cache.dao.TagCacheDao
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.network.PersonDto
import top.mcxiafeng.badger.network.ProfileDto
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.network.SyncChange
import top.mcxiafeng.badger.network.SyncPage
import java.io.IOException

/**
 * [T16b] SyncEngine PullLoop 回归测试（原 SyncRepositoryTest 改挂，doPull 原样搬运）：
 * 游标安全、缺行恢复、未知变更和分页边界。
 */
class SyncPullLoopTest {

    private lateinit var serverApi: ServerApi
    private lateinit var syncCursorDao: SyncCursorDao
    private lateinit var contactCacheDao: ContactCacheDao
    private lateinit var contactPlatformCacheDao: ContactPlatformCacheDao
    private lateinit var tagCacheDao: TagCacheDao
    private lateinit var cardCollectionCacheDao: CardCollectionCacheDao
    private lateinit var contactTagCacheDao: ContactTagCacheDao
    private lateinit var personProfileCacheDao: PersonProfileCacheDao
    private lateinit var outboxStore: OutboxStore
    private lateinit var engine: SyncEngine

    @Before
    fun setup() {
        serverApi = mockk(relaxed = true)
        outboxStore = mockk(relaxed = true)
        syncCursorDao = mockk(relaxed = true)
        contactCacheDao = mockk(relaxed = true)
        contactPlatformCacheDao = mockk(relaxed = true)
        tagCacheDao = mockk(relaxed = true)
        cardCollectionCacheDao = mockk(relaxed = true)
        contactTagCacheDao = mockk(relaxed = true)
        personProfileCacheDao = mockk(relaxed = true)
        engine = SyncEngine(
            serverApi = serverApi,
            outboxStore = outboxStore,
            syncCursorDao = syncCursorDao,
            contactCacheDao,
            contactPlatformCacheDao,
            tagCacheDao,
            cardCollectionCacheDao,
            contactTagCacheDao = contactTagCacheDao,
            personProfileCacheDao = personProfileCacheDao,
        )
    }

    private fun addPersonChange(version: Long, uuid: String, name: String): SyncChange {
        val json = buildJsonObject {
            put("uuid", uuid)
            put("name", name)
            put("createTime", "2026-01-01 00:00:00")
            put("updateTime", "2026-01-01 00:00:00")
            put("self", false)
            put("profile", buildJsonObject {
                put("contactMap", buildJsonObject { put("qq", "123") })
            })
        }
        return SyncChange(
            version = version,
            type = "ADD",
            objectName = "Person",
            objectId = uuid,
            fieldName = null,
            value = json,
        )
    }

    private fun remotePerson(uuid: String, name: String): PersonDto = PersonDto(
        uuid = uuid,
        name = name,
        profile = ProfileDto(contactMap = mapOf("qq" to "123456")),
        createTime = "2026-01-01 00:00:00",
        updateTime = "2026-01-01 00:00:00",
        self = false,
    )

    @Test
    fun pullOnce_emptyChanges_returnsDoneZeroCursor() = runTest {
        coEvery { serverApi.syncSince(0L) } returns SyncPage(version = 0L, changes = emptyList(), hasMore = false)

        val result = engine.pullOnce()

        assertThat(result).isEqualTo(SyncPullResult.Done(applied = 0, cursor = 0L))
        coVerify(exactly = 0) { syncCursorDao.upsert(any()) }
    }

    @Test
    fun pullOnce_singleBatch_appliesChangesAndAdvancesCursor() = runTest {
        val change = addPersonChange(version = 5L, uuid = "p1", name = "张三")
        coEvery { serverApi.syncSince(0L) } returns SyncPage(version = 5L, changes = listOf(change), hasMore = false)
        coEvery { contactCacheDao.getContactByServerId("p1") } returns null
        coEvery { contactCacheDao.insertContact(any()) } returns 7L

        val result = engine.pullOnce()

        assertThat(result).isEqualTo(SyncPullResult.Done(applied = 1, cursor = 5L))
        coVerify { contactCacheDao.insertContact(match { it.serverId == "p1" && it.name == "张三" }) }
        coVerify { contactPlatformCacheDao.deleteByContact(7L) }
        coVerify { contactPlatformCacheDao.insertPlatforms(match { it.size == 1 }) }
        coVerify { contactCacheDao.bumpContact(7L) }
        coVerify { syncCursorDao.upsert(match { it.lastVersion == 5L }) }
    }

    @Test
    fun pullOnce_hasMore_keepsPullingUntilFalse() = runTest {
        val c1 = addPersonChange(version = 3L, uuid = "p1", name = "甲")
        val c2 = addPersonChange(version = 6L, uuid = "p2", name = "乙")
        coEvery { serverApi.syncSince(0L) } returns SyncPage(version = 3L, changes = listOf(c1), hasMore = true)
        coEvery { serverApi.syncSince(3L) } returns SyncPage(version = 6L, changes = listOf(c2), hasMore = false)
        coEvery { contactCacheDao.getContactByServerId(any()) } returns null
        coEvery { contactCacheDao.insertContact(any()) } returns 1L

        val result = engine.pullOnce()

        assertThat(result).isEqualTo(SyncPullResult.Done(applied = 2, cursor = 6L))
        coVerify(exactly = 1) { serverApi.syncSince(0L) }
        coVerify(exactly = 1) { serverApi.syncSince(3L) }
        coVerify { syncCursorDao.upsert(match { it.lastVersion == 3L }) }
        coVerify { syncCursorDao.upsert(match { it.lastVersion == 6L }) }
    }

    @Test
    fun pullOnce_applyFailed_returnsFailed_keepsCursor() = runTest {
        val bad = SyncChange(
            version = 2L,
            type = "ADD",
            objectName = "Person",
            objectId = "p1",
            fieldName = null,
            value = JsonPrimitive("not-an-object"),
        )
        coEvery { serverApi.syncSince(0L) } returns SyncPage(version = 2L, changes = listOf(bad), hasMore = false)

        val result = engine.pullOnce()

        assertThat(result).isEqualTo(SyncPullResult.Failed(applied = 0, cursor = 0L))
        coVerify(exactly = 0) { syncCursorDao.upsert(any()) }
    }

    @Test
    fun pullOnce_unknownChangeType_returnsFailed_withoutAdvancingCursor() = runTest {
        val unknown = SyncChange(
            version = 2L,
            type = "UPSERT",
            objectName = "Person",
            objectId = "p1",
            fieldName = null,
            value = null,
        )
        coEvery { serverApi.syncSince(0L) } returns SyncPage(version = 2L, changes = listOf(unknown), hasMore = false)

        val result = engine.pullOnce()

        assertThat(result).isEqualTo(SyncPullResult.Failed(applied = 0, cursor = 0L))
        coVerify(exactly = 0) { syncCursorDao.upsert(any()) }
    }

    @Test
    fun pullOnce_personUpdateMissingLocally_recoversFromServer() = runTest {
        val update = SyncChange(
            version = 2L,
            type = "UPDATE",
            objectName = "Person",
            objectId = "p1",
            fieldName = "name",
            value = JsonPrimitive("回源后")
        )
        val hydrated = ContactCacheEntity(
            id = 11L,
            serverId = "p1",
            name = "旧名字",
            createTime = 1L,
            updateTime = 1L,
            isLocalOnly = false,
        )
        coEvery { serverApi.syncSince(0L) } returns SyncPage(version = 2L, changes = listOf(update), hasMore = false)
        every { serverApi.getPerson("p1") } returns remotePerson("p1", "服务端快照")
        // [迁移适配] 查询序列:①applyPersonUpdate 探测缺行 → null;②upsertPerson 内
        // 复查仍无本地行 → insertContact;③恢复后再查 → hydrated,走 name 更新。
        coEvery { contactCacheDao.getContactByServerId("p1") } returnsMany listOf(null, null, hydrated)
        coEvery { contactCacheDao.insertContact(any()) } returns 11L

        val result = engine.pullOnce()

        assertThat(result).isEqualTo(SyncPullResult.Done(applied = 1, cursor = 2L))
        verify(exactly = 1) { serverApi.getPerson("p1") }
        coVerify { contactCacheDao.insertContact(match { it.serverId == "p1" && it.name == "服务端快照" }) }
        coVerify { contactCacheDao.updateContact(match { it.id == 11L && it.name == "回源后" }) }
    }

    @Test
    fun pullOnce_networkError_returnsFailed() = runTest {
        coEvery { serverApi.syncSince(0L) } throws IOException("offline")

        val result = engine.pullOnce()

        assertThat(result).isEqualTo(SyncPullResult.Failed(applied = 0, cursor = 0L))
        coVerify(exactly = 0) { syncCursorDao.upsert(any()) }
    }

    @Test
    fun pullOnce_versionRegression_returnsFailed_withoutApplying() = runTest {
        coEvery { serverApi.syncSince(0L) } returns SyncPage(version = 0L, changes = listOf(addPersonChange(0L, "p1", "坏数据")), hasMore = false)

        val result = engine.pullOnce()

        assertThat(result).isEqualTo(SyncPullResult.Failed(applied = 0, cursor = 0L))
        coVerify(exactly = 0) { contactCacheDao.insertContact(any()) }
        coVerify(exactly = 0) { syncCursorDao.upsert(any()) }
    }

    @Test
    fun pullOnce_repeatedHasMore_stopsAtMaxRoundsAndReturnsFailed() = runTest {
        every { serverApi.syncSince(any()) } answers {
            val since = firstArg<Long>()
            SyncPage(
                version = since + 1L,
                changes = listOf(addPersonChange(since + 1L, "p$since", "name$since")),
                hasMore = true,
            )
        }
        coEvery { contactCacheDao.getContactByServerId(any()) } returns null
        coEvery { contactCacheDao.insertContact(any()) } returns 1L

        val result = engine.pullOnce()

        assertThat(result).isEqualTo(SyncPullResult.Failed(applied = 50, cursor = 50L))
        verify(exactly = 50) { serverApi.syncSince(any()) }
    }

    @Test
    fun syncOnceIfIdle_concurrentReentry_returnsSkipped() = runTest {
        // [修复竞态] 原写法在 async 与主线程之间对 started 标志的竞争顺序不确定（运气差时互相等
        // mutex 死锁）。用 entered 门闩保证第一个调用已持有标志并阻塞在 syncSince 后，再发起第二个调用。
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<SyncPage>()
        coEvery { serverApi.syncSince(0L) } coAnswers {
            entered.complete(Unit)
            gate.await()
        }

        val first = async { engine.syncOnceIfIdle() }
        entered.await()
        val second = engine.syncOnceIfIdle()
        assertThat(second.pull).isEqualTo(SyncPullResult.Skipped)

        gate.complete(SyncPage(version = 0L, changes = emptyList(), hasMore = false))
        val firstResult = first.await()
        assertThat(firstResult.pull).isEqualTo(SyncPullResult.Done(applied = 0, cursor = 0L))
    }

    // ============ [F1] upsertTag 捕获 insertTag 返回 rowId ============

    private fun addTagChange(version: Long, uuid: String, name: String, members: List<String>): SyncChange {
        val json = buildJsonObject {
            put("uuid", uuid)
            put("name", name)
            put("colorHash", "0xFF1976D2")
            put("personMembers", JsonArray(members.map { JsonPrimitive(it) }))
        }
        return SyncChange(
            version = version,
            type = "ADD",
            objectName = "Tag",
            objectId = uuid,
            fieldName = null,
            value = json,
        )
    }

    @Test
    fun upsertTag_newTag_rebuildsRefsWithNonZeroId() = runTest {
        val localContact = ContactCacheEntity(
            id = 7L,
            serverId = "p-1",
            name = "张三",
            createTime = 1L,
            updateTime = 1L,
            isLocalOnly = false,
        )
        coEvery { serverApi.syncSince(0L) } returns SyncPage(
            version = 9L,
            changes = listOf(addTagChange(9L, "t-1", "朋友", listOf("p-1"))),
            hasMore = false,
        )
        coEvery { tagCacheDao.getTagByServerId("t-1") } returns null
        coEvery { tagCacheDao.insertTag(any()) } returns 42L
        coEvery { contactCacheDao.getContactsByServerIds(listOf("p-1")) } returns listOf(localContact)

        val result = engine.pullOnce()

        assertThat(result).isEqualTo(SyncPullResult.Done(applied = 1, cursor = 9L))
        // [F1] cross-ref 的 tagId 必须是 insertTag 返回的 42，绝不能是 0
        coVerify {
            contactTagCacheDao.insertCrossRefs(match { refs ->
                refs.size == 1 && refs[0].tagId == 42L && refs[0].contactId == 7L
            })
        }
    }

    // ============ [T09] sync REMOVE 回收本地头像文件 ============

    @Test
    fun pullOnce_personRemove_deletesLocalAvatarFile() = runTest {
        val tmpDir = kotlin.io.path.createTempDirectory("avatar-remove").toFile()
        val avatar = java.io.File(tmpDir, "contact_11_avatar.webp").apply { writeBytes(byteArrayOf(1)) }
        val local = ContactCacheEntity(
            id = 11L,
            serverId = "p1",
            name = "待删",
            avatarPath = avatar.absolutePath,
            createTime = 1L,
            updateTime = 1L,
            isLocalOnly = false,
        )
        coEvery { serverApi.syncSince(0L) } returns SyncPage(
            version = 3L,
            changes = listOf(
                SyncChange(version = 3L, type = "REMOVE", objectName = "Person", objectId = "p1", fieldName = null, value = null)
            ),
            hasMore = false,
        )
        coEvery { contactCacheDao.getContactByServerId("p1") } returns local

        val result = engine.pullOnce()

        assertThat(result).isEqualTo(SyncPullResult.Done(applied = 1, cursor = 3L))
        coVerify { contactCacheDao.deleteById(11L) }
        assertThat(avatar.exists()).isFalse()
        tmpDir.deleteRecursively()
    }
}
