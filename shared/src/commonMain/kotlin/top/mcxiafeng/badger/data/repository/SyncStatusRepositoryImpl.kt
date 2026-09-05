package top.mcxiafeng.badger.data.repository

import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.dao.SyncCursorDao
import top.mcxiafeng.badger.sync.SyncEngine
import top.mcxiafeng.badger.sync.SyncPullResult

/**
 * [Phase 4 Task #21] SyncStatusRepository impl。
 *
 * 退役 `pending_uploads` 队列语义：
 * - [snapshot] 改为读 `sync_cursor` + `contacts_cache.isLocalOnly` 计数；
 * - [retryAll] [T17] 触发一轮完整同步（`SyncEngine.syncOnce` = 回填 CREATE → push → pull），
 *   不再只 pull；
 * - retryOne / purgeFinished 删除（队列已退役，无消费语义）。
 *
 * [§14.2] Koin `singleOf(::SyncStatusRepositoryImpl) { bind<SyncStatusRepository>() }`。
 */
class SyncStatusRepositoryImpl(
    private val syncCursorDao: SyncCursorDao,
    private val contactCacheDao: ContactCacheDao,
    private val syncEngine: SyncEngine,
) : SyncStatusRepository {

    override suspend fun snapshot(): SyncStatusSnapshot = withContext(BadgerDispatchers.io) {
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
     * 触发一轮完整同步（先推本地未同步，再拉服务端增量）。
     *
     * @return 本次 pull 成功重放的 change 数（Failed 时返回已应用的条数，Skipped 返回 0）。
     */
    override suspend fun retryAll(): Int = withContext(BadgerDispatchers.io) {
        val result = syncEngine.syncOnce()
        when (val pull = result.pull) {
            is SyncPullResult.Done -> pull.applied
            is SyncPullResult.Failed -> pull.applied
            SyncPullResult.Skipped -> 0
        }
    }
}
