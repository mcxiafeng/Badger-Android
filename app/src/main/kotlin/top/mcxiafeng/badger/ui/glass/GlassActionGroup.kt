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
import top.mcxiafeng.badger.ui.designsystem.continuousClip as clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val CONTRAST_CROSSOVER = 0.179f

/**
 * 顶栏右上角动作组的共享胶囊包裹，子按钮保持独立点击目标。跟随「液态导航栏」材质开关：
 * 液态开 = 中性亮暗玻璃胶囊（`GlassOpticalParameters.Bar` 软透镜档，与液态导航栏同一管线，
 * 底色随日夜模式亮暗切换、不掺主题色），前景按近似填充亮度取黑/白对比色；
 * 液态关 = 不透明卡片色胶囊配中性 onSurface 前景（扁平悬浮 chrome 规则）。
 */
@Composable
internal fun GlassActionGroup(content: @Composable RowScope.() -> Unit) {
    val style = LocalGlassStyle.current
    val colors = MiuixTheme.colorScheme
    LaunchedEffect(style.materialEnabled, style.dark) {
        Log.d(GLASS_LOG_TAG, "Grouped toolbar; material=${style.materialEnabled}; capsule=true; neutralChrome=true; radiusDp=${BadgerRadius.large.value}")
    }
    if (style.materialEnabled) {
        // The group takes the liquid navbar's neutral day/night chrome tint; the long pill uses
        // the soft lens so the mid-band stays quiet and the dispersion low, or a wide capsule
        // stripes over content.
        val fill = style.neutralChromeTint
        val foreground = if (fill.compositeOver(colors.surface).luminance() > CONTRAST_CROSSOVER) {
            Color.Black
        } else {
            Color.White
        }
        Row(
            Modifier.clip(BadgerShapes.Capsule)
                .glassSurface(BadgerRadius.large, fill, colors.surface,
                    opticalParameters = { GlassOpticalParameters.Bar }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentColor provides foreground) { content() }
        }
    } else {
        Row(
            Modifier.background(colors.surfaceContainer, BadgerShapes.Capsule)
                .clip(BadgerShapes.Capsule),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentColor provides colors.onSurface) { content() }
        }
    }
}
