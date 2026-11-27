package top.mcxiafeng.badger.ui.glass

import android.graphics.ColorMatrix
import android.os.Build
import android.util.Log
import androidx.compose.foundation.background
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
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.addG2RoundRect
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal const val GLASS_LOG_TAG = "GlassSurfaceTester"
internal const val CHROME_TINT_ALPHA = 0.19f
internal const val MENU_TINT_ALPHA = 0.29f
internal const val LIGHT_MENU_TINT_ALPHA = 0.09f
internal const val BUTTON_TINT_ALPHA = 0.50f

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
/** Menus follow the card surface radius for a lighter corner profile than the dialog radius. */
internal val GlassMenuRadius = BadgerRadius.surface
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
    val glassButtons: Boolean = true,
    val materialEnabled: Boolean = true,
    val floatingNavigation: Boolean = true,
) {
    /** Styling/interaction stay enabled independently of the optical material and navigation shape. */
    val buttonsEnabled: Boolean get() = enabled && glassButtons
    val opticsEnabled: Boolean get() = enabled && materialEnabled
    /** Flat mode swaps the retained glass window for Miuix's own dialog and list popup. */
    val flatMode: Boolean get() = enabled && !materialEnabled

    private val neutralTint: Color get() = if (dark) Color.White else Color.Black
    val chromeTint: Color get() = neutralTint.copy(alpha = CHROME_TINT_ALPHA)
    val menuTint: Color get() = neutralTint.copy(alpha = if (dark) MENU_TINT_ALPHA else LIGHT_MENU_TINT_ALPHA)

    /** Shared button fill: neutral gray until content is valid, then the active accent. */
    @Composable
    fun buttonFill(ready: Boolean = true): Color {
        val colors = MiuixTheme.colorScheme
        if (!enabled) return colors.primary
        val base = if (ready) colors.primary else if (materialEnabled) neutralTint else colors.surfaceVariant
        return base.copy(alpha = if (materialEnabled) BUTTON_TINT_ALPHA else 1f)
    }

    /**
     * Floating chrome fill (icon buttons, toolbars, FAB, menus, the floating nav bar): flat mode
     * flattens onto the theme's opaque card surface instead of the accent, with primary content.
     */
    @Composable
    fun floatingFill(ready: Boolean = true): Color {
        val colors = MiuixTheme.colorScheme
        if (!enabled) return colors.primary
        if (materialEnabled) {
            val base = if (ready) colors.primary else neutralTint
            return base.copy(alpha = BUTTON_TINT_ALPHA)
        }
        return if (ready) colors.surfaceContainer else colors.surfaceVariant
    }
}

internal val LocalAppDarkTheme = staticCompositionLocalOf { false }
internal val LocalGlassStyle = staticCompositionLocalOf { GlassStyle() }
internal val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }
internal val LocalMenuBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }
internal val LocalGlassUnderlay = staticCompositionLocalOf { Color.Unspecified }

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
internal fun Modifier.buttonGlass(
    radius: Dp,
    ready: Boolean = true,
    underlay: Color = LocalGlassUnderlay.current,
    floating: Boolean = true,
    opticalParameters: () -> GlassOpticalParameters = { GlassOpticalParameters.Icon },
    bounds: DrawScope.() -> RoundRect = {
        RoundRect(Offset.Zero.x, Offset.Zero.y, size.width, size.height, CornerRadius(radius.toPx()))
    },
): Modifier {
    val style = LocalGlassStyle.current
    if (!style.buttonsEnabled) return this
    val colors = MiuixTheme.colorScheme
    val fill = if (floating) style.floatingFill(ready) else style.buttonFill(ready)
    return glassSurface(
        radius = radius,
        tint = fill,
        underlay = if (underlay == Color.Unspecified) colors.surface else underlay,
        solidColor = fill,
        opticalParameters = opticalParameters,
        bounds = bounds,
    )
}

@Composable
internal fun Modifier.buttonGlassOrBackground(
    radius: Dp,
    color: Color,
    underlay: Color = LocalGlassUnderlay.current,
    ready: Boolean = true,
): Modifier =
    if (LocalGlassStyle.current.buttonsEnabled) buttonGlass(radius, ready, underlay) else background(color)

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
    solidColor: Color = Color.Unspecified,
    bounds: DrawScope.() -> RoundRect = {
        RoundRect(Offset.Zero.x, Offset.Zero.y, size.width, size.height, CornerRadius(radius.toPx()))
    },
): Modifier {
    if (!LocalGlassStyle.current.materialEnabled) {
        // Flat panels keep the theme's current light/dark card surface; no tint compositing, no optics.
        val fill = if (solidColor == Color.Unspecified) MiuixTheme.colorScheme.surfaceContainer
        else solidColor.copy(alpha = 1f)
        return solidMaterialSurface(fill, radius, opacity, bounds)
    }
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
