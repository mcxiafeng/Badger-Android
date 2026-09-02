package top.mcxiafeng.badger.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Outbox 重放调度器：把高频 kick 合并为一条 unique WorkManager 任务（旧
 * PendingPersonUpdateScheduler 的泛化搬运，行为不变）。
 *
 * kick 点：ServerApi 的每个 enqueue 写路径（T12b 起 Repository 直调处）。
 * 去抖语义由 `ExistingWorkPolicy.APPEND_OR_REPLACE` 保证——已在排队的 kick 不重复入队，
 * 新 kick 追加且不丢新 op。
 */
class OutboxScheduler(
    private val context: Context,
) {
    fun kick() {
        val request = OneTimeWorkRequestBuilder<OutboxWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WORK_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    companion object {
        /** 独立于旧 "pending-person-updates" 的 unique 名，避免与遗留任务互撞。 */
        const val WORK_NAME = "outbox-replay"

        /** WorkManager 层指数退避基长（行级退避在 OutboxStore.recordFailure 内另算）。 */
        const val WORK_BACKOFF_SECONDS = 10L
    }
}
