package top.mcxiafeng.badger.ui.glass

import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import top.mcxiafeng.badger.ui.designsystem.BadgerAlpha
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val BLUR_LOG_TAG = "ProgressiveTopBlurTester"
private const val BLUR_SAMPLE_SCALE = 0.25f
private const val BLUR_TAP_RADIUS = 12
private const val BLUR_SIGMA_STEP = 0.25f
private const val BLUR_MARGIN_MULTIPLIER = BLUR_TAP_RADIUS * BLUR_SIGMA_STEP
private const val BLUR_MASK_INTERVALS = 16
private const val MAX_COLOR_ALPHA = 255
private val BlurMaskPositions = FloatArray(BLUR_MASK_INTERVALS + 1) { it.toFloat() / BLUR_MASK_INTERVALS }

/** All visible coordinates are local pixels; only the shader coordinates include the sampling margin. */
internal data class ProgressiveTopBlurGeometry(val size: Size, val screenTop: Float, val radius: Float) {
    val margin = radius * BLUR_MARGIN_MULTIPLIER
    val sourceSize = IntSize(
        ceil((size.width + 2f * margin) * BLUR_SAMPLE_SCALE).toInt(),
        ceil((size.height + 2f * margin) * BLUR_SAMPLE_SCALE).toInt(),
    )
    val fadeStartLocalY = -screenTop
    val fadeEndLocalY = size.height
    val fadeEndScreenY = screenTop + size.height
    val profileHeight = (fadeEndLocalY - fadeStartLocalY).coerceAtLeast(1f)
    val shaderTop = (margin + fadeStartLocalY) * BLUR_SAMPLE_SCALE
    val shaderHeight = profileHeight * BLUR_SAMPLE_SCALE
    val shaderRadius = radius * BLUR_SAMPLE_SCALE

    fun strengthAt(localY: Float): Float = progressiveTopBlurStrength((localY - fadeStartLocalY) / profileHeight)
}

internal fun progressiveTopBlurStrength(fraction: Float): Float {
    val progress = fraction.coerceIn(0f, 1f)
    return 1f - progress * progress * (3f - 2f * progress)
}

/** A decoration only: the source excludes chrome, and this layer never receives pointer input. */
@Composable
internal fun Modifier.progressiveTopBlur(): Modifier {
    val backdrop = LocalGlassBackdrop.current
    val color = MiuixTheme.colorScheme.surface
    val origin = remember { mutableStateOf(Offset.Unspecified) }
    val renderer = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ProgressiveBlurRenderer() else null
    }
    LaunchedEffect(renderer) {
        Log.d(BLUR_LOG_TAG, "Top blur initialized; hardwareEffectAvailable=${renderer != null}; sampleScale=$BLUR_SAMPLE_SCALE; buttonsExcluded=true; contentVeil=100%..0%; gaussianRetained=true")
    }
    return onGloballyPositioned { origin.value = it.glassOrigin() }.drawWithCache {
        val screenTop = if (origin.value == Offset.Unspecified) 0f else origin.value.y
        val geometry = ProgressiveTopBlurGeometry(size, screenTop, BadgerSize.topProgressiveBlurRadius.toPx())
        val veil = Brush.verticalGradient(
            colorStops = Array(BlurMaskPositions.size) { index ->
                val position = BlurMaskPositions[index]
                position to color.copy(alpha = BadgerAlpha.TOP_CONTENT_VEIL_ALPHA * progressiveTopBlurStrength(position))
            },
            startY = geometry.fadeStartLocalY,
            endY = geometry.fadeStartLocalY + geometry.profileHeight,
        )
        onDrawBehind {
            val offset = backdrop?.sourceOffset(origin.value)
            if (renderer != null && backdrop != null && offset != null && drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
                renderer.draw(this, backdrop, offset, geometry)
            }
            // Keep the Gaussian pass; this extra veil hides content while it passes behind pinned chrome.
            drawRect(veil)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private class ProgressiveBlurRenderer {
    private val source = RenderNode("Badger progressive top blur")
    private val shaders = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ProgressiveBlurShaders() else null
    private val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private var cachedGeometry: ProgressiveTopBlurGeometry? = null

    fun draw(scope: DrawScope, backdrop: GlassBackdrop, offset: Offset, geometry: ProgressiveTopBlurGeometry) = with(scope) {
        val (width, height) = geometry.sourceSize
        if (size.width <= 0f || size.height <= 0f || width <= 0 || height <= 0) return@with
        configureEffect(geometry)
        recordSource(backdrop, offset, geometry.margin, width, height)
        val canvas = drawContext.canvas.nativeCanvas
        val saved = canvas.saveLayer(0f, 0f, size.width, size.height, null)
        canvas.clipRect(0f, 0f, size.width, size.height)
        val transform = canvas.save()
        canvas.translate(-geometry.margin, -geometry.margin)
        canvas.scale(1f / BLUR_SAMPLE_SCALE, 1f / BLUR_SAMPLE_SCALE)
        canvas.drawRenderNode(source)
        canvas.restoreToCount(transform)
        canvas.drawRect(0f, 0f, size.width, size.height, mask)
        canvas.restoreToCount(saved)
    }

    private fun configureEffect(geometry: ProgressiveTopBlurGeometry) {
        if (cachedGeometry == geometry) return
        // Both shader and opacity mask fade from screen y=0 to the measured action container bottom.
        val effect = shaders?.effect(geometry.shaderRadius, geometry.shaderHeight, geometry.shaderTop)
            ?: RenderEffect.createBlurEffect(geometry.shaderRadius, geometry.shaderRadius, Shader.TileMode.CLAMP)
        source.setRenderEffect(effect)
        val colors = IntArray(BlurMaskPositions.size) { index ->
            AndroidColor.argb((progressiveTopBlurStrength(BlurMaskPositions[index]) * MAX_COLOR_ALPHA).roundToInt(),
                MAX_COLOR_ALPHA, MAX_COLOR_ALPHA, MAX_COLOR_ALPHA)
        }
        mask.shader = LinearGradient(0f, geometry.fadeStartLocalY, 0f, geometry.fadeStartLocalY + geometry.profileHeight,
            colors, BlurMaskPositions, Shader.TileMode.CLAMP)
        cachedGeometry = geometry
        Log.d(BLUR_LOG_TAG, "Configured Gaussian blur; screenY=0..${geometry.fadeEndScreenY}; surfaceScreenTop=${geometry.screenTop}; radiusPx=${geometry.radius}; marginPx=${geometry.margin}; variableRadius=${shaders != null}; shaderStartY=${geometry.shaderTop}; shaderEndY=${geometry.shaderTop + geometry.shaderHeight}")
    }

    private fun DrawScope.recordSource(backdrop: GlassBackdrop, offset: Offset, margin: Float, width: Int, height: Int) {
        source.setPosition(0, 0, width, height)
        val recording = source.beginRecording(width, height)
        try {
            recording.drawColor(backdrop.color.toArgb())
            recording.scale(BLUR_SAMPLE_SCALE, BLUR_SAMPLE_SCALE)
            draw(this, layoutDirection, Canvas(recording), Size(width / BLUR_SAMPLE_SCALE, height / BLUR_SAMPLE_SCALE),
                drawContext.graphicsLayer) {
                translate(left = offset.x + margin, top = offset.y + margin) { drawLayer(backdrop.layer) }
            }
        } finally {
            source.endRecording()
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class ProgressiveBlurShaders {
    private val horizontal = RuntimeShader(PROGRESSIVE_BLUR_SHADER)
    private val vertical = RuntimeShader(PROGRESSIVE_BLUR_SHADER)

    fun effect(radius: Float, height: Float, top: Float): RenderEffect {
        listOf(horizontal, vertical).forEach { shader ->
            shader.setFloatUniform("radius", radius)
            shader.setFloatUniform("height", height)
            shader.setFloatUniform("top", top)
        }
        horizontal.setFloatUniform("axis", 1f, 0f)
        vertical.setFloatUniform("axis", 0f, 1f)
        return RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(vertical, "content"),
            RenderEffect.createRuntimeShaderEffect(horizontal, "content"))
    }
}

private const val PROGRESSIVE_BLUR_SHADER = """
    uniform shader content;
    uniform float radius;
    uniform float height;
    uniform float top;
    uniform float2 axis;
    half4 main(float2 p) {
        float sigma = radius * (1.0 - smoothstep(0.0, height, p.y - top));
        const int tapRadius = $BLUR_TAP_RADIUS;
        const float sigmaStep = $BLUR_SIGMA_STEP;
        half4 result = half4(0.0);
        float weightSum = 0.0;
        for (int tap = -tapRadius; tap <= tapRadius; tap++) {
            float distanceInSigma = float(tap) * sigmaStep;
            float weight = exp(-0.5 * distanceInSigma * distanceInSigma);
            result += content.eval(p + axis * sigma * distanceInSigma) * weight;
            weightSum += weight;
        }
        return result / weightSum;
    }
"""
