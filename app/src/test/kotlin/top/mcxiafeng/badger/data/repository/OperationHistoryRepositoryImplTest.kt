package top.mcxiafeng.badger.data.repository

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.queue.OperationHistoryDao
import top.mcxiafeng.badger.data.queue.OperationHistoryEntity
import top.mcxiafeng.badger.data.queue.PendingUploadDao
import top.mcxiafeng.badger.data.queue.PendingUploadEntity
import top.mcxiafeng.badger.sync.PendingUploadScheduler

/**
 * [V2-P7] OperationHistoryRepositoryImpl 测试。
 *
 * 覆盖 8 个核心契约:
 * 1. observe join 联系人名
 * 2. 联系人已被删除 → name 兜底 null
 * 3. 联系人完全找不到 → name 兜底 null
 * 4. retry 触发 PendingUploadDao.retryNow + scheduler.kick
 * 5. withdraw 标两个 DAO 为 WITHDRAWN
 * 6. adoptLocal 标 DONE + 写 serverVersion
 * 7. adoptServer 标 DONE + 写 snapshotAfterJson
 * 8. retry 在已 WITHDRAWN/DONE 时返 Failure
 */
class OperationHistoryRepositoryImplTest {

    private lateinit var historyDao: OperationHistoryDao
    private lateinit var contactCacheDao: ContactCacheDao
    private lateinit var pendingDao: PendingUploadDao
    private lateinit var scheduler: PendingUploadScheduler
    private lateinit var repository: OperationHistoryRepositoryImpl

    @Before
    fun setup() {
        historyDao = mockk(relaxed = true)
        contactCacheDao = mockk(relaxed = true)
        pendingDao = mockk(relaxed = true)
        scheduler = mockk(relaxed = true)
        repository = OperationHistoryRepositoryImpl(
            historyDao = historyDao,
            contactCacheDao = contactCacheDao,
            pendingDao = pendingDao,
            scheduler = scheduler,
        )
    }

    @After
    fun tearDown() {
        // mockk auto-clear
    }

    private fun entity(
        opId: String = "op-1",
        contactId: Long = 1L,
        opStatus: String = "DONE",
        canUndo: Boolean = true,
        canReplay: Boolean = false,
    ) = OperationHistoryEntity(
        opId = opId,
        contactId = contactId,
        opType = "UPDATE_NAME",
        opLabel = "修改姓名",
        payloadJson = """{"name":"new"}""",
        snapshotBeforeJson = """{"name":"old"}""",
        snapshotAfterJson = null,
        createdAt = 1_000L,
        opStatus = opStatus,
        canUndo = canUndo,
        canReplay = canReplay,
    )

    private fun contact(id: Long, name: String) = ContactCacheEntity(
        id = id,
        name = name,
        createTime = 1L,
        updateTime = 1L,
    )

    // ============ 1. observe join 联系人名 ============

    @Test
    fun observe_returnsCombinedHistoryWithContactNames() = runTest {
        val history = listOf(entity(opId = "op-1", contactId = 10L))
        val contacts = listOf(contact(10L, "Alice"))
        every { historyDao.observeRecent(100) } returns flowOf(history)
        every { contactCacheDao.getAllContacts() } returns flowOf(contacts)

        val collected = repository.observeHistory(filter = HistoryFilter.All, limit = 100).first()
        assertThat(collected).hasSize(1)
        assertThat(collected[0].contactName).isEqualTo("Alice")
        assertThat(collected[0].history.opId).isEqualTo("op-1")
    }

    // ============ 2. 联系人被删除 → name 兜底 null ============

    @Test
    fun observe_deletedContact_fallsBackToNull() = runTest {
        val history = listOf(entity(opId = "op-1", contactId = 10L))
        val contacts = emptyList<ContactCacheEntity>()
        every { historyDao.observeRecent(100) } returns flowOf(history)
        every { contactCacheDao.getAllContacts() } returns flowOf(contacts)

        val collected = repository.observeHistory(filter = HistoryFilter.All, limit = 100).first()
        assertThat(collected).hasSize(1)
        assertThat(collected[0].contactName).isNull()
    }

    // ============ 3. 联系人完全找不到 → name 兜底 null ============

    @Test
    fun observe_missingContact_fallsBackToNull() = runTest {
        val history = listOf(entity(opId = "op-1", contactId = 999L))
        val contacts = listOf(contact(10L, "Alice"))
        every { historyDao.observeRecent(100) } returns flowOf(history)
        every { contactCacheDao.getAllContacts() } returns flowOf(contacts)

        val collected = repository.observeHistory(filter = HistoryFilter.All, limit = 100).first()
        assertThat(collected).hasSize(1)
        assertThat(collected[0].contactName).isNull()
    }

    // ============ 4. retry 触发 PendingUploadDao.retryNow + scheduler.kick ============

    @Test
    fun retry_marksPendingAndKicksScheduler() = runTest {
        val op = PendingUploadEntity(
            opId = "op-1",
            contactId = 1L,
            opType = "UPDATE_NAME",
            resourceVersion = 0L,
            payloadJson = """{"name":"x"}""",
            createdAt = 1L,
            status = "FAILED",
            deviceId = "dev",
        )
        coEvery { pendingDao.getById("op-1") } returns op
        val result = repository.retry("op-1")
        assertThat(result).isEqualTo(HistoryOpResult.Success)
        coVerify { pendingDao.retryNow("op-1", any()) }
        coVerify { scheduler.kick() }
    }

    // ============ 5. withdraw 标两个 DAO 为 WITHDRAWN ============

    @Test
    fun withdraw_marksBothPendingAndHistoryWithdrawn() = runTest {
        coEvery { historyDao.getById("op-1") } returns entity(canUndo = true)
        val result = repository.withdraw("op-1")
        assertThat(result).isEqualTo(HistoryOpResult.Success)
        coVerify { historyDao.markWithdrawn("op-1") }
        coVerify { pendingDao.markWithdrawn("op-1") }
    }

    // ============ 6. adoptLocal 标 DONE + 写 serverVersion ============

    @Test
    fun adoptLocal_marksDoneWithServerVersion() = runTest {
        val ent = entity(opStatus = "CONFLICT").copy(serverVersion = 99L)
        coEvery { historyDao.getById("op-1") } returns ent
        val result = repository.adoptLocal("op-1")
        assertThat(result).isEqualTo(HistoryOpResult.Success)
        coVerify { historyDao.markDone("op-1", serverVersion = 99L, snapshotAfterJson = null) }
        coVerify { pendingDao.markDone("op-1") }
    }

    // ============ 7. adoptServer 标 DONE + 写 snapshotAfterJson ============

    @Test
    fun adoptServer_marksDoneWithSnapshotAfter() = runTest {
        val ent = entity(opStatus = "CONFLICT").copy(serverVersion = 7L)
        coEvery { historyDao.getById("op-1") } returns ent
        val serverJson = """{"name":"server-side"}"""
        val result = repository.adoptServer("op-1", serverJson)
        assertThat(result).isEqualTo(HistoryOpResult.Success)
        coVerify { historyDao.markDone("op-1", serverVersion = 7L, snapshotAfterJson = serverJson) }
        coVerify { pendingDao.markDone("op-1") }
    }

    // ============ 8. retry 在已 WITHDRAWN 时返 Failure ============

    @Test
    fun retry_alreadyWithdrawn_returnsFailure() = runTest {
        val op = PendingUploadEntity(
            opId = "op-1",
            contactId = 1L,
            opType = "UPDATE_NAME",
            resourceVersion = 0L,
            payloadJson = """{"name":"x"}""",
            createdAt = 1L,
            status = "WITHDRAWN",
            deviceId = "dev",
        )
        coEvery { pendingDao.getById("op-1") } returns op
        val result = repository.retry("op-1")
        assertThat(result).isInstanceOf(HistoryOpResult.Failure::class.java)
        coVerify(exactly = 0) { pendingDao.retryNow(any(), any()) }
        coVerify(exactly = 0) { scheduler.kick() }
    }
}