package top.mcxiafeng.badger.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import top.mcxiafeng.badger.data.prefs.DEFAULT_SERVER_URL
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "ServerUrlDialog"

/**
 * 修改服务器地址对话框 —— 认证页（未验证提示条）与账号设置页共用。
 *
 * Pattern A: caller controls mount via `if (showDialog) { EditServerUrlDialog(...) }`。
 * 回调在 dismiss / cancel / confirm 三条路径都会触发，调用方据此复位挂载 flag。
 *
 * 输入清洗：拒绝疑似凭据的输入（@ / token= / Bearer），路径后缀剥离，仅保留 scheme://host。
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
                BadgerLog.w(TAG, "rejected url input that looks like credential (len=${input.length})")
                return@BadgerDialog
            }
            val schemeEnd = input.indexOf("://")
            val searchStart = if (schemeEnd >= 0) schemeEnd + 3 else 0
            val firstSlash = input.indexOf('/', startIndex = searchStart)
            val cleaned = if (firstSlash > 0) {
                BadgerLog.w(TAG, "url had path suffix, stripping (inLen=${input.length})")
                input.substring(0, firstSlash).trimEnd('/')
            } else {
                input.trim().trimEnd('/')
            }
            val hostPart = cleaned.substringAfter("://", missingDelimiterValue = "")
            if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
                if (hostPart.isBlank()) {
                    BadgerLog.w(TAG, "rejected empty host (inLen=${input.length})")
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
        TextButton(
            text = "恢复默认",
            onClick = {
                urlInput = TextFieldValue(
                    text = DEFAULT_SERVER_URL,
                    selection = TextRange(0, DEFAULT_SERVER_URL.length),
                )
                BadgerLog.d(TAG, "restore default URL")
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
