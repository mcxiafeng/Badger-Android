package top.mcxiafeng.badger.ui.glass

import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.mutableStateOf
import kotlin.math.roundToInt

/** Shared physical-screen bounds; system-bar insets never shift the top of either effect. */
internal data class TopEffectRegion(val bottomScreenY: Float) {
    fun localTop(screenY: Float): Float = -screenY
    fun localBottom(screenY: Float): Float = bottomScreenY - screenY
    fun contentAlpha(screenY: Float): Float = (screenY / bottomScreenY).coerceIn(0f, 1f)
}

internal val LocalTopEffectRegion = staticCompositionLocalOf<State<TopEffectRegion?>?> { null }

/** Attached to a scroll viewport, before its translation/scroll modifiers; never to page chrome. */
@Composable
internal fun Modifier.scrollTopFade(): Modifier {
    val region = LocalTopEffectRegion.current ?: return this
    val edgeScroll = LocalTopEdgeScroll.current
    val origin = remember { mutableStateOf(Offset.Unspecified) }
    val mask = remember { ScrollTopFadeMask() }
    return onGloballyPositioned { origin.value = it.glassOrigin() }.drawWithContent {
        val bounds = region.value
        val screenY = origin.value.let { if (it == Offset.Unspecified) 0f else it.y }
        if (bounds == null || bounds.bottomScreenY <= screenY) {
            drawContent()
        } else {
            // Scroll edge effect: strength sits at a shared baseline at rest and deepens as
            // content scrolls under the bar; the mask keeps its bottom stop fully opaque.
            val strength = edgeScroll?.strength ?: FULL_EDGE_STRENGTH
            val canvas = drawContext.canvas.nativeCanvas
            val saved = canvas.saveLayer(0f, 0f, size.width, size.height, null)
            drawContent()
            // Blend only the top effect strip; the lower viewport never participates in masking.
            val fadeBottom = bounds.localBottom(screenY).coerceIn(0f, size.height)
            canvas.drawRect(0f, 0f, size.width, fadeBottom, mask.paint(bounds, screenY, strength))
            canvas.restoreToCount(saved)
        }
    }
}

/**
 * A two-stop opacity mask: no pixel-valued colour stops, tint, or second content copy.
 *
 * Scroll strength only lifts the TOP stop toward opaque; the bottom white stop stays full so the
 * rows near the region bottom never lose opacity. Scaling the whole paint alpha instead would dim
 * the bottom stop too, leaving half-faded rows and a gray hole under the bar.
 */
internal class ScrollTopFadeMask {
    private var cachedRegion: TopEffectRegion? = null
    private var cachedScreenY = Float.NaN
    private var cachedStrength = Float.NaN
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    fun paint(region: TopEffectRegion, screenY: Float, strength: Float = FULL_EDGE_STRENGTH): Paint {
        val clamped = strength.coerceIn(0f, FULL_EDGE_STRENGTH)
        if (cachedRegion != region || cachedScreenY != screenY || cachedStrength != clamped) {
            val topStopAlpha = (OPAQUE_ALPHA * (FULL_EDGE_STRENGTH - clamped)).roundToInt()
            paint.shader = LinearGradient(
                0f, region.localTop(screenY), 0f, region.localBottom(screenY),
                AndroidColor.argb(topStopAlpha, OPAQUE_ALPHA, OPAQUE_ALPHA, OPAQUE_ALPHA),
                AndroidColor.WHITE, Shader.TileMode.CLAMP,
            )
            cachedRegion = region
            cachedScreenY = screenY
            cachedStrength = clamped
            Log.d("ScrollTopFadeTester", "Scroll viewport fade; screen=0..${region.bottomScreenY}; " +
                "viewportY=$screenY; strength=$clamped; topStopAlpha=$topStopAlpha")
        }
        return paint
    }
}

/** Hosts without a [TopEdgeScrollState] keep the full fade. */
private const val FULL_EDGE_STRENGTH = 1f
private const val OPAQUE_ALPHA = 255
