package top.mcxiafeng.badger.ui.glass

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import top.mcxiafeng.badger.ui.designsystem.addG2RoundRect

/** Flatten a panel's existing tint over an opaque theme surface, without sampling a backdrop. */
internal fun solidGlassFill(underlay: Color, tint: Color, fallback: Color): Color {
    val base = if (underlay == Color.Unspecified || underlay.alpha < 1f) fallback else underlay
    return tint.compositeOver(base.copy(alpha = 1f)).copy(alpha = 1f)
}

/** Same live bounds, content and transition opacity; no optical renderer, blur, rim or backdrop. */
@Composable
internal fun Modifier.solidMaterialSurface(
    fill: Color, radius: Dp, opacity: () -> Float, bounds: DrawScope.() -> RoundRect,
): Modifier {
    LaunchedEffect(fill, radius) {
        Log.d(GLASS_LOG_TAG, "Solid material; optics=false; backgroundBlur=false; fillAlpha=${fill.alpha}; radiusDp=${radius.value}; feedbackUnchanged=true")
    }
    return drawWithCache {
        val path = Path()
        onDrawWithContent {
            val panel = bounds()
            path.rewind()
            path.addG2RoundRect(panel)
            drawPath(path, fill, alpha = opacity().coerceIn(0f, 1f))
            drawContent()
        }
    }
}
