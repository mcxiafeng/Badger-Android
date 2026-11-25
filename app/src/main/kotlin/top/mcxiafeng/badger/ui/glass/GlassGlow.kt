package top.mcxiafeng.badger.ui.glass

import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val GlowOuterWidth = 8.dp
internal val GlassGlowOutset = GlowOuterWidth / 2
private val GlowMiddleWidth = 4.dp
private val GlowCoreWidth = 1.dp
private const val OUTER_GLOW_ALPHA = 0.10f
private const val MIDDLE_GLOW_ALPHA = 0.24f
private const val CORE_GLOW_ALPHA = 0.88f
private const val OPAQUE_ALPHA = 255

/** Draw outside the lens clip so the complete outline survives its enlarged bounds. */
internal fun DrawScope.drawGlassGlow(path: Path, intensity: Float) {
    val strength = intensity.coerceIn(0f, 1f)
    if (strength == 0f) return
    drawPath(path, Color.White.copy(alpha = OUTER_GLOW_ALPHA * strength),
        style = Stroke(GlowOuterWidth.toPx()), blendMode = BlendMode.Plus)
    drawPath(path, Color.White.copy(alpha = MIDDLE_GLOW_ALPHA * strength),
        style = Stroke(GlowMiddleWidth.toPx()), blendMode = BlendMode.Plus)
    drawPath(path, Color.White.copy(alpha = CORE_GLOW_ALPHA * strength),
        style = Stroke(GlowCoreWidth.toPx()), blendMode = BlendMode.Plus)
}

/** Fade the optical pass and glow together while preserving the untinted source pixels. */
internal inline fun DrawScope.withGlassOpacity(panel: RoundRect, alpha: Float, draw: DrawScope.() -> Unit) {
    val canvas = drawContext.canvas.nativeCanvas
    val margin = GlowOuterWidth.toPx()
    val checkpoint = if (alpha < 1f) {
        canvas.saveLayerAlpha(panel.left - margin, panel.top - margin, panel.right + margin,
            panel.bottom + margin, (alpha * OPAQUE_ALPHA).roundToInt())
    } else {
        canvas.save()
    }
    try {
        draw()
    } finally {
        canvas.restoreToCount(checkpoint)
    }
}
