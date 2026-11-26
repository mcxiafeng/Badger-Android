package top.mcxiafeng.badger.ui.glass

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.basic.CardDefaults

internal const val MODAL_POPUP_DIM_ALPHA = 0.15f
internal const val MODAL_POPUP_WHITE_FILL_ALPHA = 0.09f
internal val LocalModalPopupWindow = staticCompositionLocalOf { false }

internal fun modalPopupDimAlpha(progress: Float): Float =
    MODAL_POPUP_DIM_ALPHA * progress.coerceIn(0f, 1f)

internal fun modalPopupFill(style: GlassStyle, cardColor: Color): Color =
    if (style.materialEnabled) Color.White.copy(alpha = MODAL_POPUP_WHITE_FILL_ALPHA) else cardColor.copy(alpha = 1f)

/** One scrim per modal window; button dropdowns keep their own original dimming policy. */
@Composable
internal fun ModalPopupDim(motion: PopupMotionState) {
    SideEffect {
        Log.d("ModalPopupStyleTester", "Modal backdrop; blackAlpha=$MODAL_POPUP_DIM_ALPHA; sharedWithPanelMotion=true")
    }
    Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black, alpha = modalPopupDimAlpha(motion.progress.value)) })
}

/** Glass uses a white tint; flat panels use the theme's fully opaque card surface. */
@Composable
internal fun Modifier.modalPopupSurface(
    radius: Dp,
    underlay: Color,
    backdrop: GlassBackdrop? = LocalGlassBackdrop.current,
    bounds: DrawScope.() -> RoundRect = {
        RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius.toPx()))
    },
): Modifier {
    val style = LocalGlassStyle.current
    val fill = modalPopupFill(style, CardDefaults.defaultColors().color)
    SideEffect {
        Log.d("ModalPopupStyleTester", "Modal panel; material=${style.materialEnabled}; dark=${style.dark}; fillAlpha=${fill.alpha}; whiteFill=${style.materialEnabled}; optics=${style.opticsEnabled}")
    }
    return if (style.opticsEnabled) glassSurface(radius, fill, underlay, backdrop, bounds = bounds)
        else solidMaterialSurface(fill, radius, opacity = { 1f }, bounds = bounds)
}
