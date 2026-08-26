package top.mcxiafeng.badger.data.repository

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.queue.OperationHistoryDao
import top.mcxiafeng.badger.data.queue.OperationHistoryEntity
import top.mcxiafeng.badger.data.queue.OperationTypes

/**
 * [Phase 3] OperationHistoryRepositoryImpl（只读日志版）测试。
 *
 * 队列退役后保留的契约：
 * 1. observe join 联系人名
 * 2. 联系人已删除 → name 兜底 null
 * 3. 联系人完全找不到 → name 兜底 null
 * 4. Pending filter 只留 CONFLICT / FAILED_PERMANENT
 */
class OperationHistoryRepositoryImplTest {

    private lateinit var historyDao: OperationHistoryDao
    private lateinit var contactCacheDao: ContactCacheDao
    private lateinit var repository: OperationHistoryRepositoryImpl

    @Before
    fun setup() {
        historyDao = mockk(relaxed = true)
        contactCacheDao = mockk(relaxed = true)
        repository = OperationHistoryRepositoryImpl(
            historyDao = historyDao,
            contactCacheDao = contactCacheDao,
        )
    }

    private fun entity(
        opId: String = "op-1",
        contactId: Long = 1L,
        opType: String = OperationTypes.UPDATE_NAME,
        opStatus: String = "DONE",
    ) = OperationHistoryEntity(
        opId = opId,
        contactId = contactId,
        opType = opType,
        opLabel = OperationTypes.labelOf(opType),
        payloadJson = """{"name":"new"}""",
        snapshotBeforeJson = "",
        snapshotAfterJson = null,
        createdAt = 1_000L,
        opStatus = opStatus,
        serverVersion = null,
        inversePayloadJson = null,
        canUndo = false,
        canReplay = false,
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

    // ============ 4. Pending filter 只留 CONFLICT / FAILED_PERMANENT ============

    @Test
    fun observe_pendingFilter_keepsOnlyConflictAndFailedPermanent() = runTest {
        val history = listOf(
            entity(opId = "op-conflict", opStatus = "CONFLICT"),
            entity(opId = "op-failed-perm", opStatus = "FAILED_PERMANENT"),
            entity(opId = "op-done", opStatus = "DONE"),
            entity(opId = "op-failed", opStatus = "FAILED"),
        )
        every { historyDao.observeRecent(100) } returns flowOf(history)
        every { contactCacheDao.getAllContacts() } returns flowOf(emptyList())

        val collected = repository.observeHistory(filter = HistoryFilter.Pending, limit = 100).first()

        assertThat(collected.map { it.history.opId })
            .containsExactly("op-conflict", "op-failed-perm")
    }
}
