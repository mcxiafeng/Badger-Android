package top.mcxiafeng.badger.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val SKELETON_ALPHA_MIN = 0.4f
private const val SKELETON_ALPHA_MAX = 0.9f

/**
 * 骨架占位块：surfaceContainer 呼吸透明度，形状（尺寸 / 圆角）由调用方的 modifier 决定。
 * 按最终布局形状组合多个 [SkeletonBox]，替代转圈加载。
 */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier) {
    val breath = rememberInfiniteTransition(label = "skeletonBreath")
    val alpha by breath.animateFloat(
        initialValue = SKELETON_ALPHA_MIN,
        targetValue = SKELETON_ALPHA_MAX,
        animationSpec = infiniteRepeatable(
            animation = tween(BadgerMotion.DURATION_SLOW),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonBreathAlpha",
    )
    Box(
        modifier = modifier.background(
            MiuixTheme.colorScheme.surfaceContainer.copy(alpha = alpha),
        )
    )
}
