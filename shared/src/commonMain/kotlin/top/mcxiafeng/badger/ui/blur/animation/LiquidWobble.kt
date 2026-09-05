package top.mcxiafeng.badger.ui.blur.animation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 液态晃动形状：4 角 radius 施加正弦扰动，产生"果冻"晃动效果。
 * idle 时启用，滑动/拖拽时禁用。
 */
class WobbleShape(
    private val baseCornerRadius: Dp,
    private val wobbleA: Float,
    private val wobbleB: Float,
    private val wobbleC: Float,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ) = androidx.compose.ui.graphics.Outline.Rounded(
        roundRect = with(density) {
            val base = baseCornerRadius.toPx()
            val tl = (base + wobbleA).coerceAtLeast(0f)
            val tr = (base + wobbleB).coerceAtLeast(0f)
            val br = (base + wobbleC).coerceAtLeast(0f)
            val bl = (base + (wobbleA + wobbleC) * 0.5f).coerceAtLeast(0f)
            RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                topLeftCornerRadius = CornerRadius(x = tl, y = tl),
                topRightCornerRadius = CornerRadius(x = tr, y = tr),
                bottomRightCornerRadius = CornerRadius(x = br, y = br),
                bottomLeftCornerRadius = CornerRadius(x = bl, y = bl),
            )
        }
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WobbleShape) return false
        return baseCornerRadius == other.baseCornerRadius
    }

    override fun hashCode(): Int = baseCornerRadius.hashCode()

    override fun toString(): String = "WobbleShape(cornerRadius=$baseCornerRadius)"
}

/**
 * 记住液态晃动状态，返回 [WobbleShape] 实例。
 * @param enabled 是否启用晃动（idle=true, scrolling=false）
 * @param baseCornerRadius 基础圆角
 */
@Composable
fun rememberLiquidWobble(
    enabled: Boolean,
    baseCornerRadius: Dp = 50.dp,
): WobbleShape {
    val transition = rememberInfiniteTransition(label = "liquidWobble")

    val wobbleA by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (enabled) 6f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wobbleA",
    )

    val wobbleB by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (enabled) -4f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wobbleB",
    )

    val wobbleC by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (enabled) 5f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wobbleC",
    )

    return WobbleShape(
        baseCornerRadius = baseCornerRadius,
        wobbleA = wobbleA,
        wobbleB = wobbleB,
        wobbleC = wobbleC,
    )
}