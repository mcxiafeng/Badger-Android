package top.mcxiafeng.badger.pages.settings

import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.components.BadgerConfirmDialog
import top.mcxiafeng.badger.ui.components.BadgerDialog
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.platform.LocalContext

private const val TAG = "AccountSettingsDialogs"

internal const val DEFAULT_SERVER_URL = "http://10.0.2.2:8080"

/**
 * Pattern A: caller controls mount via `if (showDialog) { EditServerUrlDialog(...) }`.
 * Pass-through callback resets the caller's flag in all three exit paths
 * (dismiss / cancel / confirm) so the dialog never gets stuck open.
 *
 * 基于 [BadgerDialog] 封装。
 */
@Composable
fun EditServerUrlDialog(
    currentUrl: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val initial = (currentUrl.takeIf { it.isNotBlank() } ?: DEFAULT_SERVER_URL)
    var urlInput by remember(currentUrl) {
        mutableStateOf(
            TextFieldValue(
                text = initial,
                selection = TextRange(0, initial.length),
            )
        )
    }

    LaunchedEffect(Unit) {
        runCatching {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    BadgerDialog(
        show = true,
        title = "修改服务器地址",
        onDismissRequest = onDismiss,
        negativeText = "取消",
        positiveText = "保存",
        positiveEnabled = urlInput.text.isNotBlank() && urlInput.text != currentUrl,
        onPositive = {
            val input = urlInput.text
            val looksLikeCredential =
                input.contains("://") &&
                    (input.contains("@") || input.contains("token=") || input.contains("Bearer "))
            if (looksLikeCredential) {
                Log.w(TAG, "EditServerUrlDialog: rejected url input that looks like credential (len=${input.length})")
                return@BadgerDialog
            }
            val schemeEnd = input.indexOf("://")
            val searchStart = if (schemeEnd >= 0) schemeEnd + 3 else 0
            val firstSlash = input.indexOf('/', startIndex = searchStart)
            val cleaned = if (firstSlash > 0) {
                Log.w(TAG, "EditServerUrlDialog: url had path suffix, stripping (inLen=${input.length})")
                input.substring(0, firstSlash).trimEnd('/')
            } else {
                input.trim().trimEnd('/')
            }
            val hostPart = cleaned.substringAfter("://", missingDelimiterValue = "")
            if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
                if (hostPart.isBlank()) {
                    Log.w(TAG, "EditServerUrlDialog: rejected empty host (inLen=${input.length})")
                    return@BadgerDialog
                }
            }
            onConfirm(cleaned)
        },
    ) {
        TextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            label = "https://badger.example.com",
            useLabelAsPlaceholder = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )
        Spacer(Modifier.height(BadgerSpacing.sm))
        Text(
            text = "完整 Base URL，保存后即时对全部网络请求生效。点击恢复默认可填回 ${DEFAULT_SERVER_URL}。",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(BadgerSpacing.sm))
        top.yukonga.miuix.kmp.basic.TextButton(
            text = "恢复默认",
            onClick = {
                urlInput = TextFieldValue(
                    text = DEFAULT_SERVER_URL,
                    selection = TextRange(0, DEFAULT_SERVER_URL.length),
                )
                Log.d(TAG, "EditServerUrlDialog: restore default URL")
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

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
            Log.d(TAG, "LogoutConfirmDialog: user confirmed logout")
            onConfirm()
        },
        onDismiss = { if (!isLoggingOut) onDismiss() },
    )
}
