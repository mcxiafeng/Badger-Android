package top.mcxiafeng.badger.ui.glass

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
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
private const val SPRING_RECOVERY_VISIBILITY = 0.001f
private val LocalItemPressOwner = staticCompositionLocalOf { false }

/** Keep nested action buttons at one visual scale while their compound item owns the feedback. */
@Composable
internal fun ItemPressFeedbackScope(enabled: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalItemPressOwner provides (enabled || LocalItemPressOwner.current), content = content)
}

/** Retain an existing spring's trajectory while fitting its recovery inside a timed keyframe leg. */
internal fun timedSpringRecoveryEasing(dampingRatio: Float, stiffness: Float): Easing {
    val recovery = spring<Float>(dampingRatio = dampingRatio, stiffness = stiffness,
        visibilityThreshold = SPRING_RECOVERY_VISIBILITY).vectorize(Float.VectorConverter)
    val start = AnimationVector1D(1f)
    val end = AnimationVector1D(0f)
    val velocity = AnimationVector1D(0f)
    val duration = recovery.getDurationNanos(start, end, velocity)
    return Easing { fraction ->
        if (fraction >= 1f) 1f else
            1f - recovery.getValueFromNanos((duration * fraction).toLong(), start, end, velocity).value
    }
}

/** Each leg lasts 239ms: press, release to the trough, then recovery from the trough. */
internal class TouchScaleFeedback(
    private val scope: CoroutineScope,
    private val pressedScale: Float = BadgerTouchFeedback.PRESSED_SCALE,
    private val releaseMinScale: Float = BadgerTouchFeedback.RELEASE_MIN_SCALE,
) {
    val scale = Animatable(1f)
    private var pressed = false
    private var animation: Job? = null

    fun press() {
        if (pressed) return
        pressed = true
        animation?.cancel()
        animation = scope.launch {
            Log.d(BUTTON_PRESS_LOG_TAG, "Touch scale to $pressedScale; segmentMs=${BadgerMotion.DURATION_TOUCH_FEEDBACK}")
            scale.animateTo(pressedScale, tween(BadgerMotion.DURATION_TOUCH_FEEDBACK, easing = FastOutSlowInEasing))
            Log.d(BUTTON_PRESS_LOG_TAG, "Touch scale settled=${scale.value}")
        }
    }

    fun release() {
        if (!pressed) return
        pressed = false
        animation?.cancel()
        animation = scope.launch {
            Log.d(BUTTON_PRESS_LOG_TAG, "Release scale from ${scale.value} through $releaseMinScale to 1.0; eachSegmentMs=${BadgerMotion.DURATION_TOUCH_FEEDBACK}")
            scale.animateTo(releaseMinScale, tween(BadgerMotion.DURATION_TOUCH_FEEDBACK, easing = FastOutSlowInEasing))
            Log.d(BUTTON_PRESS_LOG_TAG, "Release trough=${scale.value}; recovery segment starts")
            scale.animateTo(1f, tween(BadgerMotion.DURATION_TOUCH_FEEDBACK, easing = FastOutSlowInEasing))
            Log.d(BUTTON_PRESS_LOG_TAG, "Release scale settled=${scale.value}")
        }
    }
}

/** Native buttons retain click/focus semantics; this modifier only observes their touch. */
@Composable
internal fun Modifier.glassPressFeedback(
    enabled: Boolean, radius: Dp, feedback: ButtonTouchFeedback? = null,
    motionEnabled: Boolean = true, haloEnabled: Boolean = true,
): Modifier {
    val state = feedback ?: rememberButtonTouchFeedback(enabled) ?: return this
    if (!enabled) return this
    return pointerInput(state, motionEnabled) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            state.press(down.position)
            try {
                awaitButtonPressEnd(down.id) { state.move(it, BadgerTouchFeedback.dragFollowLimit.toPx()) }
            } finally {
                state.release()
            }
        }
    }.graphicsLayer {
        scaleX = if (motionEnabled) state.scale.scale.value else 1f
        scaleY = scaleX
        translationX = if (motionEnabled) state.follow.translation.value else 0f
    }.drawWithContent {
        drawContent()
        if (haloEnabled && state.glow.value > BadgerTouchFeedback.GLOW_VISIBLE_MIN) {
            val scale = if (motionEnabled) state.scale.scale.value else 1f
            val offset = if (motionEnabled) state.follow.translation.value else 0f
            val point = (state.touch - center - Offset(offset, 0f)) / scale + center
            drawButtonPressHalo(radius, state.glow.value, point)
        }
    }
}

@Composable
internal fun itemOwnsPressFeedback(): Boolean = LocalItemPressOwner.current

private suspend fun AwaitPointerEventScope.awaitButtonPressEnd(id: PointerId, move: (Offset) -> Unit) {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Final)
        val change = event.changes.firstOrNull { it.id == id } ?: return
        if (!change.pressed) return
        val point = change.position
        if (point.x < 0f || point.y < 0f || point.x > size.width || point.y > size.height ||
            (change.isConsumed && point != change.previousPosition)) return
        move(point)
    }
}

/** The complete interactive item brightens and deforms, including its background and children. */
@Composable
internal fun Modifier.itemPressFeedback(enabled: Boolean, radius: Dp, motionEnabled: Boolean = true): Modifier =
    pressFeedback(enabled && !LocalItemPressOwner.current, radius, uniform = true, motionEnabled = motionEnabled)

@Composable
private fun Modifier.pressFeedback(enabled: Boolean, radius: Dp, uniform: Boolean, motionEnabled: Boolean): Modifier {
    if (!enabled) return this
    val scope = rememberCoroutineScope()
    val feedback = remember(scope) { TouchScaleFeedback(scope) }
    val glow = remember { Animatable(0f) }
    val touch = remember { mutableStateOf(Offset.Unspecified) }
    LaunchedEffect(uniform, motionEnabled) {
        Log.d(BUTTON_PRESS_LOG_TAG, "Feedback owner; wholeItem=$uniform; motion=$motionEnabled; radiusDp=${radius.value}; eachSegmentMs=${BadgerMotion.DURATION_TOUCH_FEEDBACK}")
    }
    // Observe outside the scaling layer, so stationary fingers do not drift in local coordinates.
    return pointerInput(feedback, motionEnabled) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            touch.value = down.position
            if (motionEnabled) feedback.press()
            val light = scope.launch {
                glow.animateTo(1f, tween(BadgerMotion.DURATION_TOUCH_FEEDBACK, easing = FastOutSlowInEasing))
            }
            try {
                awaitPressEnd(down.id, down.position)
            } finally {
                light.cancel()
                if (motionEnabled) feedback.release()
                scope.launch { glow.animateTo(0f, tween(BadgerMotion.DURATION_TOUCH_FEEDBACK, easing = FastOutSlowInEasing)) }
            }
        }
    }.graphicsLayer {
        scaleX = if (motionEnabled) feedback.scale.value else 1f
        scaleY = scaleX
    }.drawWithContent {
        drawContent()
        val strength = glow.value
        if (strength > BadgerTouchFeedback.GLOW_VISIBLE_MIN) {
            if (uniform) drawItemPressHighlight(radius.toPx(), strength)
            else drawButtonPressHalo(radius, strength, touch.value)
        }
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

private fun DrawScope.drawButtonPressHalo(radius: Dp, strength: Float, touch: Offset) {
    if (touch == Offset.Unspecified) return
    val path = Path().apply { addG2RoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(radius.toPx()))) }
    clipPath(path) {
        drawRect(brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = strength * BadgerTouchFeedback.GLOW_ALPHA), Color.Transparent),
            center = touch, radius = size.height * BadgerTouchFeedback.GLOW_RADIUS_FACTOR,
        ), blendMode = BlendMode.Plus)
    }
}
