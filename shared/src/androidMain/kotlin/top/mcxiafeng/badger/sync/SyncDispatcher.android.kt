package top.mcxiafeng.badger.sync

import android.content.Context

actual class SyncDispatcher(private val context: Context) {
    private val scheduler = OutboxScheduler(context)

    actual fun kick() = scheduler.kick()

    companion object {
        
        val WORK_NAME = OutboxScheduler.WORK_NAME
        val WORK_BACKOFF_SECONDS = OutboxScheduler.WORK_BACKOFF_SECONDS
    }
}
