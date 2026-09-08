package top.mcxiafeng.badger.pages.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.utils.BadgerLog
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult

private const val TAG = "SettingsMessage"

/**
 * 设置域 Snackbar 默认时长（毫秒）。
 *
 * 取代原先散落 8+ 处的 `SnackbarDuration.Custom(1800)` / `(1500)` 裸数字。
 */
internal const val SETTINGS_SNACKBAR_DURATION_MS: Long = 1800L

/**
 * 设置域统一一次性 UI 消息（Snackbar 呈现）。
 *
 * 各设置页 VM 通过 `Channel<SettingsUiMessage>` 上抛，[SettingsMessageEffect] 消费。
 * 取代原先每页自定义 sealed message 类（SyncStatusMessage / TagManagerMessage 等）的碎片化。
 *
 * public 以便 public VM 暴露 `Flow<SettingsUiMessage>`（模块内无泄漏风险）。
 */
data class SettingsUiMessage(
    val text: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

/**
 * 把 VM 的消息流接到 [SnackbarHostState]。
 *
 * 用法（VM）：
 * ```
 * private val _messages = Channel<SettingsUiMessage>(Channel.BUFFERED)
 * val messages: ReceiveChannel<SettingsUiMessage> = _messages
 * private fun snackbar(text: String) { _messages.trySend(SettingsUiMessage(text)) }
 * ```
 * 用法（Screen）：
 * ```
 * val snackbarHostState = remember { SnackbarHostState() }
 * SettingsMessageEffect(snackbarHostState, viewModel.messages)
 * ```
 *
 * 携带 [SettingsUiMessage.onAction] 的消息会以可点按钮呈现，点击回调由消息自带。
 */
@Composable
internal fun SettingsMessageEffect(
    snackbarHostState: SnackbarHostState,
    messages: Flow<SettingsUiMessage>,
) {
    LaunchedEffect(snackbarHostState, messages) {
        messages.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = message.text,
                actionLabel = message.actionLabel,
                duration = SnackbarDuration.Custom(SETTINGS_SNACKBAR_DURATION_MS),
            )
            if (result == SnackbarResult.ActionPerformed) {
                message.onAction?.invoke()
            }
        }
    }
}

/**
 * 便捷发送：把异常消息推入 Channel（容错，Channel 满即丢）。
 */
internal fun Channel<SettingsUiMessage>.postError(tag: String, msg: String, error: Throwable?) {
    val text = if (error != null) "$msg（${error::class.simpleName}）" else msg
    BadgerLog.e(tag, "$msg", error)
    trySend(SettingsUiMessage(text))
}

/** 便捷发送普通提示。 */
internal fun Channel<SettingsUiMessage>.postInfo(msg: String) {
    trySend(SettingsUiMessage(msg))
}
