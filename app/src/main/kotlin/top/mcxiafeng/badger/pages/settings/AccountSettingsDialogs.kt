package top.mcxiafeng.badger.pages.settings

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.data.AuthPrefs
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import androidx.compose.ui.platform.LocalContext

private const val TAG = "AccountSettingsDialogs"

private const val DEFAULT_SERVER_URL = "http://10.0.2.2:8080"

/**
 * Pattern A: caller controls mount via `if (showDialog) { EditServerUrlDialog(...) }`.
 * Pass-through callback resets the caller's flag in all three exit paths
 * (dismiss / cancel / confirm) so the dialog never gets stuck open.
 *
 * [V2-E2E #2] 修复:
 *   - 打开 dialog 时 TextField 自动 selectAll,真实用户点击后会全选默认 URL,
 *     之后输入直接覆盖,避免 type_keys 模拟时把字符串追加到默认 URL 后面。
 *   - 加 "恢复默认" 按钮,一键回填 10.0.2.2:8080 兜底。
 *   - Label 改为说明默认 URL,提示用户输入框已填充。
 *   - 校验 URL 格式:host 部分必须存在(否则 raw 提交),保存前 **清空 trailing slash**。
 */
@Composable
fun EditServerUrlDialog(
    currentUrl: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // [修复防御]: TextFieldValue 不是 Parcelable,默认 rememberSaveable 无法写入 Bundle。
    // 必须显式传 TextFieldValue.Saver 才能在 dialog 关闭/重建时恢复光标与选区。
    // 否则 process death 后重建会抛 IllegalArgumentException("MutableState containing TextFieldValue cannot be saved")。
    // [修复崩溃 #1]: currentUrl 可能为 null/空(从未配置时 ServerUrlHolder 初值为空),
    // 以 null 为 key 触发 rememberSaveable 会让两次重建状态错位 + 当前 key 命中问题。
    // 改成直接 remember { mutableStateOf(...) },丢掉 saver 的持久化能力 —— dialog 短生命周期内
    // 自然重建不丢内存状态,process death 不会跨越 dialog 存在期间(进程死了就一起销毁)。
    val initial = (currentUrl.takeIf { it.isNotBlank() } ?: DEFAULT_SERVER_URL)
    var urlInput by remember(currentUrl) {
        mutableStateOf(
            TextFieldValue(
                text = initial,
                selection = TextRange(0, initial.length),
            )
        )
    }

    // [修复防御]: 打开 dialog 时自动 focus + selectAll,这样用户键入会直接替换,而不是追加。
    LaunchedEffect(Unit) {
        runCatching {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

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
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "完整 Base URL，保存后即时对全部网络请求生效。点击恢复默认可填回 ${DEFAULT_SERVER_URL}。",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = "恢复默认",
                    onClick = {
                        // [修复防御]: 恢复默认 = 直接覆盖 urlInput + 重新 selectAll。
                        // 不触发保存,用户必须再点"保存"才落 prefs,避免误触丢地址。
                        urlInput = TextFieldValue(
                            text = DEFAULT_SERVER_URL,
                            selection = TextRange(0, DEFAULT_SERVER_URL.length),
                        )
                        Log.d(TAG, "EditServerUrlDialog: restore default URL")
                    },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "保存",
                    enabled = urlInput.text.isNotBlank() && urlInput.text != currentUrl,
                    onClick = {
                        val input = urlInput.text
                        // 修复防御:不允许把 token、用户名或密码误粘进 server url。
                        // 这里只记录是否拦截 + 命中长度,绝不打印 urlInput 原文。
                        val looksLikeCredential =
                            input.contains("://") &&
                                (input.contains("@") || input.contains("token=") || input.contains("Bearer "))
                        if (looksLikeCredential) {
                            Log.w(TAG, "EditServerUrlDialog: rejected url input that looks like credential (len=${input.length})")
                            return@TextButton
                        }
                        // [修复防御]: 拦截带路径后缀的 URL —— 用户粘 "https://api.example.com/badger"
                        // 会导致所有 /api/* 请求拼成 /badger/api/* → 全 404 → 401 → refresh 死循环,
                        // 用户体感就是 "明明地址对却连不上"。这里只保留 scheme + host(:port)。
                        val schemeEnd = input.indexOf("://")
                        val searchStart = if (schemeEnd >= 0) schemeEnd + 3 else 0
                        val firstSlash = input.indexOf('/', startIndex = searchStart)
                        val cleaned = if (firstSlash > 0) {
                            Log.w(TAG, "EditServerUrlDialog: url had path suffix, stripping (inLen=${input.length})")
                            input.substring(0, firstSlash).trimEnd('/')
                        } else {
                            input.trim().trimEnd('/')
                        }
                        // [修复防御]: 拒绝完全没主机名的输入(纯 "http://" 或 "https://"),
                        // 防止保存后所有网络请求 host 解析失败。
                        val hostPart = cleaned.substringAfter("://", missingDelimiterValue = "")
                        if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
                            if (hostPart.isBlank()) {
                                Log.w(TAG, "EditServerUrlDialog: rejected empty host (inLen=${input.length})")
                                return@TextButton
                            }
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
