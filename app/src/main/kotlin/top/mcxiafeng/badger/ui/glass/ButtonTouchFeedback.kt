package top.mcxiafeng.badger.ui.glass

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.DelegatableNode
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

/** 按压反馈 = 轻微缩小 + 内容微暗；无光晕、无按钮级水平跟手（按钮不是可拖目标）。 */
internal class ButtonTouchFeedback(private val scope: CoroutineScope) {
    val scale = TouchScaleFeedback(scope)
    val contentAlpha = Animatable(1f)
    private var alphaJob: Job? = null

    fun press() {
        scale.press()
        animateContentAlpha(BadgerTouchFeedback.PRESSED_CONTENT_ALPHA)
        Log.d("GlassButtonPressTester", "Button press; contentAlpha=${BadgerTouchFeedback.PRESSED_CONTENT_ALPHA}; segmentMs=${BadgerMotion.DURATION_FAST}")
    }

    fun release() {
        scale.release()
        animateContentAlpha(1f)
    }

    private fun animateContentAlpha(alpha: Float) {
        alphaJob?.cancel()
        alphaJob = scope.launch {
            contentAlpha.animateTo(alpha, tween(BadgerMotion.DURATION_FAST, easing = FastOutSlowInEasing))
        }
    }
}

@Composable
internal fun rememberButtonTouchFeedback(enabled: Boolean): ButtonTouchFeedback? {
    val scope = rememberCoroutineScope()
    val feedback = remember(scope) { ButtonTouchFeedback(scope) }
    return if (enabled && !itemOwnsPressFeedback()) feedback else null
}

internal fun Modifier.buttonPressContentAlpha(feedback: ButtonTouchFeedback?): Modifier =
    if (feedback == null) this else graphicsLayer { alpha = feedback.contentAlpha.value }

/** The native clickable retains its interactions, focus, role and callback, without its press mask. */
@Composable
internal fun HaloOnlyIndicationScope(enabled: Boolean, content: @Composable () -> Unit) {
    if (enabled) CompositionLocalProvider(LocalIndication provides HaloOnlyIndication, content = content) else content()
}

private object HaloOnlyIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = javaClass.hashCode()
}
