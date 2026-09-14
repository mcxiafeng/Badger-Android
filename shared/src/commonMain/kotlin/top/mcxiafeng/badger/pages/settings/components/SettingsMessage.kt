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

internal const val SETTINGS_SNACKBAR_DURATION_MS: Long = 1800L

data class SettingsUiMessage(
    val text: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

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

internal fun Channel<SettingsUiMessage>.postError(tag: String, msg: String, error: Throwable?) {
    val text = if (error != null) "$msg（${error::class.simpleName}）" else msg
    BadgerLog.e(tag, "$msg", error)
    trySend(SettingsUiMessage(text))
}

internal fun Channel<SettingsUiMessage>.postInfo(msg: String) {
    trySend(SettingsUiMessage(msg))
}
