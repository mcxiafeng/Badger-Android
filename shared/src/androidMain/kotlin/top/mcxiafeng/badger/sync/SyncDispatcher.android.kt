package top.mcxiafeng.badger.sync

import android.content.Context

/**
 * [KMP K09] Android actual：直接委托现有 [OutboxScheduler]（WorkManager 路径行为零变化）。
 * 保留原类与常量（OutboxWorker 入队、`WORK_NAME`、指数退避），本类只是契约边界的 actual 壳。
 */
actual class SyncDispatcher(private val context: Context) {
    private val scheduler = OutboxScheduler(context)

    actual fun kick() = scheduler.kick()

    companion object {
        /** 透传 OutboxScheduler 常量（OutboxWorker 结构/契约不因抽象层引入而变化）。 */
        val WORK_NAME = OutboxScheduler.WORK_NAME
        val WORK_BACKOFF_SECONDS = OutboxScheduler.WORK_BACKOFF_SECONDS
    }
}
