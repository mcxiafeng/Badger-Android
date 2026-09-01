package top.mcxiafeng.badger.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.dao.SyncCursorDao
import top.mcxiafeng.badger.sync.SyncPullResult
import top.mcxiafeng.badger.sync.SyncRepository

/**
 * [Phase 4 Task #21] SyncStatusRepository impl。
 *
 * 退役 `pending_uploads` 队列语义：
 * - [snapshot] 改为读 `sync_cursor` + `contacts_cache.isLocalOnly` 计数；
 * - [retryAll] 触发一次服务端增量同步（`SyncRepository.pullOnceIfIdle`）；
 * - retryOne / purgeFinished 删除（队列已退役，无消费语义）。
 *
 * [§14.2] Koin `singleOf(::SyncStatusRepositoryImpl) { bind<SyncStatusRepository>() }`。
 */
class SyncStatusRepositoryImpl(
    private val syncCursorDao: SyncCursorDao,
    private val contactCacheDao: ContactCacheDao,
    private val syncRepository: SyncRepository,
) : SyncStatusRepository {

    override suspend fun snapshot(): SyncStatusSnapshot = withContext(Dispatchers.IO) {
        coroutineScope {
            val versionDef = async { syncCursorDao.getLastVersion() }
            val unsyncedDef = async { contactCacheDao.countLocalOnly() }
            SyncStatusSnapshot(
                lastSyncVersion = versionDef.await() ?: 0L,
                lastSyncedAt = 0L,
                unsyncedCount = unsyncedDef.await(),
            )
        }
    }

    /**
     * 触发一次服务端增量同步。
     *
     * @return 本次同步成功重放的 change 数（Failed 时返回已应用的条数，Skipped 返回 0）。
     */
    override suspend fun retryAll(): Int = withContext(Dispatchers.IO) {
        val result = syncRepository.pullOnceIfIdle()
        when (result) {
            is SyncPullResult.Done -> result.applied
            is SyncPullResult.Failed -> result.applied
            SyncPullResult.Skipped -> 0
        }
    }
}
