package top.mcxiafeng.badger.platform

import androidx.compose.runtime.Composable

expect fun showToast(message: String)

@Composable
expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)
