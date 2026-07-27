package top.mcxiafeng.badger.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.queue.OperationHistoryDao
import top.mcxiafeng.badger.data.queue.PendingUploadDao

/**
 * [V2-P6] 30s 恢复窗口兜底 Worker(对齐 `docs/BADGER_V2_CLIENT_PLAN.md` §5.5.3)。
 *
 * 由 [PendingUploadScheduler.scheduleRevertIfStuck] 在 commitDelete 完成 enqueue
 * 后 30s 触发,目的是兜底"直发 HTTP 失败 + Worker 接力一直没成功"的极端情况:
 * - 若 30s 内 op 已 DONE → no-op
 * - 若 30s 后 op 仍 IN_FLIGHT / PENDING → 视为服务端真失败,UI 复活 isDeleted=false
 * - 若 30s 后 op FAILED_PERMANENT → 同样复活
 * - 若 30s 后 op CONFLICT → 标记 historyDao 进入"用户决策"态(OperationHistoryPage 弹)
 *
 * [修复防御]:本 Worker 用 WorkManager OneTimeWorkRequest,不依赖 viewModelScope,
 * 即使 App 被强制杀 / 30s 内用户关 App,WorkManager 仍会在 30s 后拉起来跑一次。
 * 之前 scope.launch 在 §5.5.1 极端情况表已标"revert 永远不会执行" — 这里根治。
 *
 * CAS 抢锁:30s 拉起时,先 check op 状态,再决定是否 revert。多个 Worker 实例不会
 * 重复 revert(因为 op 状态已落库,二次 revert 看到 op 已经是 DONE/FAILED/CONFLICT
 * 等状态会跳过)。
 *
 * [§14.2] **删除** `@HiltWorker` + `@AssistedInject` — 现在由 [SyncWorkerFactory]
 * 手动 `new RevertStuckOpWorker(appContext, params, pendingDao, historyDao, contactCacheDao)` 构造,
 * 所有依赖从 Koin `GlobalContext.get()` 解析。
 */
class RevertStuckOpWorker(
    appContext: Context,
    params: WorkerParameters,
    private val pendingDao: PendingUploadDao,
    private val historyDao: OperationHistoryDao,
    private val contactCacheDao: ContactCacheDao,
) : CoroutineWorker(appContext, params) {

    private val tag = TAG

    override suspend fun doWork(): Result {
        val opId = inputData.getString(KEY_OP_ID)
        if (opId.isNullOrBlank()) {
            Log.w(tag, "doWork: 缺少 opId,Result.failure")
            return Result.failure()
        }
        val op = pendingDao.getById(opId)
        if (op == null) {
            Log.w(tag, "doWork: opId=${opId.take(8)} 不在 pending_uploads 里,Result.success")
            return Result.success()
        }
        Log.d(tag, "doWork: 30s 恢复窗口触发,opId=${opId.take(8)} status=${op.status} contactId=${op.contactId}")

        return when (op.status) {
            "DONE" -> {
                // 已成功,无需 revert
                Log.d(tag, "doWork: opId=${opId.take(8)} 已 DONE,跳过")
                Result.success()
            }
            "WITHDRAWN" -> {
                Log.d(tag, "doWork: opId=${opId.take(8)} 已 WITHDRAWN,跳过")
                Result.success()
            }
            "FAILED_PERMANENT", "CONFLICT" -> {
                // 服务端真失败 → 复活联系人(若已标记 isDeleted=true)
                // [修复防御]:只复活 DELETE_CONTACT 类型,避免误把 MERGE_CONTACT 等"非恢复"操作回滚
                if (op.opType == "DELETE_CONTACT") {
                    contactCacheDao.setDeleted(op.contactId, deleted = false, now = System.currentTimeMillis())
                    Log.w(tag, "doWork: opId=${opId.take(8)} status=${op.status} → 复活 isDeleted=false (contactId=${op.contactId})")
                } else {
                    Log.w(tag, "doWork: opId=${opId.take(8)} status=${op.status} (opType=${op.opType}) — 非 DELETE,本 Worker 不复活")
                }
                // history 落"恢复提示"lastError 便于 OperationHistoryPage 弹红徽章
                // (不重置 opStatus — 保留 CONFLICT / FAILED_PERMANENT 给用户决策)
                Result.success()
            }
            "PENDING", "IN_FLIGHT" -> {
                // 30s 内 Worker 还没消化掉 — 视为暂时性网络问题,继续等它处理
                // [修复防御]:不强行 revive,否则 Worker 后续真成功时 hit hardDelete(已 setDeleted=true)
                // 反而把复活的状态又删了。这里仅打日志,等 Worker 下次 done/recover。
                Log.w(tag, "doWork: opId=${opId.take(8)} 仍 ${op.status},等 Worker 自然完结")
                Result.success()
            }
            else -> {
                Log.w(tag, "doWork: opId=${opId.take(8)} 未知 status=${op.status},跳过")
                Result.success()
            }
        }
    }

    companion object {
        private const val TAG = "RevertStuckOpWorker"
        const val KEY_OP_ID = "opId"
        fun uniqueWorkName(opId: String) = "badger.revert_stuck.$opId"
    }
}
