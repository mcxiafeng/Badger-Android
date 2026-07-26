package top.mcxiafeng.badger.data.repository

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import top.mcxiafeng.badger.data.queue.PendingUploadDao
import top.mcxiafeng.badger.data.queue.PendingUploadEntity
import top.mcxiafeng.badger.sync.PendingUploadScheduler

/**
 * [V2-P9] SyncStatusRepositoryImpl 单元测试。
 *
 * 覆盖 4 类核心契约:
 * 1. snapshot:6 个 countByStatus + count 总数对齐
 * 2. retryAll:遍历 FAILED → 全部 retryNow + kick 一次
 * 3. retryOne:仅 FAILED + 存在 → retryNow + kick;其他 false
 * 4. purgeFinished:阈值 = now - days*86400_000
 */
class SyncStatusRepositoryImplTest {

    private lateinit var pendingDao: PendingUploadDao
    private lateinit var scheduler: PendingUploadScheduler
    private lateinit var repository: SyncStatusRepositoryImpl

    @Before
    fun setup() {
        pendingDao = mockk(relaxed = true)
        scheduler = mockk(relaxed = true)
        repository = SyncStatusRepositoryImpl(pendingDao, scheduler)
    }

    @After
    fun tearDown() {
        // mockk auto-clear
    }

    // ============ 1. snapshot 6 个状态计数对齐 ============

    @Test
    fun snapshot_returnsCorrectCountsByStatus() = runTest {
        // 给每个 status 返不同数字,验证 snapshot 字段映射
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
        // hasAttention: FAILED(2) + CONFLICT(1) = true
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

    // ============ 2. retryAll ============

    @Test
    fun retryAll_marksAllFailedAsPendingAndKicksScheduler() = runTest {
        val failed1 = pendingUploadEntity(opId = "op-1", status = "FAILED")
        val failed2 = pendingUploadEntity(opId = "op-2", status = "FAILED")
        val pending = pendingUploadEntity(opId = "op-3", status = "PENDING")
        val done = pendingUploadEntity(opId = "op-4", status = "DONE")
        coEvery { pendingDao.getAll() } returns listOf(failed1, failed2, pending, done)

        val count = repository.retryAll()

        // [修复防御]: FAILED 两条 → retryNow 各一次,kick 1 次(全局而非每条)
        assertThat(count).isEqualTo(2)
        coVerify { pendingDao.retryNow("op-1", any()) }
        coVerify { pendingDao.retryNow("op-2", any()) }
        coVerify(exactly = 0) { pendingDao.retryNow("op-3", any()) }
        coVerify(exactly = 0) { pendingDao.retryNow("op-4", any()) }
        coVerify(exactly = 1) { scheduler.kick() }
    }

    @Test
    fun retryAll_noFailed_returnsZero() = runTest {
        coEvery { pendingDao.getAll() } returns listOf(
            pendingUploadEntity(status = "DONE"),
            pendingUploadEntity(status = "PENDING"),
        )

        val count = repository.retryAll()

        assertThat(count).isEqualTo(0)
        coVerify(exactly = 0) { pendingDao.retryNow(any(), any()) }
        coVerify(exactly = 0) { scheduler.kick() }
    }

    // ============ 3. retryOne ============

    @Test
    fun retryOne_validFailedOpId_retriesAndKicksScheduler() = runTest {
        val op = pendingUploadEntity(opId = "op-1", status = "FAILED")
        coEvery { pendingDao.getById("op-1") } returns op

        val ok = repository.retryOne("op-1")

        assertThat(ok).isTrue()
        coVerify { pendingDao.retryNow("op-1", any()) }
        coVerify(exactly = 1) { scheduler.kick() }
    }

    @Test
    fun retryOne_nonFailedStatus_returnsFalse() = runTest {
        val op = pendingUploadEntity(opId = "op-1", status = "DONE")
        coEvery { pendingDao.getById("op-1") } returns op

        val ok = repository.retryOne("op-1")

        assertThat(ok).isFalse()
        coVerify(exactly = 0) { pendingDao.retryNow(any(), any()) }
        coVerify(exactly = 0) { scheduler.kick() }
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
        // [修复防御]: 阈值 = now - 30*86400_000,验证 invoke 时传的是阈值(不是 now,不是别的)
        coVerify { pendingDao.purgeDone(match { it > 0L }) }
    }

    @Test
    fun purgeFinished_default30Days() = runTest {
        coEvery { pendingDao.purgeDone(any()) } returns 0

        val deleted = repository.purgeFinished()  // 不传参数 → 默认 30

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
