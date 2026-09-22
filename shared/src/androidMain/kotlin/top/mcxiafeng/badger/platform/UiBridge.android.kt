package top.mcxiafeng.badger.platform

import androidx.activity.compose.BackHandler as ActivityBackHandler
import androidx.compose.runtime.Composable

actual fun showToast(message: String) {
    // Android toast stub
}

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    ActivityBackHandler(enabled = enabled, onBack = onBack)
}
