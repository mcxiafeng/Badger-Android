package top.mcxiafeng.badger.ui.glass

import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val BLUR_LOG_TAG = "ProgressiveTopBlurTester"
private const val BLUR_MARGIN_SIGMAS = 3f
internal const val GAUSSIAN_LEVEL_COUNT = 8
private const val MASK_STOPS = 64
private const val MAX_COLOR_ALPHA = 255

/** Hosts without a [TopEdgeScrollState] keep the full effect; strength never drops below the baseline. */
private const val FULL_EDGE_STRENGTH = 1f
private const val MIN_EDGE_STRENGTH = 0.004f

/** One shared screen-space region for opacity and Gaussian radius, including the status-bar area. */
internal data class ProgressiveTopBlurGeometry(
    val size: Size,
    val screenTop: Float,
    val radius: Float,
    val region: TopEffectRegion = TopEffectRegion(screenTop + size.height),
) {
    val samplingMargin = radius * BLUR_MARGIN_SIGMAS
    val sourceSize = IntSize(
        ceil(size.width + 2f * samplingMargin).toInt(),
        ceil(size.height + 2f * samplingMargin).toInt(),
    )
    val fadeStartLocalY = region.localTop(screenTop)
    val fadeEndLocalY = region.localBottom(screenTop)
    val effectTopLocalY = fadeStartLocalY.coerceIn(0f, size.height)
    val effectBottomLocalY = fadeEndLocalY.coerceIn(0f, size.height)

    fun strengthAt(localY: Float): Float =
        progressiveTopBlurStrength((localY + screenTop) / region.bottomScreenY)

    fun radiusAt(localY: Float): Float = radius * strengthAt(localY)
}

internal fun progressiveTopBlurStrength(fraction: Float): Float {
    val progress = fraction.coerceIn(0f, 1f)
    return 1f - progress * progress * (3f - 2f * progress)
}

/** Adjacent Gaussian radii crossfade with byte weights summing to 255, without brightening. */
internal fun gaussianLevelAlpha(level: Int, strength: Float): Int {
    val position = strength.coerceIn(0f, 1f) * (GAUSSIAN_LEVEL_COUNT - 1)
    val lower = position.toInt()
    val upperAlpha = ((position - lower) * MAX_COLOR_ALPHA).roundToInt()
    return when (level) {
        lower -> MAX_COLOR_ALPHA - upperAlpha
        lower + 1 -> upperAlpha
        else -> 0
    }
}

/** Single overlay above the already-faded scroll content, below the pinned top bar. */
@Composable
internal fun Modifier.progressiveTopBlur(
    region: State<TopEffectRegion?>,
    edgeScroll: TopEdgeScrollState? = null,
): Modifier {
    val backdrop = LocalGlassBackdrop.current
    val origin = remember { mutableStateOf(Offset.Unspecified) }
    val renderer = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ProgressiveBlurRenderer() else null
    }
    val saturation = glassBlurSaturation(LocalAppDarkTheme.current)
    LaunchedEffect(renderer) {
        Log.d(BLUR_LOG_TAG, "Top blur initialized; gaussianOnly=true; levels=$GAUSSIAN_LEVEL_COUNT; source=afterScrollFade; strength=basePlusScroll; blurSaturation=$saturation")
    }
    return onGloballyPositioned { origin.value = it.glassOrigin() }.drawWithCache {
        val screenTop = if (origin.value == Offset.Unspecified) 0f else origin.value.y
        val bounds = region.value
        val geometry = bounds?.let {
            ProgressiveTopBlurGeometry(size, screenTop, BadgerSize.topProgressiveBlurRadius.toPx(), it)
        }
        onDrawBehind {
            val offset = backdrop?.sourceOffset(origin.value)
            // Scroll edge effect: strength is read per frame in the draw phase, so scrolling
            // repaints without recomposition. It never drops below the shared baseline, so the
            // mist always coexists with the glass bottom bar; the additive levels scale linearly
            // with it and the content fade keeps its bottom stop opaque, so no state has a hole.
            val strength = edgeScroll?.strength ?: FULL_EDGE_STRENGTH
            if (renderer != null && backdrop != null && offset != null && geometry != null &&
                strength > MIN_EDGE_STRENGTH &&
                geometry.region.bottomScreenY > 0f && drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
                renderer.draw(this, backdrop, offset, geometry, strength, saturation)
            }
        }
    }
}

/** Full-resolution Gaussian samples. Each radius owns a RenderNode to avoid effect mutation aliasing. */
@RequiresApi(Build.VERSION_CODES.S)
private class ProgressiveBlurRenderer {
    private val source = RenderNode("Badger faded scroll source")
    private val levels = Array(GAUSSIAN_LEVEL_COUNT) { GaussianBlurLevel(it) }
    private var cachedGeometry: ProgressiveTopBlurGeometry? = null
    private var cachedSaturation = 0f

    fun draw(
        scope: DrawScope, backdrop: GlassBackdrop, offset: Offset,
        geometry: ProgressiveTopBlurGeometry, strength: Float, saturation: Float,
    ) {
        if (geometry.size.width <= 0f || geometry.effectBottomLocalY <= geometry.effectTopLocalY) return
        recordSource(scope, backdrop, offset, geometry)
        if (cachedGeometry != geometry || cachedSaturation != saturation) {
            levels.forEach { it.configure(geometry, source, saturation) }
            cachedGeometry = geometry
            cachedSaturation = saturation
            Log.d(BLUR_LOG_TAG, "Gaussian region screen=0..${geometry.region.bottomScreenY}; " +
                "local=${geometry.fadeStartLocalY}..${geometry.fadeEndLocalY}; clip=${geometry.effectTopLocalY}..${geometry.effectBottomLocalY}; " +
                "maxRadiusPx=${geometry.radius}; source=afterScrollFade; blurSaturation=$saturation")
        }
        val canvas = scope.drawContext.canvas.nativeCanvas
        val saved = canvas.saveLayer(0f, geometry.effectTopLocalY, scope.size.width, geometry.effectBottomLocalY, null)
        canvas.clipRect(0f, geometry.effectTopLocalY, scope.size.width, geometry.effectBottomLocalY)
        levels.forEach { it.draw(canvas, geometry, strength) }
        canvas.restoreToCount(saved)
    }

    private fun recordSource(scope: DrawScope, backdrop: GlassBackdrop, offset: Offset, geometry: ProgressiveTopBlurGeometry) {
        val (width, height) = geometry.sourceSize
        source.setPosition(0, 0, width, height)
        val recording = source.beginRecording(width, height)
        try {
            recording.drawColor(backdrop.color.toArgb())
            recording.translate(offset.x + geometry.samplingMargin, offset.y + geometry.samplingMargin)
            scope.draw(scope, scope.layoutDirection, Canvas(recording), Size(width.toFloat(), height.toFloat()),
                scope.drawContext.graphicsLayer) { drawLayer(backdrop.layer) }
        } finally {
            source.endRecording()
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private class GaussianBlurLevel(private val level: Int) {
    private val node = RenderNode("Badger Gaussian level $level")
    private val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val add = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }

    fun configure(geometry: ProgressiveTopBlurGeometry, source: RenderNode, saturation: Float) {
        val (width, height) = geometry.sourceSize
        node.setPosition(0, 0, width, height)
        // Level 0 is the untouched passthrough the other levels crossfade into, so it must stay
        // unsaturated; every blurred level chains the restore matrix after the Gaussian, or
        // the mist averages colorful rows into milk. Level 0 must never create a RenderEffect at
        // all: its sigma is zero, and zero-sigma native creation throws on-device
        // (IllegalArgumentException: nativePtr is null, issuetracker 241546169).
        node.setRenderEffect(if (level == 0) null else {
            val sigma = geometry.radius * level / (GAUSSIAN_LEVEL_COUNT - 1)
            val blur = RenderEffect.createBlurEffect(sigma, sigma, Shader.TileMode.CLAMP)
            val restore = RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(glassBlurColorMatrix(saturation)))
            RenderEffect.createChainEffect(restore, blur)
        })
        val recording = node.beginRecording(width, height)
        try {
            recording.drawRenderNode(source)
        } finally {
            node.endRecording()
        }
        val colors = IntArray(MASK_STOPS + 1) { index ->
            val strength = progressiveTopBlurStrength(index.toFloat() / MASK_STOPS)
            val alpha = gaussianLevelAlpha(level, strength)
            AndroidColor.argb(alpha, MAX_COLOR_ALPHA, MAX_COLOR_ALPHA, MAX_COLOR_ALPHA)
        }
        // Null positions means evenly spaced normalized stops, independent of pixel size/origin.
        mask.shader = LinearGradient(0f, geometry.fadeStartLocalY, 0f, geometry.fadeEndLocalY,
            colors, null, Shader.TileMode.CLAMP)
    }

    fun draw(canvas: AndroidCanvas, geometry: ProgressiveTopBlurGeometry, strength: Float) {
        // Scaling every level's mask by the shared strength keeps the weights summing to 255
        // while the crisp content underneath shows through proportionally.
        mask.alpha = (MAX_COLOR_ALPHA * strength.coerceIn(0f, FULL_EDGE_STRENGTH)).roundToInt()
        val saved = canvas.saveLayer(0f, 0f, geometry.size.width, geometry.size.height, add)
        val translated = canvas.save()
        canvas.translate(-geometry.samplingMargin, -geometry.samplingMargin)
        canvas.drawRenderNode(node)
        canvas.restoreToCount(translated)
        canvas.drawRect(0f, 0f, geometry.size.width, geometry.size.height, mask)
        canvas.restoreToCount(saved)
    }
}
