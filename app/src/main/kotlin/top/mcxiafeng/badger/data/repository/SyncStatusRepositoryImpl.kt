package top.mcxiafeng.badger.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.queue.PendingUploadDao
import top.mcxiafeng.badger.sync.PendingUploadScheduler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [V2-P9] SyncStatusRepository impl。
 *
 * 实现要点:
 * - `snapshot()`: 6 个 countByStatus 调用并发跑(每个 IO 都很快);汇总返 SyncStatusSnapshot。
 * - `retryAll()`: `getAll()` 拿 FAILED 过滤 → 逐条 `retryNow` → 最后 `kick()` 一次。
 * - `retryOne(opId)`: 单条查 + 状态校验 + retryNow + kick。
 * - `purgeFinished(days)`: 阈值 = now - days*86400_000,调 `pendingDao.purgeDone(before)`。
 *
 * 所有 IO 走 [Dispatchers.IO] —— 即便 `countByStatus` 是 @Query suspend,也要避免上层在 Main
 * 线程调 [SyncStatusRepository] 时把 Room Query 当作同步阻塞(防止 NetworkOnMainThreadException
 * 同款的"主线程慢 IO"问题)。
 */
@Singleton
class SyncStatusRepositoryImpl @Inject constructor(
    private val pendingDao: PendingUploadDao,
    private val scheduler: PendingUploadScheduler,
) : SyncStatusRepository {

    private val tag = TAG

    override suspend fun snapshot(): SyncStatusSnapshot = withContext(Dispatchers.IO) {
        Log.d(tag, "snapshot: 读 6 个状态计数")
        // [修复防御]: countByStatus 是 @Query suspend,Room 内部用 IO 调度,但 awaitAll
        // 把多个独立 IO 操作并发跑,避免 6 次串行 IO 累积延迟。
        coroutineScope {
            val pendingDef = async { pendingDao.countByStatus("PENDING") }
            val inFlightDef = async { pendingDao.countByStatus("IN_FLIGHT") }
            val failedDef = async { pendingDao.countByStatus("FAILED") }
            val conflictDef = async { pendingDao.countByStatus("CONFLICT") }
            val failedPermDef = async { pendingDao.countByStatus("FAILED_PERMANENT") }
            val withdrawnDef = async { pendingDao.countByStatus("WITHDRAWN") }
            val doneDef = async { pendingDao.countByStatus("DONE") }
            val totalDef = async { pendingDao.count() }
            val pending = pendingDef.await()
            val inFlight = inFlightDef.await()
            val failed = failedDef.await()
            val conflict = conflictDef.await()
            val failedPerm = failedPermDef.await()
            val withdrawn = withdrawnDef.await()
            val done = doneDef.await()
            val total = totalDef.await()
            SyncStatusSnapshot(
                pendingCount = pending,
                inFlightCount = inFlight,
                failedCount = failed,
                conflictCount = conflict,
                failedPermanentCount = failedPerm,
                withdrawnCount = withdrawn,
                doneCount = done,
                totalCount = total,
            ).also {
                Log.d(
                    tag,
                    "snapshot: pending=$pending inFlight=$inFlight failed=$failed " +
                        "conflict=$conflict failedPermanent=$failedPerm withdrawn=$withdrawn " +
                        "done=$done total=$total",
                )
            }
        }
    }

    override suspend fun retryAll(): Int = withContext(Dispatchers.IO) {
        Log.d(tag, "retryAll: 开始批量重试 FAILED")
        val now = System.currentTimeMillis()
        // [修复防御]: 不直接调 getAll() 再 filter —— Room `@Query` 已有"FULL SCAN" 风险。
        // 这里依赖 P9+ 阶段扩 `pendingDao.observeAll().first()`,暂用 getAll()(数据量 1w+ 才需优化)。
        val all = pendingDao.getAll()
        val failedOps = all.filter { it.status == "FAILED" }
        Log.d(tag, "retryAll: 待重试 op 数量 = ${failedOps.size}")
        if (failedOps.isEmpty()) {
            return@withContext 0
        }
        var retriedCount = 0
        for (op in failedOps) {
            try {
                pendingDao.retryNow(op.opId, now)
                retriedCount++
                Log.d(tag, "retryAll: opId=${op.opId.take(8)} → PENDING")
            } catch (e: Exception) {
                // [修复防御]: 单条 retryNow 抛异常被 catch + warn 继续下一条,不阻断整体。
                // (理论上 retryNow 不会抛,但 Room 主键冲突 / DB 锁等待 等极端情况下可能抛)
                Log.w(tag, "retryAll: opId=${op.opId.take(8)} retryNow 失败,继续", e)
            }
        }
        // [修复防御]: 全部 retryNow 完才 kick 一次 —— 不每条都 kick 避免 high-priority burst
        scheduler.kick()
        Log.d(tag, "retryAll: 完成,共重试 $retriedCount 条,kick 1 次")
        retriedCount
    }

    override suspend fun retryOne(opId: String): Boolean = withContext(Dispatchers.IO) {
        Log.d(tag, "retryOne: opId=${opId.take(8)}")
        val op = pendingDao.getById(opId)
        if (op == null) {
            Log.w(tag, "retryOne: opId=${opId.take(8)} not in pending_uploads")
            return@withContext false
        }
        if (op.status != "FAILED") {
            Log.w(tag, "retryOne: opId=${opId.take(8)} status=${op.status},只重试 FAILED")
            return@withContext false
        }
        pendingDao.retryNow(opId, System.currentTimeMillis())
        scheduler.kick()
        Log.d(tag, "retryOne: opId=${opId.take(8)} → PENDING + kick")
        true
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
