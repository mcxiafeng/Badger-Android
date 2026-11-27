package top.mcxiafeng.badger.ui.glass

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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
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
 * Popup menus follow the grouped toolbar's capsule radius so a menu reads as an extension of the
 * button that opened it. Dialog panels keep the larger [BadgerRadius.menu] profile.
 */
internal val GlassMenuRadius = BadgerRadius.large
private val GlassRimWidth = 0.8.dp
private val BrightRim = Color(0x59FFFFFF)
private val SideRim = Color(0x14FFFFFF)

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

    /** Danger variant of [buttonFill]: neutral gray until ready, then the caller's accent. */
    @Composable
    fun buttonFill(ready: Boolean, accent: Color): Color {
        if (!enabled) return accent
        val base = if (ready) accent else if (materialEnabled) neutralTint else MiuixTheme.colorScheme.surfaceVariant
        return base.copy(alpha = if (materialEnabled) BUTTON_TINT_ALPHA else 1f)
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
): Modifier = buttonGlass(radius, LocalGlassStyle.current.buttonFill(ready), underlay)

/** Accent variant so a button can transition its fill between gray and a custom color. */
@Composable
internal fun Modifier.buttonGlass(
    radius: Dp,
    fill: Color,
    underlay: Color,
): Modifier {
    if (!LocalGlassStyle.current.buttonsEnabled) return this
    val colors = MiuixTheme.colorScheme
    return glassSurface(
        radius = radius,
        tint = fill,
        underlay = if (underlay == Color.Unspecified) colors.surface else underlay,
        solidColor = fill,
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
        val fill = if (solidColor == Color.Unspecified) solidGlassFill(underlay, tint, MiuixTheme.colorScheme.surface)
            else solidColor.copy(alpha = 1f)
        return solidMaterialSurface(fill, radius, opacity, bounds)
    }
    val origin = remember { mutableStateOf(Offset.Unspecified) }
    val position = if (backdrop == null) Modifier else Modifier.onGloballyPositioned { origin.value = it.glassOrigin() }
    return this.then(position).drawWithCache {
        val path = Path()
        val renderer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) GlassOptics() else null
        val rim = Brush.verticalGradient(listOf(BrightRim, SideRim, BrightRim))
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
                            renderer.draw(this, panel, backdrop, sourceOffset, underlay, tint, frosted, opticalParameters())
                        } else {
                            drawFallback(backdrop, sourceOffset, underlay, tint, frosted)
                        }
                        if (frosted || renderer == null) drawPath(path, rim, style = stroke, blendMode = BlendMode.Plus)
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
