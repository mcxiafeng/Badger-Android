package top.mcxiafeng.badger.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * 消费通用 Outbox 的 WorkManager 触发器：委托注册表注入的重放回调（默认 = SyncEngine.pushOnce，
 * BadgerApplication.onCreate 注入）执行 CREATE → PATCH → MEMBER → DELETE 重放
 * （与手动「立即同步」共享同一把 mutex，不会并发重放）。
 *
 * 结局三态（§3.8）：有失败/未知结局 → Result.retry（行级退避在 OutboxStore.recordFailure 内另算）；
 * 全部成功或无可做行 → Result.success。
 *
 * [KMP K09] 本类迁 shared androidMain；依赖经 [OutboxReplayRegistry] 注入（Koin 不进 shared），
 * WorkManager 默认 factory 无参构造照常工作。
 */
class OutboxWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val replay = OutboxReplayRegistry.requireProvider()
        val outcome = replay(false)
        return if (outcome.failedOps > 0) Result.retry() else Result.success()
    }
}
