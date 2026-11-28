package top.mcxiafeng.badger.ui.glass

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerShapes
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 顶栏右上角动作组的共享胶囊包裹，子按钮保持独立点击目标。
 * 胶囊底色与 Scaffold 同色（surface），在页面顶部与背景融为一体，前景为中性 onSurface。
 */
@Composable
internal fun GlassActionGroup(content: @Composable RowScope.() -> Unit) {
    val colors = MiuixTheme.colorScheme
    LaunchedEffect(Unit) {
        Log.d(GLASS_LOG_TAG, "Grouped toolbar; capsule=true; scaffoldBackground=true; radiusDp=${BadgerRadius.large.value}")
    }
    Row(
        Modifier.background(colors.surface, BadgerShapes.Capsule),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onSurface) { content() }
    }
}
