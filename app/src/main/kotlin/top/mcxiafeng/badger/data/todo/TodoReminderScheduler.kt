package top.mcxiafeng.badger.data.todo

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import java.security.MessageDigest
import java.time.ZoneId
import java.util.Base64
import top.mcxiafeng.badger.data.account.AccountContext

internal object TodoReminderScheduler {
    /** Called with the account's file lock held, after the records are safely persisted. */
    fun sync(context: Context, previous: List<LocalTodo>, current: List<LocalTodo>) {
        if (previous.none { it.reminder != null } && current.none { it.reminder != null }) return
        withPlatformAccess("reconciling reminders") {
            val scope = AccountContext.find(context)?.scope
            if (scope == null) {
                Log.d(TAG, "Skipped reminder reconciliation without a fixed account scope")
                return@withPlatformAccess
            }
            val alarms = checkNotNull(context.getSystemService(AlarmManager::class.java))
            val updated = current.associateBy { it.id }
            val space = spaceToken(scope.key)
            for (old in previous) {
                if (old.reminder != null && reminderChanged(old, updated[old.id])) {
                    cancel(context, alarms, space, taskToken(scope.key, old.id))
                }
            }
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis()
            for (todo in current) {
                withPlatformAccess("scheduling reminder") {
                    val trigger = todo.reminderAt(zone)?.toEpochMilli()
                    if (trigger != null && trigger > now) {
                        schedule(context, alarms, space, taskToken(scope.key, todo.id), trigger)
                    }
                }
            }
            Log.d(TAG, "Reconciled reminder alarms")
        }
    }

    fun canScheduleExactly(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    private fun reminderChanged(old: LocalTodo, updated: LocalTodo?): Boolean =
        updated == null || old.completed != updated.completed || old.date != updated.date ||
            old.reminder != updated.reminder

    private fun cancel(context: Context, alarms: AlarmManager, space: String, task: String) {
        withPlatformAccess("canceling alarm") {
            val operation = PendingIntent.getBroadcast(
                context, REMINDER_REQUEST, alarmIntent(context, space, task),
                ALARM_FLAGS or PendingIntent.FLAG_NO_CREATE,
            )
            if (operation != null) {
                alarms.cancel(operation)
                operation.cancel()
                Log.d(TAG, "Canceled reminder alarm")
            }
        }
        withPlatformAccess("clearing notification") {
            context.getSystemService(NotificationManager::class.java)?.cancel(task, NOTIFICATION_ID)
        }
    }

    private fun schedule(context: Context, alarms: AlarmManager, space: String, task: String, trigger: Long) {
        val operation = checkNotNull(PendingIntent.getBroadcast(
            context, REMINDER_REQUEST, alarmIntent(context, space, task).putExtra(EXPECTED_TIME, trigger),
            ALARM_FLAGS or PendingIntent.FLAG_CANCEL_CURRENT,
        ))
        if (canScheduleExactly(context)) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, operation)
                Log.d(TAG, "Scheduled exact reminder")
                return
            } catch (_: SecurityException) {
                Log.d(TAG, "Exact alarm access changed before scheduling")
            }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, operation)
        Log.d(TAG, "Scheduled inexact reminder")
    }

    private fun alarmIntent(context: Context, space: String, task: String) =
        Intent(context, TodoReminderReceiver::class.java).setAction(REMINDER_ACTION)
            .setData(Uri.Builder().scheme(REMINDER_SCHEME).authority(REMINDER_AUTHORITY)
                .appendPath(space).appendPath(task).build())

    fun spaceToken(key: String): String = token(key)

    fun taskToken(key: String, id: String): String = token("$key:$id")

    private fun token(value: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)),
    )

    private inline fun withPlatformAccess(operation: String, block: () -> Unit) {
        try {
            block()
        } catch (error: Exception) {
            Log.e(TAG, "Platform failure while $operation (${error.javaClass.simpleName})")
        }
    }

    const val CHANNEL_ID = "todo_reminders"
    const val NOTIFICATION_ID = 1
    const val REMINDER_ACTION = "top.mcxiafeng.badger.action.TODO_REMINDER"
    const val EXPECTED_TIME = "expected_time"
    const val REMINDER_SCHEME = "badger-todo"
    const val REMINDER_AUTHORITY = "reminder"
    private const val TAG = "TodoReminderSchedulerTester"
    private const val REMINDER_REQUEST = 0
    private const val ALARM_FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
}
