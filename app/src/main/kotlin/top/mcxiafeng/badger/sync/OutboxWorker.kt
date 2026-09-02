package top.mcxiafeng.badger.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import org.koin.core.context.GlobalContext
import top.mcxiafeng.badger.network.ServerApi

/**
 * 消费通用 Outbox（规格 §3.4 PushLoop 的 Phase 2 形态）：
 * 按 FIFO 取到期行，经 [ServerApi.replayOutboxOp] 按 EntityKind 分发重放。
 *
 * 结局三态（§3.8）：成功 → markSuccess 出队；失败/未知（超时、断连）→ recordFailure
 * 记账 + 退避保留 PENDING。**没有 FAILED_PERMANENT**——行在成功前不删。
 *
 * WorkManager 由默认 factory 构造（[BadgerApplication] 不再实现 Configuration.Provider），
 * 依赖在 doWork 时从 Koin GlobalContext 拉取。
 */
class OutboxWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val koin = GlobalContext.get()
        val store = koin.get<OutboxStore>()
        val api = koin.get<ServerApi>()

        while (true) {
            val ops = store.getReady()
            if (ops.isEmpty()) return Result.success()

            var failed = false
            for (op in ops) {
                try {
                    Log.d(
                        TAG,
                        "replay: id=${op.id} kind=${op.entityKind} op=${op.op} localId=${op.localId} " +
                            "attempts=${op.attempts} payloadBytes=${op.payload.toString().length}",
                    )
                    api.replayOutboxOp(op)
                    store.markSuccess(op.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 失败与未知结局同路径：记账 + 退避，保留 PENDING 等下一轮
                    store.recordFailure(op.id, e)
                    failed = true
                }
            }
            if (failed) return Result.retry()
            // 本批全部成功但队列未空（超过单批上限）→ 继续下一批
        }
    }

    private companion object {
        const val TAG = "OutboxWorker"
    }
}
