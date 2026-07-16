package top.mcxiafeng.badger.pages.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private const val TAG = "AccountSettingsDialogs"

/**
 * Pattern A: caller controls mount via `if (showDialog) { EditServerUrlDialog(...) }`.
 * Pass-through callback resets the caller's flag in all three exit paths
 * (dismiss / cancel / confirm) so the dialog never gets stuck open.
 */
@Composable
fun EditServerUrlDialog(
    currentUrl: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var urlInput by rememberSaveable(currentUrl) { mutableStateOf(currentUrl) }
    WindowDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = "修改服务器地址",
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = "https://badger.example.com",
                useLabelAsPlaceholder = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "完整 Base URL，保存后需重启应用才能让网络客户端切到新地址。",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "保存",
                    enabled = urlInput.isNotBlank() && urlInput != currentUrl,
                    onClick = { onConfirm(urlInput) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@Composable
fun LogoutConfirmDialog(
    isLoggingOut: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    // [修复防御]: 退出中时拦截 onDismissRequest,避免用户在 logout 飞行中关闭弹窗导致状态不一致
    WindowDialog(
        show = true,
        onDismissRequest = { if (!isLoggingOut) onDismiss() },
        title = "确认退出登录",
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "退出后将清除本地凭证。云端备份需重新登录后才会自动启用。",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = "取消",
                    enabled = !isLoggingOut,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = if (isLoggingOut) "退出中..." else "退出",
                    enabled = !isLoggingOut,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}