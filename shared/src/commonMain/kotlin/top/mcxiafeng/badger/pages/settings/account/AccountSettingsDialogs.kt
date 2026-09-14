package top.mcxiafeng.badger.pages.settings.account

import androidx.compose.runtime.Composable
import top.mcxiafeng.badger.ui.components.BadgerConfirmDialog
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "AccountSettingsDialogs"

@Composable
fun LogoutConfirmDialog(
    isLoggingOut: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    BadgerConfirmDialog(
        show = true,
        title = "确认退出登录",
        message = "退出后将清除本地凭证。云端备份需重新登录后才会自动启用。",
        confirmText = if (isLoggingOut) "退出中..." else "退出",
        isDestructive = true,
        onConfirm = {
            BadgerLog.d(TAG, "LogoutConfirmDialog: user confirmed logout")
            onConfirm()
        },
        onDismiss = { if (!isLoggingOut) onDismiss() },
    )
}
