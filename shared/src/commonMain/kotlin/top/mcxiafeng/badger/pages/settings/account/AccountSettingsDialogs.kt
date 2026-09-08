package top.mcxiafeng.badger.pages.settings.account

import androidx.compose.runtime.Composable
import top.mcxiafeng.badger.ui.components.BadgerConfirmDialog
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "AccountSettingsDialogs"

/**
 * 退出登录确认 Dialog
 *
 * 基于 [BadgerConfirmDialog] 封装。
 * 退出中时拦截 onDismissRequest,避免用户在 logout 飞行中关闭弹窗导致状态不一致。
 */
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
