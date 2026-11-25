package top.mcxiafeng.badger.pages.todo

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import top.mcxiafeng.badger.data.todo.TodoReminderScheduler
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.mcxiafeng.badger.ui.components.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun TodoReminderPermission() {
    val context = LocalContext.current
    var access by remember(context) { mutableStateOf(reminderAccess(context)) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var actionFailed by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        access = reminderAccess(context)
        Log.d(TAG, "Notification permission result; granted=$granted")
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        access = reminderAccess(context)
        Log.d(TAG, "Refreshed reminder permission status")
    }
    Card(cornerRadius = BadgerRadius.surface, insideMargin = PaddingValues()) {
        ReminderPermissionRow(access, requested, onRequest = {
            actionFailed = try {
                launcher.launch(POST_NOTIFICATIONS)
                requested = true
                Log.d(TAG, "Requested notification permission")
                false
            } catch (error: RuntimeException) {
                Log.e(TAG, "Could not request notification permission (${error.javaClass.simpleName})")
                true
            }
        }, onSettings = { action ->
            actionFailed = !openReminderSettings(context, action)
        })
    }
    if (actionFailed) Text("暂时无法打开权限设置", color = MiuixTheme.colorScheme.error)
}

@Composable
private fun ReminderPermissionRow(
    access: ReminderAccess,
    requested: Boolean,
    onRequest: () -> Unit,
    onSettings: (String) -> Unit,
) {
    when (access) {
        ReminderAccess.NEEDS_NOTIFICATION_PERMISSION -> ArrowPreference(
            title = if (requested) "打开通知设置" else "允许待办通知",
            summary = "待办通知权限未开启，提醒无法显示",
            onClick = { if (requested) onSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS) else onRequest() },
        )
        ReminderAccess.APP_NOTIFICATIONS_DISABLED -> ArrowPreference(
            title = "开启应用通知",
            summary = "应用通知已关闭，待办提醒无法显示",
            onClick = { onSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS) },
        )
        ReminderAccess.CHANNEL_DISABLED -> ArrowPreference(
            title = "开启待办提醒通知",
            summary = "待办提醒的通知类别已关闭",
            onClick = { onSettings(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS) },
        )
        ReminderAccess.INEXACT -> ArrowPreference(
            title = "允许精确提醒",
            summary = "当前使用近似提醒，通知可能延迟",
            onClick = { onSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) },
        )
        ReminderAccess.READY -> BasicComponent(title = "待办通知", summary = "通知和精确提醒均已开启")
        ReminderAccess.UNAVAILABLE -> BasicComponent(title = "待办通知", summary = "暂时无法读取通知权限")
    }
}

private fun reminderAccess(context: Context): ReminderAccess = try {
    val notifications = checkNotNull(context.getSystemService(NotificationManager::class.java))
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(POST_NOTIFICATIONS) != PERMISSION_GRANTED ->
            ReminderAccess.NEEDS_NOTIFICATION_PERMISSION
        !notifications.areNotificationsEnabled() -> ReminderAccess.APP_NOTIFICATIONS_DISABLED
        notifications.getNotificationChannel(TodoReminderScheduler.CHANNEL_ID)?.importance ==
            NotificationManager.IMPORTANCE_NONE -> ReminderAccess.CHANNEL_DISABLED
        !TodoReminderScheduler.canScheduleExactly(context) -> ReminderAccess.INEXACT
        else -> ReminderAccess.READY
    }
} catch (error: RuntimeException) {
    Log.e(TAG, "Could not read reminder permissions (${error.javaClass.simpleName})")
    ReminderAccess.UNAVAILABLE
}

private fun openReminderSettings(context: Context, action: String): Boolean {
    val target = Intent(action).apply {
        if (action == Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) {
            data = Uri.fromParts("package", context.packageName, null)
        } else {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            if (action == Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS) {
                putExtra(Settings.EXTRA_CHANNEL_ID, TodoReminderScheduler.CHANNEL_ID)
            }
        }
    }
    return try {
        context.startActivity(target)
        Log.d(TAG, "Opened system reminder permission settings")
        true
    } catch (error: RuntimeException) {
        Log.e(TAG, "Could not open reminder settings (${error.javaClass.simpleName})")
        false
    }
}

private enum class ReminderAccess {
    NEEDS_NOTIFICATION_PERMISSION, APP_NOTIFICATIONS_DISABLED, CHANNEL_DISABLED, INEXACT, READY, UNAVAILABLE,
}

private const val TAG = "TodoReminderPermissionTester"
