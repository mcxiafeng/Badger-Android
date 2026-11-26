package top.mcxiafeng.badger.ui.glass

import android.util.Log
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import top.mcxiafeng.badger.ui.designsystem.BadgerShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import top.mcxiafeng.badger.ui.designsystem.continuousClip as clip
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.yukonga.miuix.kmp.theme.LocalContentColor

internal val LocalGroupedActions = staticCompositionLocalOf { false }

/** One optical surface for the toolbar; each child keeps its own action and hit target. */
@Composable
internal fun GlassActionGroup(content: @Composable RowScope.() -> Unit) {
    val style = LocalGlassStyle.current
    // This explicit toolbar remains glass even on pages whose standalone buttons are flat.
    CompositionLocalProvider(LocalGlassStyle provides style.copy(glassButtons = true)) {
        val foreground = glassButtonForeground()
        LaunchedEffect(style.enabled, style.materialEnabled) {
            Log.d(GLASS_LOG_TAG, "Grouped toolbar; glass=${style.enabled}; material=${style.materialEnabled}; fillAlpha=${if (style.materialEnabled) BUTTON_TINT_ALPHA else 1f}; capsule=true")
        }
        Row(Modifier.glassPressFeedback(style.enabled, BadgerRadius.large)
            .clip(BadgerShapes.Capsule).buttonGlass(BadgerRadius.large),
            verticalAlignment = Alignment.CenterVertically) {
            CompositionLocalProvider(
                LocalGlassStyle provides style.copy(glassButtons = false),
                LocalGroupedActions provides true,
                LocalContentColor provides foreground,
            ) { content() }
        }
    }
}
