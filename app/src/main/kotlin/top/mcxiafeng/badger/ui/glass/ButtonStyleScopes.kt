package top.mcxiafeng.badger.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
internal fun GlassButtonScope(content: @Composable () -> Unit) {
    val style = LocalGlassStyle.current
    CompositionLocalProvider(LocalGlassStyle provides style.copy(glassButtons = true), content = content)
}

@Composable
internal fun FlatButtonScope(content: @Composable () -> Unit) {
    val style = LocalGlassStyle.current
    CompositionLocalProvider(LocalGlassStyle provides style.copy(glassButtons = false), content = content)
}
