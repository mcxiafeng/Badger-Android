package top.mcxiafeng.badger.data.repository

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.dao.SyncCursorDao
import top.mcxiafeng.badger.sync.SyncPullResult
import top.mcxiafeng.badger.sync.SyncRepository

/**
 * [Phase 4 Task #21] SyncStatusRepositoryImpl 单元测试。
 *
 * 退役 pending_uploads 队列后覆盖的契约：
 * 1. snapshot：读 sync_cursor + contacts_cache.isLocalOnly 计数
 * 2. retryAll：触发一次增量同步（SyncRepository.pullOnceIfIdle），返回 applied 数
 */
class SyncStatusRepositoryImplTest {

    private lateinit var syncCursorDao: SyncCursorDao
    private lateinit var contactCacheDao: ContactCacheDao
    private lateinit var syncRepository: SyncRepository
    private lateinit var repository: SyncStatusRepositoryImpl

    @Before
    fun setup() {
        syncCursorDao = mockk(relaxed = true)
        contactCacheDao = mockk(relaxed = true)
        syncRepository = mockk(relaxed = true)
        repository = SyncStatusRepositoryImpl(syncCursorDao, contactCacheDao, syncRepository)
    }

    // ============ 1. snapshot 读 sync_cursor + isLocalOnly ============

    @Test
    fun snapshot_returnsSyncCursorAndUnsyncedCount() = runTest {
        coEvery { syncCursorDao.getLastVersion() } returns 42L
        coEvery { contactCacheDao.countLocalOnly() } returns 5

        val snap = repository.snapshot()

        assertThat(snap.lastSyncVersion).isEqualTo(42L)
        assertThat(snap.unsyncedCount).isEqualTo(5)
        assertThat(snap.hasAttention).isTrue()
    }

    @Test
    fun snapshot_noCursor_returnsZeroVersion() = runTest {
        coEvery { syncCursorDao.getLastVersion() } returns null
        coEvery { contactCacheDao.countLocalOnly() } returns 0

        val snap = repository.snapshot()

        assertThat(snap.lastSyncVersion).isEqualTo(0L)
        assertThat(snap.unsyncedCount).isEqualTo(0)
        assertThat(snap.hasAttention).isFalse()
    }

    @Test
    fun snapshot_noUnsynced_hasNoAttention() = runTest {
        coEvery { syncCursorDao.getLastVersion() } returns 100L
        coEvery { contactCacheDao.countLocalOnly() } returns 0

        val snap = repository.snapshot()

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
}
