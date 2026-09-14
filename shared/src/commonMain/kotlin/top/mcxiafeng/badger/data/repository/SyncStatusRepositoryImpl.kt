package top.mcxiafeng.badger.data.repository

import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.dao.SyncCursorDao
import top.mcxiafeng.badger.sync.SyncEngine
import top.mcxiafeng.badger.sync.SyncPullResult

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

    

    override suspend fun retryAll(): Int = withContext(BadgerDispatchers.io) {
        val result = syncEngine.syncOnce()
        when (val pull = result.pull) {
            is SyncPullResult.Done -> pull.applied
            is SyncPullResult.Failed -> pull.applied
            SyncPullResult.Skipped -> 0
        }
    }
}
