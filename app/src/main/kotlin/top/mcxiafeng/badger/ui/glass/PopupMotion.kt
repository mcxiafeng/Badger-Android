package top.mcxiafeng.badger.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal const val POPUP_SEGMENT_DURATION_MS = 239

/**
 * 单调三次贝塞尔：进场 easeOutQuint 快速到位再缓收，出场加速离开。
 * 不过冲、不回摆（过冲回摆=果冻感），全程限制在 0..1，两个方向都恰好在 239ms 完成。
 */
internal val PopupEnterEasing: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
internal val PopupExitEasing: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

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
