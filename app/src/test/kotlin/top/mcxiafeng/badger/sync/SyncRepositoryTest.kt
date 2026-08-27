package top.mcxiafeng.badger.sync

import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonPrimitive
import io.mockk.coEvery
import io.mockk.coVerify
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
import top.mcxiafeng.badger.data.cache.entity.SyncCursorEntity
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.network.SyncChange
import top.mcxiafeng.badger.network.SyncPage
import java.io.IOException

/**
 * [Phase 3] SyncRepository（服务端权威增量同步引擎）单元测试。
 *
 * 覆盖：
 * - 空 changes → Done(0, 0)，不推进游标
 * - 单批 ADD Person 成功重放落 Room + 游标推进
 * - hasMore 分页续拉（多轮直到 hasMore=false）
 * - 批次应用失败 → Failed，游标不动（下轮重放）
 * - 网络异常 → Failed（游标未推进的数据不丢）
 * - 并发重入 → Skipped
 */
class SyncRepositoryTest {

    private lateinit var serverApi: ServerApi
    private lateinit var syncCursorDao: SyncCursorDao
    private lateinit var contactCacheDao: ContactCacheDao
    private lateinit var contactPlatformCacheDao: ContactPlatformCacheDao
    private lateinit var tagCacheDao: TagCacheDao
    private lateinit var cardCollectionCacheDao: CardCollectionCacheDao
    private lateinit var contactTagCacheDao: ContactTagCacheDao
    private lateinit var personProfileCacheDao: PersonProfileCacheDao
    private lateinit var repository: SyncRepository

    @Before
    fun setup() {
        serverApi = mockk(relaxed = true)
        syncCursorDao = mockk(relaxed = true)
        contactCacheDao = mockk(relaxed = true)
        contactPlatformCacheDao = mockk(relaxed = true)
        tagCacheDao = mockk(relaxed = true)
        cardCollectionCacheDao = mockk(relaxed = true)
        contactTagCacheDao = mockk(relaxed = true)
        personProfileCacheDao = mockk(relaxed = true)
        repository = SyncRepository(
            serverApi,
            syncCursorDao,
            contactCacheDao,
            contactPlatformCacheDao,
            tagCacheDao,
            cardCollectionCacheDao,
            contactTagCacheDao,
            personProfileCacheDao,
        )
    }

    private fun addPersonChange(version: Long, uuid: String, name: String): SyncChange {
        val json = com.google.gson.JsonObject().apply {
            addProperty("uuid", uuid)
            addProperty("name", name)
            addProperty("createTime", "2026-01-01 00:00:00")
            addProperty("updateTime", "2026-01-01 00:00:00")
            addProperty("self", false)
            val profile = com.google.gson.JsonObject()
            val contactMap = com.google.gson.JsonObject()
            contactMap.addProperty("qq", "123")
            profile.add("contactMap", contactMap)
            add("profile", profile)
        }
        return SyncChange(version = version, type = "ADD", objectName = "Person", objectId = uuid, fieldName = null, value = json)
    }

    // ============ 1. 空 changes → Done，不推进游标 ============

    @Test
    fun pullOnce_emptyChanges_returnsDoneZeroCursor() = runTest {
        coEvery { serverApi.syncSince(0L) } returns SyncPage(version = 0L, changes = emptyList(), hasMore = false)

        val result = repository.pullOnce()

        assertThat(result).isEqualTo(SyncPullResult.Done(applied = 0, cursor = 0L))
        coVerify(exactly = 0) { syncCursorDao.upsert(any()) }
    }

    // ============ 2. 单批 ADD Person → 落 Room + 游标推进 ============

    @Test
    fun pullOnce_singleBatch_appliesChangesAndAdvancesCursor() = runTest {
        val change = addPersonChange(version = 5L, uuid = "p1", name = "张三")
        coEvery { serverApi.syncSince(0L) } returns SyncPage(version = 5L, changes = listOf(change), hasMore = false)
        coEvery { contactCacheDao.getContactByServerId("p1") } returns null
        coEvery { contactCacheDao.insertContact(any()) } returns 7L

        val result = repository.pullOnce()

        assertThat(result).isEqualTo(SyncPullResult.Done(applied = 1, cursor = 5L))
        coVerify { contactCacheDao.insertContact(match { it.serverId == "p1" && it.name == "张三" }) }
        coVerify { contactPlatformCacheDao.deleteByContact(7L) }
        coVerify { contactPlatformCacheDao.insertPlatforms(match { it.size == 1 }) }
        coVerify { contactCacheDao.bumpContact(7L) }
        coVerify { syncCursorDao.upsert(match { it.lastVersion == 5L }) }
    }

    // ============ 3. hasMore 分页续拉 ============

    @Test
    fun pullOnce_hasMore_keepsPullingUntilFalse() = runTest {
        val c1 = addPersonChange(version = 3L, uuid = "p1", name = "甲")
        val c2 = addPersonChange(version = 6L, uuid = "p2", name = "乙")
        coEvery { serverApi.syncSince(0L) } returns SyncPage(version = 3L, changes = listOf(c1), hasMore = true)
        coEvery { serverApi.syncSince(3L) } returns SyncPage(version = 6L, changes = listOf(c2), hasMore = false)
        coEvery { contactCacheDao.getContactByServerId(any()) } returns null
        coEvery { contactCacheDao.insertContact(any()) } returns 1L

        val result = repository.pullOnce()

        assertThat(result).isEqualTo(SyncPullResult.Done(applied = 2, cursor = 6L))
        coVerify(exactly = 1) { serverApi.syncSince(0L) }
        coVerify(exactly = 1) { serverApi.syncSince(3L) }
        coVerify { syncCursorDao.upsert(match { it.lastVersion == 3L }) }
        coVerify { syncCursorDao.upsert(match { it.lastVersion == 6L }) }
    }

    // ============ 4. 批次应用失败 → Failed，游标不动 ============

    @Test
    fun pullOnce_applyFailed_returnsFailed_keepsCursor() = runTest {
        // ADD Person value 非对象 → upsertPerson 抛 IllegalStateException
        val bad = SyncChange(
            version = 2L, type = "ADD", objectName = "Person", objectId = "p1",
            fieldName = null, value = JsonPrimitive("not-an-object"),
        )
        coEvery { serverApi.syncSince(0L) } returns SyncPage(version = 2L, changes = listOf(bad), hasMore = false)

        val result = repository.pullOnce()

        assertThat(result).isEqualTo(SyncPullResult.Failed(applied = 0, cursor = 0L))
        coVerify(exactly = 0) { syncCursorDao.upsert(any()) }
    }

    // ============ 5. 网络异常 → Failed ============

    @Test
    fun pullOnce_networkError_returnsFailed() = runTest {
        coEvery { serverApi.syncSince(0L) } throws IOException("offline")

        val result = repository.pullOnce()

        assertThat(result).isEqualTo(SyncPullResult.Failed(applied = 0, cursor = 0L))
        coVerify(exactly = 0) { syncCursorDao.upsert(any()) }
    }

    // ============ 6. 并发重入 → Skipped ============

    @Test
    fun pullOnceIfIdle_concurrentReentry_returnsSkipped() = runTest {
        val gate = CompletableDeferred<SyncPage>()
        coEvery { serverApi.syncSince(0L) } coAnswers { gate.await() }

        val first = async { repository.pullOnceIfIdle() }
        // 让 first 进入 doPull 并在 syncSince 挂起
        kotlinx.coroutines.yield()
        val second = repository.pullOnceIfIdle()
        assertThat(second).isEqualTo(SyncPullResult.Skipped)

        gate.complete(SyncPage(version = 0L, changes = emptyList(), hasMore = false))
        val firstResult = first.await()
        assertThat(firstResult).isEqualTo(SyncPullResult.Done(applied = 0, cursor = 0L))
    }
}
