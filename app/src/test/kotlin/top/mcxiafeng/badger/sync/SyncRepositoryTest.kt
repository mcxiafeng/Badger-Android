package top.mcxiafeng.badger.sync

import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonPrimitive
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

/** SyncRepository 回归测试：游标安全、缺行恢复、未知变更和分页边界。 */
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

        val result = repository.pullOnce()

        assertThat(result).isEqualTo(SyncPullResult.Done(applied = 0, cursor = 0L))
        coVerify(exactly = 0) { syncCursorDao.upsert(any()) }
    }

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

        val result = repository.pullOnce()

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

        val result = repository.pullOnce()

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
        coEvery { contactCacheDao.getContactByServerId("p1") } returnsMany listOf(null, hydrated, hydrated)
        coEvery { contactCacheDao.insertContact(any()) } returns 11L

        val result = repository.pullOnce()

        assertThat(result).isEqualTo(SyncPullResult.Done(applied = 1, cursor = 2L))
        verify(exactly = 1) { serverApi.getPerson("p1") }
        coVerify { contactCacheDao.insertContact(match { it.serverId == "p1" && it.name == "服务端快照" }) }
        coVerify { contactCacheDao.updateContact(match { it.id == 11L && it.name == "回源后" }) }
    }

    @Test
    fun pullOnce_networkError_returnsFailed() = runTest {
        coEvery { serverApi.syncSince(0L) } throws IOException("offline")

        val result = repository.pullOnce()

        assertThat(result).isEqualTo(SyncPullResult.Failed(applied = 0, cursor = 0L))
        coVerify(exactly = 0) { syncCursorDao.upsert(any()) }
    }

    @Test
    fun pullOnce_versionRegression_returnsFailed_withoutApplying() = runTest {
        coEvery { serverApi.syncSince(0L) } returns SyncPage(version = 0L, changes = listOf(addPersonChange(0L, "p1", "坏数据")), hasMore = false)

        val result = repository.pullOnce()

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

        val result = repository.pullOnce()

        assertThat(result).isEqualTo(SyncPullResult.Failed(applied = 50, cursor = 50L))
        verify(exactly = 50) { serverApi.syncSince(any()) }
    }

    @Test
    fun pullOnceIfIdle_concurrentReentry_returnsSkipped() = runTest {
        val gate = CompletableDeferred<SyncPage>()
        coEvery { serverApi.syncSince(0L) } coAnswers { gate.await() }

        val first = async { repository.pullOnceIfIdle() }
        kotlinx.coroutines.yield()
        val second = repository.pullOnceIfIdle()
        assertThat(second).isEqualTo(SyncPullResult.Skipped)

        gate.complete(SyncPage(version = 0L, changes = emptyList(), hasMore = false))
        val firstResult = first.await()
        assertThat(firstResult).isEqualTo(SyncPullResult.Done(applied = 0, cursor = 0L))
    }
}
