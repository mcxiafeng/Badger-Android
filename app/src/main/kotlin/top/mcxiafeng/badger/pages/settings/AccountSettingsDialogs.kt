package top.mcxiafeng.badger.pages.settings

import android.util.Log
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
                text = "完整 Base URL，保存后即时对全部网络请求生效。",
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
                    onClick = {
                        // 修复防御:不允许把 token、用户名或密码误粘进 server url。
                        // 这里只记录是否拦截 + 命中长度,绝不打印 urlInput 原文。
                        val looksLikeCredential =
                            urlInput.contains("://") &&
                                (urlInput.contains("@") || urlInput.contains("token=") || urlInput.contains("Bearer "))
                        if (looksLikeCredential) {
                            Log.w(TAG, "EditServerUrlDialog: rejected url input that looks like credential (len=${urlInput.length})")
                            return@TextButton
                        }
                        // [修复防御]: 拦截带路径后缀的 URL —— 用户粘 "https://api.example.com/badger"
                        // 会导致所有 /api/* 请求拼成 /badger/api/* → 全 404 → 401 → refresh 死循环,
                        // 用户体感就是 "明明地址对却连不上"。这里只保留 scheme + host(:port)。
                        val schemeEnd = urlInput.indexOf("://")
                        val searchStart = if (schemeEnd >= 0) schemeEnd + 3 else 0
                        val firstSlash = urlInput.indexOf('/', startIndex = searchStart)
                        val cleaned = if (firstSlash > 0) {
                            Log.w(TAG, "EditServerUrlDialog: url had path suffix, stripping (inLen=${urlInput.length})")
                            urlInput.substring(0, firstSlash).trimEnd('/')
                        } else {
                            urlInput.trim().trimEnd('/')
                        }
                        onConfirm(cleaned)
                    },
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
                    onClick = {
                        // 修复防御:只记录用户发起的退出意图(供行为路径排查),不打印任何账号信息
                        Log.d(TAG, "LogoutConfirmDialog: user confirmed logout")
                        onConfirm()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}