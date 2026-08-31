package top.mcxiafeng.badger.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.context.GlobalContext
import top.mcxiafeng.badger.data.queue.PendingPersonUpdateStore
import top.mcxiafeng.badger.network.ServerApi

/** Replays durable Person PUTs without creating another outbox generation. */
class PendingPersonUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val koin = GlobalContext.get()
        val store = koin.get<PendingPersonUpdateStore>()
        val api = koin.get<ServerApi>()
        val updates = store.getReady()
        if (updates.isEmpty()) return Result.success()

        var failed = false
        for (update in updates) {
            try {
                api.replayPendingPersonUpdate(update)
                store.deleteIfRequest(update.serverId, update.requestId)
            } catch (e: Exception) {
                store.recordFailure(update.serverId, update.requestId, e)
                failed = true
            }
        }

        return if (failed) Result.retry() else Result.success()
    }
}
