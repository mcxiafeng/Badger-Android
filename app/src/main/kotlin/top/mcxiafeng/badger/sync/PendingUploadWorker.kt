package top.mcxiafeng.badger.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.queue.OperationHistoryDao
import top.mcxiafeng.badger.data.queue.PendingUploadDao

/**
 * [V2-P4] PendingUpload 队列消费 Worker。
 *
 * 由 [PendingUploadScheduler] 通过 WorkManager.enqueueUniqueWork 触发;
 * 也可由 OperationHistoryPage"立即重试"按钮直接 enqueue。
 *
 * 工作循环(对齐 `docs/BADGER_V2_CLIENT_PLAN.md` §0#4 批量并发上限 = 8):
 * ```
 *   for batch in [pull nextReady(limit=8) while hasMore]:
 *     for op in batch:
 *       1. markInFlight(op, now)   ← CAS 防双 Worker 抢锁
 *       2. executor.execute(op)    ← 走 ServerApi + 落状态
 *     // 直到 nextReady() 空
 * ```
 *
 * **Worker 终止条件**:
 * - nextReady() 返回空列表 → Result.success()
 * - 任何 IO 异常被 Executor 兜底为 FAILED → 继续下一个
 * - Worker 进程被 OS 杀掉 → Result.retry() 让 WorkManager 用 backoff 兜底
 *
 * **不**直接调 ServerApi:所有网络细节收敛在 [PendingUploadExecutor]。
 * Worker 只负责"拉批 → 标 IN_FLIGHT → 调 executor"。
 *
 * [§14.2] **删除** `@HiltWorker` + `@AssistedInject` — 现在由 [SyncWorkerFactory]
 * 手动 `new PendingUploadWorker(appContext, params, pendingDao, executor)` 构造,
 * 所有依赖从 Koin `GlobalContext.get()` 解析。
 */
class PendingUploadWorker(
    appContext: Context,
    params: WorkerParameters,
    private val pendingDao: PendingUploadDao,
    private val executor: PendingUploadExecutor,
) : CoroutineWorker(appContext, params) {

    private val tag = TAG

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        var totalProcessed = 0
        var totalConflicts = 0
        var totalFailures = 0

        try {
            // 循环拉批,直到 nextReady 空 — 单 worker 串行消费,避免同 op 被并发抢
            while (true) {
                val batch = pendingDao.nextReady(now = now, limit = BATCH_SIZE)
                if (batch.isEmpty()) {
                    Log.d(tag, "doWork: 队列已空,total processed=$totalProcessed conflicts=$totalConflicts failures=$totalFailures")
                    return Result.success()
                }
                Log.d(tag, "doWork: 本轮 batch=${batch.size}")
                for (op in batch) {
                    // 1. CAS 抢锁:仅 PENDING 才能被抢成 IN_FLIGHT
                    val rows = pendingDao.markInFlight(op.opId, lastAttemptAt = now)
                    if (rows == 0) {
                        Log.w(tag, "doWork: opId=${op.opId.take(8)} 抢锁失败(已被其它 Worker 消费或已终止),跳过")
                        continue
                    }
                    // 2. 走 Executor:网络 + 状态机
                    val result = executor.execute(op.copy(status = "IN_FLIGHT", lastAttemptAt = now), now = now)
                    when (result) {
                        is ExecResult.Done -> totalProcessed++
                        is ExecResult.Conflict -> { totalProcessed++; totalConflicts++ }
                        ExecResult.RetryScheduled -> totalFailures++
                        ExecResult.Skipped -> { /* 不计入 */ }
                        is ExecResult.PermanentFailure -> { totalFailures++ }
                    }
                }
                // 防止无限循环(极端:每次拉到的下一批都还没到 nextAttemptAt)
                if (batch.size < BATCH_SIZE) {
                    Log.d(tag, "doWork: 短批(batch=${batch.size}<$BATCH_SIZE)无更多,退出 total=$totalProcessed")
                    return Result.success()
                }
            }
        } catch (e: Exception) {
            // [修复防御]: Worker 整体崩溃时让 WorkManager 走 backoff 兜底,attempts++
            // 由 setBackoffCriteria 控制;**不**直接 Result.failure()(会丢失 PENDING op)
            Log.e(tag, "doWork: 整体异常,Result.retry 让 WorkManager backoff", e)
            return Result.retry()
        }
    }

    companion object {
        private const val TAG = "PendingUploadWorker"
        /** 单轮 batch 上限,与 §0#4 / `PendingUploadDao.nextReady` limit=8 对齐。 */
        const val BATCH_SIZE = 8

        /** WorkManager 唯一 work name,所有 kick 都 enqueue 这个名字。 */
        const val UNIQUE_WORK_NAME = "badger.pending_upload.flush"
    }
}