package top.mcxiafeng.badger.ui.glass

import android.graphics.ColorMatrix
import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.designsystem.BadgerAlpha
import top.mcxiafeng.badger.ui.designsystem.addG2RoundRect
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal const val GLASS_LOG_TAG = "GlassSurfaceTester"
internal const val CHROME_TINT_ALPHA = 0.19f

/**
 * Saturation restored after every Gaussian pass. Averaging desaturates, so a blur without this
 * chain reads as milk over colorful content (2026-11-27 material redo, per iOS-material recipes
 * calibrated against system screenshots; two liquid-glass skills converge on 1.45–1.70).
 */
internal const val GLASS_BLUR_SATURATION_LIGHT = 1.7f
internal const val GLASS_BLUR_SATURATION_DARK = 1.55f

internal fun glassBlurSaturation(dark: Boolean): Float =
    if (dark) GLASS_BLUR_SATURATION_DARK else GLASS_BLUR_SATURATION_LIGHT

/**
 * The single color matrix chained after every Gaussian: saturation restore plus a gentle
 * per-channel lift (R 1.05 / G 1.02 / B 1.06) so the pane stays crystal instead of dulling —
 * the golden-glass recipe; the slight blue bias reads as glass, not as a filter. Built from
 * Android's own setSaturation row structure (0.213/0.715/0.072) with each row scaled by its lift.
 */
internal fun glassBlurColorMatrix(saturation: Float): ColorMatrix {
    val invSat = 1f - saturation
    val r = SATURATION_LUMINANCE_RED * invSat
    val g = SATURATION_LUMINANCE_GREEN * invSat
    val b = SATURATION_LUMINANCE_BLUE * invSat
    return ColorMatrix(floatArrayOf(
        (r + saturation) * GLASS_LIFT_RED, g * GLASS_LIFT_RED, b * GLASS_LIFT_RED, 0f, 0f,
        r * GLASS_LIFT_GREEN, (g + saturation) * GLASS_LIFT_GREEN, b * GLASS_LIFT_GREEN, 0f, 0f,
        r * GLASS_LIFT_BLUE, g * GLASS_LIFT_BLUE, (b + saturation) * GLASS_LIFT_BLUE, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    ))
}

private const val SATURATION_LUMINANCE_RED = 0.213f
private const val SATURATION_LUMINANCE_GREEN = 0.715f
private const val SATURATION_LUMINANCE_BLUE = 0.072f
private const val GLASS_LIFT_RED = 1.05f
private const val GLASS_LIFT_GREEN = 1.02f
private const val GLASS_LIFT_BLUE = 1.06f
private val GlassRimWidth = 0.8.dp

/**
 * One complete hairline around every glass panel. A uniform stroke, not a vertical gradient:
 * gradient sides (8% white) read as a border cut off at the capsule ends, and additive white on
 * light surfaces disappears entirely. Dark glass keeps a light line; light glass takes a dark
 * hairline, the way light frosted recipes separate their panes.
 */
private val DarkGlassRim = Color(0x40FFFFFF)
private val LightGlassRim = Color(0x1F000000)

@Stable
internal data class GlassStyle(
    val enabled: Boolean = false,
    val dark: Boolean = false,
    val materialEnabled: Boolean = true,
    val floatingNavigation: Boolean = true,
) {
    /** The optics run independently of the navigation shape. */
    val opticsEnabled: Boolean get() = enabled && materialEnabled

    private val neutralTint: Color get() = if (dark) Color.White else Color.Black
    val chromeTint: Color get() = neutralTint.copy(alpha = CHROME_TINT_ALPHA)

    /**
     * 中性日夜铬面底色：液态导航栏与顶栏动作组胶囊共用，深色模式取白色铬面调色、
     * 浅色模式取淡黑调色，随日夜模式亮暗切换，不掺主题色。
     */
    val neutralChromeTint: Color
        get() = if (dark) chromeTint else Color.Black.copy(alpha = BadgerAlpha.LIGHT_NAV_BAR_BLACK_TINT)
}

internal val LocalAppDarkTheme = staticCompositionLocalOf { false }
internal val LocalGlassStyle = staticCompositionLocalOf { GlassStyle() }
internal val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

@Stable
internal class GlassBackdrop(val layer: GraphicsLayer, val color: Color) {
    val origin = mutableStateOf(Offset.Unspecified)
}

internal fun LayoutCoordinates.glassOrigin(): Offset = localToScreen(Offset.Zero).let {
    if (it == Offset.Unspecified) localToWindow(Offset.Zero) else it
}

@Composable
internal fun rememberGlassBackdrop(color: Color, enabled: Boolean = LocalGlassStyle.current.opticsEnabled): GlassBackdrop? {
    if (!enabled) return null
    val layer = rememberGraphicsLayer()
    return remember(layer, color) { GlassBackdrop(layer, color) }
}

/** Keep the source in the GPU display list and draw it before any glass that samples it. */
@Composable
internal fun GlassBackdropContent(backdrop: GlassBackdrop?, content: @Composable () -> Unit) {
    val modifier = if (backdrop == null) Modifier else Modifier.recordGlassBackdrop(backdrop)
    Box(modifier, propagateMinConstraints = true) { content() }
}

internal fun Modifier.recordGlassBackdrop(backdrop: GlassBackdrop): Modifier = this
    .onGloballyPositioned { backdrop.origin.value = it.glassOrigin() }
    .drawWithContent {
        if (!drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
            drawContent()
        } else {
            backdrop.layer.record {
                drawRect(backdrop.color)
                this@drawWithContent.drawContent()
            }
            drawLayer(backdrop.layer)
        }
    }

@Composable
internal fun Modifier.glassSurface(
    radius: Dp,
    tint: Color,
    underlay: Color,
    backdrop: GlassBackdrop? = LocalGlassBackdrop.current,
    frosted: Boolean = true,
    opacity: () -> Float = { 1f },
    edgeGlow: () -> Float = { 0f },
    opticalParameters: () -> GlassOpticalParameters = { GlassOpticalParameters.Default },
    bounds: DrawScope.() -> RoundRect = {
        RoundRect(Offset.Zero.x, Offset.Zero.y, size.width, size.height, CornerRadius(radius.toPx()))
    },
): Modifier {
    val origin = remember { mutableStateOf(Offset.Unspecified) }
    val position = if (backdrop == null) Modifier else Modifier.onGloballyPositioned { origin.value = it.glassOrigin() }
    val saturation = glassBlurSaturation(LocalAppDarkTheme.current)
    val rim = if (LocalAppDarkTheme.current) DarkGlassRim else LightGlassRim
    return this.then(position).drawWithCache {
        val path = Path()
        val renderer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) GlassOptics() else null
        val stroke = Stroke(width = GlassRimWidth.toPx())
        onDrawWithContent {
            val panel = bounds()
            val alpha = opacity().coerceIn(0f, 1f)
            if (panel.width > 0f && panel.height > 0f && alpha > 0f) {
                val sourceOffset = backdrop?.sourceOffset(origin.value)
                path.rewind()
                path.addG2RoundRect(panel)
                withGlassOpacity(panel, alpha) {
                    drawGlassGlow(path, edgeGlow())
                    clipPath(path) {
                        if (renderer != null && drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
                            renderer.draw(this, panel, backdrop, sourceOffset, underlay, tint, frosted, saturation, opticalParameters())
                        } else {
                            drawFallback(backdrop, sourceOffset, underlay, tint, frosted)
                        }
                        if (frosted || renderer == null) drawPath(path, rim, style = stroke)
                    }
                }
            }
            drawContent()
        }
    }
}

internal fun GlassBackdrop.sourceOffset(targetOrigin: Offset): Offset? {
    val sourceOrigin = origin.value
    return if (sourceOrigin == Offset.Unspecified || targetOrigin == Offset.Unspecified || layer.size == IntSize.Zero) {
        null
    } else {
        sourceOrigin - targetOrigin
    }
}

private fun DrawScope.drawFallback(
    backdrop: GlassBackdrop?,
    offset: Offset?,
    underlay: Color,
    tint: Color,
    frosted: Boolean,
) {
    if (frosted) drawRect(underlay)
    if (backdrop != null && offset != null && drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
        translate(left = offset.x, top = offset.y) { drawLayer(backdrop.layer) }
    }
    if (tint.alpha > 0f) drawRect(tint)
}

internal fun logGlassStyle(style: GlassStyle) {
    Log.d(GLASS_LOG_TAG, "Glass styling=${style.enabled}; material=${style.materialEnabled}; floatingNavigation=${style.floatingNavigation}; dark=${style.dark}; runtimeShader=${style.opticsEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU}")
}
