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

/** Observed pointer motion never participates in click recognition or consumes a pointer. */
internal class HorizontalTouchFollow(private val scope: CoroutineScope) {
    val translation = Animatable(0f)
    private var origin = 0f
    private var startOffset = 0f
    private var target = 0f
    private var job: Job? = null

    fun start(x: Float) {
        job?.cancel()
        target = translation.value
        origin = x
        startOffset = translation.value
    }

    fun move(x: Float, limit: Float) {
        val next = (startOffset + (x - origin) * BadgerTouchFeedback.DRAG_FOLLOW_FRACTION).coerceIn(-limit, limit)
        animateTo(next)
    }

    fun release() {
        Log.d("GlassButtonPressTester", "Horizontal follow released; returning to origin")
        animateTo(0f)
    }

    private fun animateTo(next: Float) {
        if (target == next && (job?.isActive == true || translation.value == next)) return
        target = next
        job?.cancel()
        job = scope.launch {
            translation.animateTo(next, tween(BadgerMotion.DURATION_FAST, easing = FastOutSlowInEasing))
        }
    }
}
