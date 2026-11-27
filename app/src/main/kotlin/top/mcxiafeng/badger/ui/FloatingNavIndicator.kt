package top.mcxiafeng.badger.ui

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.designsystem.BadgerTouchFeedback
import top.mcxiafeng.badger.ui.designsystem.BadgerNavIndicator
import top.mcxiafeng.badger.ui.glass.HorizontalTouchFollow
import top.mcxiafeng.badger.ui.glass.TouchScaleFeedback
import top.mcxiafeng.badger.ui.navigation.MainTab
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

private const val TAG = "FloatingNavIndicatorTester"
internal const val DRAG_INDICATOR_SCALE = BadgerNavIndicator.PRESSED_SCALE
private const val HALF_TAB_SLOT = 0.5f
private const val NANOS_PER_SECOND = 1_000_000_000f
private const val MILLIS_PER_SECOND = 1_000f

internal enum class IndicatorDeformation { Rest, Pressed, Tapped }

internal fun bufferedIndicatorPosition(position: Float, target: Float, elapsedSeconds: Float): Float {
    val response = 1f - exp(-elapsedSeconds.coerceAtLeast(0f) * MILLIS_PER_SECOND / BadgerNavIndicator.POINTER_BUFFER_MS)
    val next = position + (target - position) * response
    return if (abs(target - next) <= BadgerNavIndicator.POINTER_SNAP_DISTANCE) target else next
}

internal fun indicatorBounds(
    size: Size, position: Float, direction: LayoutDirection, widthScale: Float = 1f, heightScale: Float = widthScale,
): RoundRect {
    val slotWidth = size.width / MainTab.entries.size
    val logicalSlot = position.coerceIn(0f, MainTab.entries.lastIndex.toFloat())
    val slot = if (direction == LayoutDirection.Ltr) logicalSlot else MainTab.entries.lastIndex - logicalSlot
    val width = (slotWidth * BadgerNavIndicator.IDLE_SCALE * widthScale).coerceAtMost(size.width)
    val height = size.height * BadgerNavIndicator.IDLE_SCALE * heightScale
    // Clamp the center independently of scale so neither endpoint shifts while resizing.
    val left = slotWidth * (slot + HALF_TAB_SLOT) - width / 2f
    val top = (size.height - height) / 2f
    return RoundRect(left, top, left + width, top + height, CornerRadius(minOf(width, height) / 2f))
}

/** Touches outside the track resolve to its first or last tab, in either layout direction. */
internal fun pointerTabPosition(x: Float, width: Float, direction: LayoutDirection): Float {
    if (width <= 0f) return 0f
    val physicalSlot = (x.coerceIn(0f, width) / width * MainTab.entries.size - HALF_TAB_SLOT)
        .coerceIn(0f, MainTab.entries.lastIndex.toFloat())
    return if (direction == LayoutDirection.Ltr) physicalSlot else MainTab.entries.lastIndex - physicalSlot
}

/**
 * 选中框指示器：32ms 缓冲跟手、239ms 单调位移（无过冲）、按压轻微缩小（0.97）。
 * 无速度形变、无到位惯性、无触点光晕（2026-11-27 动效重做）。
 */
internal class FloatingTabIndicator(
    initialPage: Int, private val scope: CoroutineScope, private val effectsEnabled: Boolean = true,
) {
    var position by mutableFloatStateOf(initialPage.toFloat())
        private set
    var dragPage by mutableStateOf<Int?>(null)
        private set
    val scale = Animatable(Offset(1f, 1f), Offset.VectorConverter)
    val opticalProgress = Animatable(0f)
    val barFeedback = TouchScaleFeedback(scope, BadgerTouchFeedback.NAV_PRESSED_SCALE)
    val barFollow = HorizontalTouchFollow(scope)
    private var surfacePressed = false
    private var motionActive = false
    private var requestedOptics = false
    private val hasTouch: Boolean get() = surfacePressed || pressedPage != null || dragPage != null
    private var pressedPage by mutableStateOf<Int?>(null)
    private var tapPage by mutableStateOf<Int?>(null)
    private var tapSequence = 0
    private var motionSequence = 0
    private var settleJob: Job? = null
    private var scaleJob: Job? = null
    private var opticsJob: Job? = null
    private var settleTarget: Int? = initialPage
    private var pointerTarget = initialPage.toFloat()
    private var approachingPointer = false
    private var followingPointer = false
    private var positionTarget = initialPage.toFloat()
    private var followAfterArrival = false
    private var releaseJob: Job? = null
    var velocity by mutableFloatStateOf(0f)
        private set

    val renderedScale: Offset get() = if (effectsEnabled) scale.value else Offset(1f, 1f)
    val fillAlpha: Float get() = BadgerNavIndicator.IDLE_FILL_ALPHA * (1f - opticalProgress.value)

    fun bounds(size: Size, direction: LayoutDirection): RoundRect =
        indicatorBounds(size, position, direction, renderedScale.x, renderedScale.y)

    /** The bar is held down: press the bar surface feedback and switch the fill to the glass state. */
    fun pressSurface() {
        surfacePressed = true
        barFeedback.press()
        animateOptics(true)
    }

    fun releaseSurface() {
        surfacePressed = false
        barFeedback.release()
        animateOptics(hasTouch || motionActive)
    }

    val deformation: IndicatorDeformation
        get() = when {
            dragPage != null -> IndicatorDeformation.Pressed
            pressedPage != null -> IndicatorDeformation.Pressed
            tapPage != null -> IndicatorDeformation.Tapped
            else -> IndicatorDeformation.Rest
        }
    val opticsActive: Boolean get() = opticalProgress.isRunning || opticalProgress.value > 0f

    fun press(page: Int, selectedPage: Int, pointerPosition: Float) {
        releaseJob?.cancel()
        tapSequence++
        tapPage = null
        settleTarget = null
        pressedPage = page
        pointerTarget = pointerPosition.coerceIn(0f, MainTab.entries.lastIndex.toFloat())
        animatePointerArrival()
        deformTo(deformation)
        Log.d(TAG, "Pointer down; page=$page; selected=$selectedPage; from=$position; absoluteTarget=$pointerTarget; smoothArrival=true; pressShrink=true; deformation=$deformation")
    }

    fun releasePress(page: Int, deferToClick: Boolean = false) {
        if (pressedPage == null) return
        pressedPage = null
        if (dragPage == null && tapPage == null) {
            deformTo(IndicatorDeformation.Rest)
            if (deferToClick) settleAfterClickDispatch(page) else settleTo(page)
        }
        Log.d(TAG, "Pointer released or cancelled; position=$position; deformation=$deformation; deferToClick=$deferToClick")
    }

    private fun settleAfterClickDispatch(page: Int) {
        val sequence = tapSequence
        releaseJob?.cancel()
        releaseJob = scope.launch {
            withFrameNanos { }
            if (sequence == tapSequence && !hasTouch && tapPage == null) {
                Log.d(TAG, "No click claimed release; settling to current page=$page")
                settleTo(page)
            }
        }
    }

    fun beginTap(page: Int): Int {
        releaseJob?.cancel()
        tapPage = page
        tapSequence++
        animateScale(1f)
        animateTo(page)
        Log.d(TAG, "Tap transition; page=$page; glassWhileMovingOrTouched=true; durationMs=${BadgerMotion.DURATION_INDICATOR}")
        return tapSequence
    }

    fun finishTap(sequence: Int, page: Int) {
        if (sequence != tapSequence) {
            Log.d(TAG, "Ignored superseded tap transition")
            return
        }
        tapPage = null
        Log.d(TAG, "Tap transition finished; page=$page")
        settleTo(page)
    }

    fun dragTo(pointerPosition: Float) {
        if (dragPage == null) {
            Log.d(TAG, "Drag started; targetScale=$DRAG_INDICATOR_SCALE; smoothArrival=$approachingPointer")
            tapSequence++
            tapPage = null
            if (!followingPointer) {
                motionSequence++
                settleJob?.cancel()
                approachingPointer = false
                finishMotion()
            }
            settleTarget = null
            deformTo(IndicatorDeformation.Pressed)
        }
        trackPointer(pointerPosition)
        dragPage = pointerTarget.roundToInt().coerceIn(MainTab.entries.indices)
    }

    fun trackPointer(pointerPosition: Float) {
        pointerTarget = pointerPosition.coerceIn(0f, MainTab.entries.lastIndex.toFloat())
        if (!approachingPointer) followPointer()
        if (dragPage != null) dragPage = pointerTarget.roundToInt().coerceIn(MainTab.entries.indices)
    }

    fun finishDrag(page: Int) {
        if (dragPage == null) return
        dragPage = null
        deformTo(IndicatorDeformation.Rest)
        settleTo(page)
        Log.d(TAG, "Drag finished or cancelled; shrinkOpticsAndPositionParallel=true; page=$page")
    }

    fun settleTo(page: Int) {
        if (dragPage != null || tapPage != null || pressedPage != null || settleTarget == page) return
        animateTo(page)
    }

    private fun deformTo(target: IndicatorDeformation) {
        animateScale(if (target == IndicatorDeformation.Pressed) DRAG_INDICATOR_SCALE else 1f)
        animateOptics(target != IndicatorDeformation.Rest || hasTouch || motionActive)
    }

    private fun animateScale(target: Float) {
        if (!effectsEnabled) return
        scaleJob?.cancel()
        scaleJob = scope.launch {
            scale.animateTo(Offset(target, target),
                tween(BadgerMotion.DURATION_INDICATOR, easing = FastOutSlowInEasing))
            Log.d(TAG, "Scale settled; width=${scale.value.x}; height=${scale.value.y}; pressedPage=$pressedPage; dragging=${dragPage != null}")
        }
    }

    private fun observeMotion() {
        motionActive = true
        animateOptics(true)
    }

    private fun finishMotion() {
        velocity = 0f
        motionActive = false
        animateOptics(hasTouch)
    }

    private fun animateOptics(active: Boolean) {
        if (!effectsEnabled) return
        val target = if (active) 1f else 0f
        if (requestedOptics == active && (opticsJob?.isActive == true || opticalProgress.value == target)) return
        requestedOptics = active
        opticsJob?.cancel()
        opticsJob = scope.launch {
            Log.d(TAG, "Glass transition; active=$active; motion=$motionActive; touch=$hasTouch; durationMs=${BadgerMotion.DURATION_INDICATOR_GLASS}")
            opticalProgress.animateTo(target, tween(BadgerMotion.DURATION_INDICATOR_GLASS, easing = FastOutSlowInEasing))
            Log.d(TAG, "Glass transition settled; opacity=${opticalProgress.value}; touch=$hasTouch; motion=$motionActive")
        }
    }

    private fun followPointer() {
        if (followingPointer || position == pointerTarget) return
        val sequence = ++motionSequence
        settleJob?.cancel()
        followingPointer = true
        settleJob = scope.launch {
            var previousFrame = withFrameNanos { it }
            Log.d(TAG, "Buffered follow started; responseMs=${BadgerNavIndicator.POINTER_BUFFER_MS}; from=$position; target=$pointerTarget")
            try {
                do {
                    withFrameNanos { frame ->
                        val elapsed = (frame - previousFrame) / NANOS_PER_SECOND
                        val previous = position
                        position = bufferedIndicatorPosition(previous, pointerTarget, elapsed)
                        velocity = if (elapsed > 0f) (position - previous) / elapsed else 0f
                        previousFrame = frame
                    }
                } while (position != pointerTarget)
            } finally {
                if (sequence == motionSequence) {
                    followingPointer = false
                    finishMotion()
                    Log.d(TAG, "Buffered follow finished; position=$position; velocity=0")
                }
            }
        }
        observeMotion()
    }

    private fun animatePointerArrival() {
        approachingPointer = true
        animatePosition(pointerTarget, followAfter = true)
    }

    private fun animateTo(page: Int) {
        settleTarget = page
        approachingPointer = false
        animatePosition(page.toFloat(), followAfter = false)
    }

    private fun animatePosition(target: Float, followAfter: Boolean) {
        followAfterArrival = followAfter
        if (!followingPointer && positionTarget == target && settleJob?.isActive == true) {
            Log.d(TAG, "Position target reused; target=$target; preserveTimeline=true")
            return
        }
        val sequence = ++motionSequence
        val from = position
        val startVelocity = velocity
        val easing = navPositionEasing(target - from, startVelocity)
        settleJob?.cancel()
        followingPointer = false
        positionTarget = target
        if (from == target) {
            approachingPointer = false
            finishMotion()
            return
        }
        settleJob = scope.launch {
            Log.d(TAG, "Position motion; target=$target; from=$from; initialVelocity=$startVelocity; singleOwner=true")
            try {
                animate(from, target, initialVelocity = startVelocity,
                    animationSpec = tween(BadgerMotion.DURATION_INDICATOR, easing = easing)) { value, speed ->
                    position = value
                    velocity = speed
                }
            } finally {
                if (sequence == motionSequence) finishPositionMotion(target)
            }
        }
        observeMotion()
    }

    private fun finishPositionMotion(target: Float) {
        approachingPointer = false
        finishMotion()
        if (followAfterArrival && (pressedPage != null || dragPage != null)) followPointer()
        Log.d(TAG, "Position settled; target=$target; effects=$effectsEnabled; durationMs=${BadgerMotion.DURATION_INDICATOR}")
    }
}
