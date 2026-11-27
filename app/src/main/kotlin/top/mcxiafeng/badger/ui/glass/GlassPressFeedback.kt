package top.mcxiafeng.badger.ui.glass

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.designsystem.BadgerTouchFeedback

/** Press shrinks to [pressedScale] over 150ms; release returns to 1.0 in a single leg. */
internal class TouchScaleFeedback(
    private val scope: CoroutineScope,
    private val pressedScale: Float = BadgerTouchFeedback.PRESSED_SCALE,
) {
    val scale = Animatable(1f)
    private var pressed = false
    private var animation: Job? = null

    fun press() {
        if (pressed) return
        pressed = true
        animation?.cancel()
        animation = scope.launch {
            Log.d(BUTTON_PRESS_LOG_TAG, "Touch scale to $pressedScale; segmentMs=${BadgerMotion.DURATION_FAST}")
            scale.animateTo(pressedScale, tween(BadgerMotion.DURATION_FAST, easing = FastOutSlowInEasing))
            Log.d(BUTTON_PRESS_LOG_TAG, "Touch scale settled=${scale.value}")
        }
    }

    fun release() {
        if (!pressed) return
        pressed = false
        animation?.cancel()
        animation = scope.launch {
            Log.d(BUTTON_PRESS_LOG_TAG, "Release scale back to 1.0; segmentMs=${BadgerMotion.DURATION_FAST}")
            scale.animateTo(1f, tween(BadgerMotion.DURATION_FAST, easing = FastOutSlowInEasing))
            Log.d(BUTTON_PRESS_LOG_TAG, "Release scale settled=${scale.value}")
        }
    }
}

private const val BUTTON_PRESS_LOG_TAG = "GlassButtonPressTester"
