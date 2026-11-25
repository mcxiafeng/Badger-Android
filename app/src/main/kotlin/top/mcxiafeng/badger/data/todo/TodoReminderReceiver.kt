package top.mcxiafeng.badger.data.todo

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import top.mcxiafeng.badger.MainActivity
import top.mcxiafeng.badger.data.account.AccountContext
import top.mcxiafeng.badger.data.account.AccountScope
import top.mcxiafeng.badger.data.account.AccountStore
import android.R as AndroidR
import top.mcxiafeng.badger.data.todo.TodoReminderScheduler.CHANNEL_ID
import top.mcxiafeng.badger.data.todo.TodoReminderScheduler.EXPECTED_TIME
import top.mcxiafeng.badger.data.todo.TodoReminderScheduler.NOTIFICATION_ID
import top.mcxiafeng.badger.data.todo.TodoReminderScheduler.REMINDER_ACTION
import top.mcxiafeng.badger.data.todo.TodoReminderScheduler.REMINDER_AUTHORITY
import top.mcxiafeng.badger.data.todo.TodoReminderScheduler.REMINDER_SCHEME

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TodoReminderAccountEntryPoint {
    fun accountStore(): AccountStore
}

class TodoReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != REMINDER_ACTION && action !in RESCHEDULE_ACTIONS) return
        val result = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scopes = EntryPointAccessors.fromApplication(appContext, TodoReminderAccountEntryPoint::class.java)
                    .accountStore().registeredScopes()
                if (action == REMINDER_ACTION) deliver(appContext, scopes, intent)
                else reconcile(appContext, scopes)
            } catch (error: Exception) {
                Log.e(TAG, "Reminder broadcast failed (${error.javaClass.simpleName})")
            } finally {
                result.finish()
            }
        }
    }

    private suspend fun deliver(context: Context, scopes: List<AccountScope>, intent: Intent) {
        val data = intent.data ?: return
        if (data.scheme != REMINDER_SCHEME || data.authority != REMINDER_AUTHORITY ||
            data.pathSegments.size != IDENTITY_PARTS
        ) return
        val (spaceToken, taskToken) = data.pathSegments
        val expected = intent.getLongExtra(EXPECTED_TIME, NO_EXPECTED_TIME)
        if (expected == NO_EXPECTED_TIME) return
        val scope = scopes.find { TodoReminderScheduler.spaceToken(it.key) == spaceToken } ?: return
        withStoredTodos(AccountContext(context, scope)) { items ->
            val task = items.find { TodoReminderScheduler.taskToken(scope.key, it.id) == taskToken }
            if (task?.reminderAt()?.toEpochMilli() == expected && expected <= System.currentTimeMillis()) {
                notifyTask(context, taskToken)
            } else {
                Log.d(TAG, "Ignored reminder that no longer matches the stored task and time")
            }
        }
    }

    private suspend fun reconcile(context: Context, scopes: List<AccountScope>) {
        for (scope in scopes) {
            try {
                val scopedContext = AccountContext(context, scope)
                withStoredTodos(scopedContext) { TodoReminderScheduler.sync(scopedContext, emptyList(), it) }
            } catch (error: Exception) {
                Log.e(TAG, "Could not reload reminders for a space (${error.javaClass.simpleName})")
            }
        }
        Log.d(TAG, "Processed system reminder rescheduling broadcast")
    }

    private suspend fun withStoredTodos(context: AccountContext, block: (List<LocalTodo>) -> Unit) {
        val storage = TodoFileStore(context.filesDir)
        LocalTodoRepository.reminderMutex(storage.key).withLock {
            block(storage.read(storage.generation()))
        }
    }

    private fun notifyTask(context: Context, tag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(POST_NOTIFICATIONS) != PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Skipped reminder because notification permission is missing")
            return
        }
        val notifications = checkNotNull(context.getSystemService(NotificationManager::class.java))
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "待办提醒", NotificationManager.IMPORTANCE_DEFAULT),
        )
        if (!notifications.areNotificationsEnabled() ||
            notifications.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE
        ) {
            Log.d(TAG, "Skipped reminder because notifications are disabled")
            return
        }
        val openApp = Intent(Intent.ACTION_MAIN, null, context, MainActivity::class.java)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentIntent = PendingIntent.getActivity(context, OPEN_APP_REQUEST, openApp, PendingIntent.FLAG_IMMUTABLE)
        // Notification content can outlive an account switch, so it contains no account or task details.
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(REMINDER_ICON)
            .setContentTitle("待办提醒")
            .setContentText("有一项待办到了提醒时间")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        notifications.notify(tag, NOTIFICATION_ID, notification)
        Log.d(TAG, "Posted a private reminder notification")
    }

    private companion object {
        const val TAG = "TodoReminderReceiverTester"
        const val IDENTITY_PARTS = 2
        const val NO_EXPECTED_TIME = Long.MIN_VALUE
        const val OPEN_APP_REQUEST = 0
        const val REMINDER_ICON = AndroidR.drawable.ic_popup_reminder
        val RESCHEDULE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
