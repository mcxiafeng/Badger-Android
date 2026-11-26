package top.mcxiafeng.badger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.designsystem.BadgerNavIndicator
import top.mcxiafeng.badger.ui.designsystem.addG2RoundRect
import top.mcxiafeng.badger.ui.glass.GlassBackdrop
import top.mcxiafeng.badger.ui.glass.GlassOpticalParameters
import top.mcxiafeng.badger.ui.glass.LocalGlassStyle
import top.mcxiafeng.badger.ui.glass.glassSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val INDICATOR_EDGE_DEPTH = 60f
private const val INDICATOR_DISPERSION = 18f
private val IndicatorOptics = GlassOpticalParameters(depth = INDICATOR_EDGE_DEPTH, dispersion = INDICATOR_DISPERSION)
private val IndicatorRadius = 64.dp

@Composable
internal fun Modifier.slidingTabIndicator(
    indicator: FloatingTabIndicator, backdrop: GlassBackdrop?,
): Modifier {
    val style = LocalGlassStyle.current
    val direction = LocalLayoutDirection.current
    val accent = MiuixTheme.colorScheme.primary
    val underlay = MiuixTheme.colorScheme.surface
    val optics = indicator.opticalProgress
    val idle = Modifier.drawWithCache {
        val path = Path()
        onDrawBehind {
            path.rewind()
            path.addG2RoundRect(indicator.bounds(size, direction))
            val fill = if (style.materialEnabled) Color.Black.copy(alpha = indicator.fillAlpha)
                else accent.copy(alpha = BadgerNavIndicator.SOLID_FILL_ALPHA)
            drawPath(path, fill)
        }
    }
    val lens = if (style.opticsEnabled) Modifier.glassSurface(
        radius = IndicatorRadius, tint = Color.Black.copy(alpha = BadgerNavIndicator.ACTIVE_FILL_ALPHA),
        underlay = underlay, backdrop = backdrop, frosted = false,
        opacity = { optics.value }, edgeGlow = { optics.value },
        opticalParameters = { IndicatorOptics.faded(optics.value) },
        bounds = { indicator.bounds(size, direction) },
    ) else Modifier
    return then(idle).then(lens)
}

/** Keep the selected content accent-coloured in both material modes. */
@Composable
internal fun Modifier.indicatorContentTint(indicator: FloatingTabIndicator): Modifier {
    val direction = LocalLayoutDirection.current
    val color = MiuixTheme.colorScheme.primary
    return drawWithCache {
        val path = Path()
        val paint = Paint().apply { colorFilter = ColorFilter.tint(color) }
        onDrawWithContent {
            drawContent()
            path.rewind()
            path.addG2RoundRect(indicator.bounds(size, direction))
            clipPath(path) {
                val canvas = drawContext.canvas
                canvas.saveLayer(Rect(Offset.Zero, size), paint)
                try { this@onDrawWithContent.drawContent() } finally { canvas.restore() }
            }
        }
    }
}
