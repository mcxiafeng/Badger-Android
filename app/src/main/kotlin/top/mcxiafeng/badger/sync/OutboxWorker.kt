package top.mcxiafeng.badger.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.context.GlobalContext

/**
 * 消费通用 Outbox 的 WorkManager 触发器：委托 [SyncEngine.pushOnce] 重放
 * （CREATE → PATCH → MEMBER → DELETE，与手动「立即同步」共享同一把 mutex，不会并发重放）。
 *
 * 结局三态（§3.8）：有失败/未知结局 → Result.retry（行级退避在 OutboxStore.recordFailure 内另算）；
 * 全部成功或无可做行 → Result.success。
 *
 * WorkManager 由默认 factory 构造，依赖在 doWork 时从 Koin GlobalContext 拉取。
 */
class OutboxWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val engine = GlobalContext.get().get<SyncEngine>()
        val outcome = engine.pushOnce()
        return if (outcome.failedOps > 0) Result.retry() else Result.success()
    }
}
