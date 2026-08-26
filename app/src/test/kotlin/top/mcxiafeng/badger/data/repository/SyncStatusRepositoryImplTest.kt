package top.mcxiafeng.badger.data.repository

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import top.mcxiafeng.badger.data.queue.PendingUploadDao
import top.mcxiafeng.badger.data.queue.PendingUploadEntity
import top.mcxiafeng.badger.sync.SyncPullResult
import top.mcxiafeng.badger.sync.SyncRepository

/**
 * [Phase 3] SyncStatusRepositoryImpl 单元测试。
 *
 * PendingUpload 队列退役后覆盖的契约：
 * 1. snapshot：pending_uploads 历史遗留计数（只读展示）
 * 2. retryAll：改为触发一次增量同步（SyncRepository.pullOnceIfIdle），返回 applied 数
 * 3. retryOne：仅作历史 FAILED 标记判断，不再消费
 * 4. purgeFinished：阈值 = now - days*86400_000
 */
class SyncStatusRepositoryImplTest {

    private lateinit var pendingDao: PendingUploadDao
    private lateinit var syncRepository: SyncRepository
    private lateinit var repository: SyncStatusRepositoryImpl

    @Before
    fun setup() {
        pendingDao = mockk(relaxed = true)
        syncRepository = mockk(relaxed = true)
        repository = SyncStatusRepositoryImpl(pendingDao, syncRepository)
    }

    // ============ 1. snapshot 6 个状态计数对齐 ============

    @Test
    fun snapshot_returnsCorrectCountsByStatus() = runTest {
        coEvery { pendingDao.countByStatus("PENDING") } returns 3
        coEvery { pendingDao.countByStatus("IN_FLIGHT") } returns 1
        coEvery { pendingDao.countByStatus("FAILED") } returns 2
        coEvery { pendingDao.countByStatus("CONFLICT") } returns 1
        coEvery { pendingDao.countByStatus("FAILED_PERMANENT") } returns 0
        coEvery { pendingDao.countByStatus("WITHDRAWN") } returns 5
        coEvery { pendingDao.countByStatus("DONE") } returns 100
        coEvery { pendingDao.count() } returns 112

        val snap = repository.snapshot()

        assertThat(snap.pendingCount).isEqualTo(3)
        assertThat(snap.inFlightCount).isEqualTo(1)
        assertThat(snap.failedCount).isEqualTo(2)
        assertThat(snap.conflictCount).isEqualTo(1)
        assertThat(snap.failedPermanentCount).isEqualTo(0)
        assertThat(snap.withdrawnCount).isEqualTo(5)
        assertThat(snap.doneCount).isEqualTo(100)
        assertThat(snap.totalCount).isEqualTo(112)
        assertThat(snap.hasAttention).isTrue()
    }

    @Test
    fun snapshot_empty_hasNoAttention() = runTest {
        coEvery { pendingDao.countByStatus(any()) } returns 0
        coEvery { pendingDao.count() } returns 0

        val snap = repository.snapshot()

        assertThat(snap.totalCount).isEqualTo(0)
        assertThat(snap.hasAttention).isFalse()
    }

    // ============ 2. retryAll → 触发增量同步 ============

    @Test
    fun retryAll_returnsAppliedFromSync() = runTest {
        coEvery { syncRepository.pullOnceIfIdle() } returns SyncPullResult.Done(applied = 7, cursor = 100L)

        val count = repository.retryAll()

        assertThat(count).isEqualTo(7)
        coVerify { syncRepository.pullOnceIfIdle() }
    }

    @Test
    fun retryAll_failedSync_returnsAppliedSoFar() = runTest {
        coEvery { syncRepository.pullOnceIfIdle() } returns SyncPullResult.Failed(applied = 3, cursor = 50L)

        val count = repository.retryAll()

        assertThat(count).isEqualTo(3)
    }

    @Test
    fun retryAll_skipped_returnsZero() = runTest {
        coEvery { syncRepository.pullOnceIfIdle() } returns SyncPullResult.Skipped

        val count = repository.retryAll()

        assertThat(count).isEqualTo(0)
    }

    // ============ 3. retryOne — 仅历史 FAILED 标记 ============

    @Test
    fun retryOne_failedOp_returnsTrue() = runTest {
        coEvery { pendingDao.getById("op-1") } returns pendingUploadEntity(status = "FAILED")

        val ok = repository.retryOne("op-1")

        assertThat(ok).isTrue()
    }

    @Test
    fun retryOne_nonFailed_returnsFalse() = runTest {
        coEvery { pendingDao.getById("op-1") } returns pendingUploadEntity(status = "DONE")

        val ok = repository.retryOne("op-1")

        assertThat(ok).isFalse()
    }

    @Test
    fun retryOne_notFound_returnsFalse() = runTest {
        coEvery { pendingDao.getById("op-1") } returns null

        val ok = repository.retryOne("op-1")

        assertThat(ok).isFalse()
    }

    // ============ 4. purgeFinished ============

    @Test
    fun purgeFinished_callsPurgeDoneWithThreshold() = runTest {
        coEvery { pendingDao.purgeDone(any()) } returns 7

        val deleted = repository.purgeFinished(olderThanDays = 30)

        assertThat(deleted).isEqualTo(7)
        coVerify { pendingDao.purgeDone(match { it > 0L }) }
    }

    @Test
    fun purgeFinished_default30Days() = runTest {
        coEvery { pendingDao.purgeDone(any()) } returns 0

        val deleted = repository.purgeFinished()

        assertThat(deleted).isEqualTo(0)
        coVerify { pendingDao.purgeDone(any()) }
    }

    // ============ helper ============

    private fun pendingUploadEntity(
        opId: String = "op-1",
        status: String = "PENDING",
    ): PendingUploadEntity = PendingUploadEntity(
        opId = opId,
        contactId = 1L,
        opType = "UPDATE_NAME",
        resourceVersion = 0L,
        payloadJson = """{"name":"x"}""",
        createdAt = 1_000L,
        status = status,
        deviceId = "dev",
    )
}
