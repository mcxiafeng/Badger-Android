package top.mcxiafeng.badger.ui.glass

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.designsystem.BadgerTouchFeedback
import top.mcxiafeng.badger.ui.designsystem.addG2RoundRect

private const val BUTTON_PRESS_LOG_TAG = "GlassButtonPressTester"
private const val ITEM_HIGHLIGHT_VISIBLE_MIN = 0.01f
private val LocalItemPressOwner = staticCompositionLocalOf { false }

/** Keep nested action buttons at one visual scale while their compound item owns the feedback. */
@Composable
internal fun ItemPressFeedbackScope(enabled: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalItemPressOwner provides (enabled || LocalItemPressOwner.current), content = content)
}

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

/** Native buttons retain click/focus semantics; this modifier only observes their touch. */
@Composable
internal fun Modifier.glassPressFeedback(
    enabled: Boolean, radius: Dp, feedback: ButtonTouchFeedback? = null,
    motionEnabled: Boolean = true,
): Modifier {
    val state = feedback ?: rememberButtonTouchFeedback(enabled) ?: return this
    if (!enabled) return this
    return pointerInput(state, motionEnabled) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            state.press()
            try {
                awaitButtonPressEnd(down.id)
            } finally {
                state.release()
            }
        }
    }.graphicsLayer {
        scaleX = if (motionEnabled) state.scale.scale.value else 1f
        scaleY = scaleX
    }
}

@Composable
internal fun itemOwnsPressFeedback(): Boolean = LocalItemPressOwner.current

private suspend fun AwaitPointerEventScope.awaitButtonPressEnd(id: PointerId) {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Final)
        val change = event.changes.firstOrNull { it.id == id } ?: return
        if (!change.pressed) return
        val point = change.position
        if (point.x < 0f || point.y < 0f || point.x > size.width || point.y > size.height ||
            (change.isConsumed && point != change.previousPosition)) return
    }
}

/** The complete interactive item brightens and shrinks, including its background and children. */
@Composable
internal fun Modifier.itemPressFeedback(enabled: Boolean, radius: Dp, motionEnabled: Boolean = true): Modifier =
    pressFeedback(enabled && !LocalItemPressOwner.current, radius, motionEnabled)

@Composable
private fun Modifier.pressFeedback(enabled: Boolean, radius: Dp, motionEnabled: Boolean): Modifier {
    if (!enabled) return this
    val scope = rememberCoroutineScope()
    val feedback = remember(scope) { TouchScaleFeedback(scope) }
    val highlight = remember { Animatable(0f) }
    LaunchedEffect(motionEnabled) {
        Log.d(BUTTON_PRESS_LOG_TAG, "Item press feedback; motion=$motionEnabled; radiusDp=${radius.value}; segmentMs=${BadgerMotion.DURATION_FAST}; highlightAlpha=${BadgerTouchFeedback.ITEM_HIGHLIGHT_ALPHA}")
    }
    // Observe outside the scaling layer, so stationary fingers do not drift in local coordinates.
    return pointerInput(feedback, motionEnabled) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (motionEnabled) feedback.press()
            val light = scope.launch {
                highlight.animateTo(1f, tween(BadgerMotion.DURATION_FAST, easing = FastOutSlowInEasing))
            }
            try {
                awaitPressEnd(down.id, down.position)
            } finally {
                light.cancel()
                if (motionEnabled) feedback.release()
                scope.launch { highlight.animateTo(0f, tween(BadgerMotion.DURATION_FAST, easing = FastOutSlowInEasing)) }
            }
        }
    }.graphicsLayer {
        scaleX = if (motionEnabled) feedback.scale.value else 1f
        scaleY = scaleX
    }.drawWithContent {
        drawContent()
        val strength = highlight.value
        if (strength > ITEM_HIGHLIGHT_VISIBLE_MIN) drawItemPressHighlight(radius.toPx(), strength)
    }
}

/** Child long-click consumption is not cancellation; a drag or a real pointer cancellation is. */
private suspend fun AwaitPointerEventScope.awaitPressEnd(id: PointerId, origin: Offset) {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == id } ?: return
        if (!change.pressed) return
        val point = change.position
        if ((point - origin).getDistance() > viewConfiguration.touchSlop ||
            point.x < 0f || point.y < 0f || point.x > size.width || point.y > size.height) return
    }
}

internal fun DrawScope.drawItemPressHighlight(radiusPx: Float, strength: Float) {
    val path = Path().apply { addG2RoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(radiusPx))) }
    drawPath(path, Color.White.copy(alpha = strength.coerceIn(0f, 1f) * BadgerTouchFeedback.ITEM_HIGHLIGHT_ALPHA))
}
