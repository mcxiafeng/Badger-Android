package top.mcxiafeng.badger.ui.blur

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp as colorLerp

// Adapted from BiliPai LiquidIndicator.kt:526-630 — 纯 Canvas 绘制，零 GPU 依赖。

/**
 * 水珠透镜参数：由拖拽状态驱动
 */
data class LiquidLensProfile(
    val shouldRefract: Boolean = false,
    val motionFraction: Float = 0f,
    val refractionAmount: Float = 0f,
    val refractionHeight: Float = 0f,
    val centerHighlightAlpha: Float = 0.12f,
    val edgeCompressionAlpha: Float = 0.03f,
    val aberrationStrength: Float = 0f,
)

/**
 * 水珠视觉调优：由 BlurIntensity 映射
 */
data class LiquidGlassTuning(
    val progress: Float = 0f,
    val surfaceAlpha: Float = 0.58f,
    val whiteOverlayAlpha: Float = 0.12f,
    val chromaticAberrationAmount: Float = 0.3f,
)

/**
 * 根据 BlurIntensity 获取对应 Tuning
 */
fun BlurIntensity.toLiquidGlassTuning(): LiquidGlassTuning = when (this) {
    BlurIntensity.THIN -> LiquidGlassTuning(
        progress = 0f,
        surfaceAlpha = 0.03f,
        whiteOverlayAlpha = 0.35f,
        chromaticAberrationAmount = 0.2f,
    )
    BlurIntensity.THICK -> LiquidGlassTuning(
        progress = 0.5f,
        surfaceAlpha = 0.04f,
        whiteOverlayAlpha = 0.45f,
        chromaticAberrationAmount = 0.3f,
    )
    BlurIntensity.APPLE_DOCK -> LiquidGlassTuning(
        progress = 1f,
        surfaceAlpha = 0.06f,
        whiteOverlayAlpha = 0.55f,
        chromaticAberrationAmount = 0.4f,
    )
}

/**
 * 纯 Canvas 绘制玻璃球面 — 2 层高光 + 环。
 * 水滴身体 = 壳层 Haze 模糊提供透亮背景，Canvas 仅画：
 *   - 锐利镜面高光（玻璃反光点）
 *   - 极淡顶部白 rim（暗示球体上曲面）
 *   - 金属边缘环（定义球形边界）
 * 不做底色——白色颜料会杀透亮感，让 Haze 模糊背景透出。
 */
fun DrawScope.drawLiquidSphereSurface(
    baseColor: Color,
    lensProfile: LiquidLensProfile,
    tuning: LiquidGlassTuning,
    accentTint: Color? = null,
) {
    val isMoving = lensProfile.shouldRefract
    val tintColor = accentTint ?: baseColor

    // 镜面高光 — 小而锐利，模拟玻璃曲面反光
    val specCoreAlpha = tuning.whiteOverlayAlpha * 0.55f * (if (isMoving) 1.6f else 1f)

    // 顶部 rim — 极微弱白色从顶部渗入，仅暗示曲面存在
    val topRimAlpha = tuning.whiteOverlayAlpha * 0.06f * (if (isMoving) 1.3f else 1f)

    val w = size.width
    val h = size.height
    val minDim = size.minDimension

    // Step 1: 锐利镜面高光 — 小亮白点，4 色阶紧凑排布形成硬切
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = specCoreAlpha),
                Color.White.copy(alpha = specCoreAlpha * 0.85f),
                Color.White.copy(alpha = specCoreAlpha * 0.30f),
                Color.Transparent,
            ),
            center = Offset(x = w / 2f, y = h * 0.40f),
            radius = minDim * 0.18f,
        ),
    )

    // Step 2: 顶部 rim — 仅白色从顶部渗入，无底部暗色
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = topRimAlpha),
                Color.Transparent,
            ),
        ),
    )

    // Step 3: 金属边缘环 — 干净 5 色环，带主题色调
    val ringAlpha = if (isMoving) 0.20f else 0.12f
    if (ringAlpha > 0.01f) {
        val ringStroke = (minDim * 0.035f).coerceAtLeast(1f)
        val ringHighlight = colorLerp(tintColor, Color.White, 0.45f).copy(alpha = ringAlpha)
        val ringMid = colorLerp(tintColor, Color.White, 0.18f).copy(alpha = ringAlpha * 0.75f)
        val ringShadow = colorLerp(tintColor, Color.Black, 0.15f).copy(alpha = ringAlpha * 0.55f)
        drawRoundRect(
            brush = Brush.sweepGradient(
                colors = listOf(ringHighlight, ringMid, ringShadow, ringMid, ringHighlight),
                center = Offset(w / 2f, h / 2f),
            ),
            cornerRadius = CornerRadius(h / 2f, h / 2f),
            style = Stroke(width = ringStroke),
        )
    }
}