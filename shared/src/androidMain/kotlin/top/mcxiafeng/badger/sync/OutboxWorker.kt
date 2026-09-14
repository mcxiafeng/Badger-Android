package top.mcxiafeng.badger.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

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
