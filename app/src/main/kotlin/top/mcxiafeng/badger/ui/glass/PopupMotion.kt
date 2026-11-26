package top.mcxiafeng.badger.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal const val POPUP_SEGMENT_DURATION_MS = 239
private const val POPUP_REBOUND_TENSION = 1.15f

/** Back curves preserve a small rebound while both directions finish in exactly 239ms. */
internal val PopupEnterEasing = Easing { fraction ->
    when {
        fraction <= 0f -> 0f
        fraction >= 1f -> 1f
        else -> {
            val t = fraction - 1f
            1f + (POPUP_REBOUND_TENSION + 1f) * t * t * t + POPUP_REBOUND_TENSION * t * t
        }
    }
}
internal val PopupExitEasing = Easing { fraction ->
    when {
        fraction <= 0f -> 0f
        fraction >= 1f -> 1f
        else -> (POPUP_REBOUND_TENSION + 1f) * fraction * fraction * fraction - POPUP_REBOUND_TENSION * fraction * fraction
    }
}

internal fun popupMotionSpec(entering: Boolean): TweenSpec<Float> = tween(
    durationMillis = POPUP_SEGMENT_DURATION_MS,
    easing = if (entering) PopupEnterEasing else PopupExitEasing,
)

@Stable
internal class PopupMotionState {
    val progress = Animatable(0f)
    var visible by mutableStateOf(true)
        internal set
}
