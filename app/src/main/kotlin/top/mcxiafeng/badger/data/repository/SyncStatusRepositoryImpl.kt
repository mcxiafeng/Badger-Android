package top.mcxiafeng.badger.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.queue.PendingUploadDao
import top.mcxiafeng.badger.sync.SyncPullResult
import top.mcxiafeng.badger.sync.SyncRepository

/**
 * [V2-P9] SyncStatusRepository impl。
 *
 * [Phase 3] PendingUpload 队列退役：
 * - [snapshot] / [purgeFinished] 仍基于 `pending_uploads` 表（历史遗留数据只读展示）；
 * - [retryAll] / [retryOne] 语义改为**触发一次服务端增量同步**（`GET /api/user/sync?since=`），
 *   不再有 Worker 队列消费。
 *
 * [§14.2] Hilt `@Singleton @Inject constructor` → Koin `singleOf(::SyncStatusRepositoryImpl) { bind<SyncStatusRepository>() }`。
 */
class SyncStatusRepositoryImpl(
    private val pendingDao: PendingUploadDao,
    private val syncRepository: SyncRepository,
) : SyncStatusRepository {

    private val tag = TAG

    override suspend fun snapshot(): SyncStatusSnapshot = withContext(Dispatchers.IO) {
        Log.d(tag, "snapshot: 读 pending_uploads 计数(历史遗留,队列已退役)")
        coroutineScope {
            val pendingDef = async { pendingDao.countByStatus("PENDING") }
            val inFlightDef = async { pendingDao.countByStatus("IN_FLIGHT") }
            val failedDef = async { pendingDao.countByStatus("FAILED") }
            val conflictDef = async { pendingDao.countByStatus("CONFLICT") }
            val failedPermDef = async { pendingDao.countByStatus("FAILED_PERMANENT") }
            val withdrawnDef = async { pendingDao.countByStatus("WITHDRAWN") }
            val doneDef = async { pendingDao.countByStatus("DONE") }
            val totalDef = async { pendingDao.count() }
            SyncStatusSnapshot(
                pendingCount = pendingDef.await(),
                inFlightCount = inFlightDef.await(),
                failedCount = failedDef.await(),
                conflictCount = conflictDef.await(),
                failedPermanentCount = failedPermDef.await(),
                withdrawnCount = withdrawnDef.await(),
                doneCount = doneDef.await(),
                totalCount = totalDef.await(),
            ).also {
                Log.d(
                    tag,
                    "snapshot: pending=${it.pendingCount} inFlight=${it.inFlightCount} failed=${it.failedCount} " +
                        "conflict=${it.conflictCount} failedPermanent=${it.failedPermanentCount} " +
                        "withdrawn=${it.withdrawnCount} done=${it.doneCount} total=${it.totalCount}",
                )
            }
        }
    }

    /**
     * [Phase 3] 队列退役：不再 kick Worker，改为触发一次服务端增量同步。
     *
     * @return 本次同步成功重放的 change 数（Failed 时返回已应用的条数，Skipped 返回 0）。
     */
    override suspend fun retryAll(): Int = withContext(Dispatchers.IO) {
        Log.d(tag, "retryAll: 队列已退役,触发一次增量同步")
        val result = syncRepository.pullOnceIfIdle()
        val applied = when (result) {
            is SyncPullResult.Done -> result.applied
            is SyncPullResult.Failed -> result.applied
            SyncPullResult.Skipped -> 0
        }
        Log.d(tag, "retryAll: 增量同步完成 result=$result applied=$applied")
        applied
    }

    /**
     * [Phase 3] 队列退役：仅作历史 FAILED 标记判断，不再消费。
     */
    override suspend fun retryOne(opId: String): Boolean = withContext(Dispatchers.IO) {
        val op = pendingDao.getById(opId)
        if (op == null) {
            Log.w(tag, "retryOne: opId=${opId.take(8)} not in pending_uploads")
            return@withContext false
        }
        op.status == "FAILED"
    }

    override suspend fun purgeFinished(olderThanDays: Int): Int = withContext(Dispatchers.IO) {
        val threshold = System.currentTimeMillis() - olderThanDays * 86_400_000L
        Log.d(tag, "purgeFinished: 清理 $olderThanDays 天前的 DONE(before=$threshold)")
        val deleted = pendingDao.purgeDone(threshold)
        Log.d(tag, "purgeFinished: 删除 $deleted 条")
        deleted
    }

    companion object {
        private const val TAG = "SyncStatusRepo"
    }
}
