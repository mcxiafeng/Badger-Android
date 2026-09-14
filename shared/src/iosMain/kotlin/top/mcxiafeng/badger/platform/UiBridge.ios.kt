package top.mcxiafeng.badger.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler as ComposeBackHandler
import platform.Foundation.NSLog

actual fun showToast(message: String) {
    NSLog("BadgerToast: %@", message)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    ComposeBackHandler(enabled = enabled, onBack = onBack)
}
