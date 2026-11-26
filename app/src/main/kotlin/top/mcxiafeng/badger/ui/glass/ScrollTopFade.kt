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
    val origin = remember { mutableStateOf(Offset.Unspecified) }
    val mask = remember { ScrollTopFadeMask() }
    return onGloballyPositioned { origin.value = it.glassOrigin() }.drawWithContent {
        val bounds = region.value
        val screenY = origin.value.let { if (it == Offset.Unspecified) 0f else it.y }
        if (bounds == null || bounds.bottomScreenY <= screenY) {
            drawContent()
        } else {
            val canvas = drawContext.canvas.nativeCanvas
            val saved = canvas.saveLayer(0f, 0f, size.width, size.height, null)
            drawContent()
            // Blend only the top effect strip; the lower viewport never participates in masking.
            val fadeBottom = bounds.localBottom(screenY).coerceIn(0f, size.height)
            canvas.drawRect(0f, 0f, size.width, fadeBottom, mask.paint(bounds, screenY))
            canvas.restoreToCount(saved)
        }
    }
}

/** A two-stop opacity mask: no pixel-valued colour stops, tint, or second content copy. */
internal class ScrollTopFadeMask {
    private var cachedRegion: TopEffectRegion? = null
    private var cachedScreenY = Float.NaN
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    fun paint(region: TopEffectRegion, screenY: Float): Paint {
        if (cachedRegion != region || cachedScreenY != screenY) {
            paint.shader = LinearGradient(
                0f, region.localTop(screenY), 0f, region.localBottom(screenY),
                AndroidColor.TRANSPARENT, AndroidColor.WHITE, Shader.TileMode.CLAMP,
            )
            cachedRegion = region
            cachedScreenY = screenY
            Log.d("ScrollTopFadeTester", "Scroll viewport fade; screen=0..${region.bottomScreenY}; viewportY=$screenY")
        }
        return paint
    }
}
