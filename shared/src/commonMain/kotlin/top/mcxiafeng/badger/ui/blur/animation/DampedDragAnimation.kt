package top.mcxiafeng.badger.ui.blur.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastFirstOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.TimeSource

class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val canDrag: (Offset) -> Boolean = { true },
    val onDragStarted: DampedDragAnimation.(position: Offset, size: IntSize) -> Unit = { _, _ -> },
    val onDragStopped: DampedDragAnimation.() -> Unit = {},
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
    val onSettled: DampedDragAnimation.(settledIndex: Int) -> Unit = {},
) {

    

    private val valueAnimationSpec = spring(1f, 1000f, visibilityThreshold)
    
    
    private val velocityAnimationSpec = spring(1f, 300f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec = spring(1f, 1000f, 0.001f)
    
    private val scaleXAnimationSpec = spring(1f, 250f, 0.001f)
    private val scaleYAnimationSpec = spring(1f, 250f, 0.001f)

    
    

    private val valueAnimation = Animatable(
        initialValue.coerceIn(valueRange), visibilityThreshold
    )
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)

    private val mutatorMutex = MutatorMutex()

    private var pressJob: Job? = null
    private var releaseJob: Job? = null

    private val velocityTracker = VelocityTracker()
    private val startMark = TimeSource.Monotonic.markNow()

    private fun nowMillis(): Long = startMark.elapsedNow().inWholeMilliseconds

    

    

    val value: Float get() = valueAnimation.value

    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    
    var isDragging: Boolean by mutableStateOf(false)
        private set

    

    val stableIndex: Int? by derivedStateOf {
        if (isDragging) return@derivedStateOf null
        val settled = abs(value - targetValue) < visibilityThreshold && abs(velocity) < 0.05f
        if (settled) {
            value.roundToInt()
                .coerceIn(valueRange.start.roundToInt(), valueRange.endInclusive.roundToInt())
        } else {
            null
        }
    }

    

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!canDrag(down.position)) return@awaitEachGesture

            val pointerId = down.id
            val downPos = down.position
            var prevPos = downPos
            var dragStarted = false

            
            press()
            onDragStarted(downPos, size)

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.fastFirstOrNull { it.id == pointerId } ?: break

                if (change.changedToUpIgnoreConsumed() || !change.pressed) {
                    if (dragStarted) change.consume()
                    break
                }

                if (change.isConsumed) {
                    
                    break
                }

                if (!dragStarted) {
                    if (abs(change.position.x - downPos.x) > slop && canDrag(change.position)) {
                        dragStarted = true
                        change.consume()
                    }
                } else if (canDrag(change.position)) {
                    change.consume()
                    onDrag(size, change.position - prevPos)
                }
                prevPos = change.position
            }

            
            onDragStopped()
            release()
        }
    }

    

    fun press() {
        isDragging = true
        releaseJob?.cancel()
        pressJob?.cancel()
        velocityTracker.resetTracking()
        
        velocityTracker.addPosition(nowMillis(), Offset(value, 0f))
        pressJob = animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    fun release() {
        isDragging = false
        releaseJob?.cancel()
        
        
        
        pressJob?.cancel()
        pressJob = animationScope.launch {
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
        
        releaseJob = animationScope.launch {
            withFrameMillis { }
            
            
            
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .first { abs(it - valueAnimation.targetValue) < threshold }
                val settledIndex = targetValue.roundToInt()
                    .coerceIn(valueRange.start.roundToInt(), valueRange.endInclusive.roundToInt())
                onSettled(settledIndex)
            }
        }
    }

    

    

    fun updateValue(value: Float) {
        val clamped = value.coerceIn(valueRange)
        animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            valueAnimation.snapTo(clamped)
        }
        updateVelocity(clamped)
    }

    

    fun animateToValue(value: Float) {
        val target = value.coerceIn(valueRange)
        val currentVisualValue = this.value
        val currentVelocity = this.velocity
        animationScope.launch {
            mutatorMutex.mutate {
                valueAnimation.snapTo(currentVisualValue)
                velocityAnimation.snapTo(currentVelocity)
            }
            mutatorMutex.mutate {
                isDragging = false
                launch { valueAnimation.animateTo(target, valueAnimationSpec) }
                launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
            }
            
            pressJob?.cancel()
            pressJob = animationScope.launch {
                launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
                launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
            }
            
            releaseJob?.cancel()
            releaseJob = animationScope.launch {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .first { abs(it - target) < threshold }
                val settledIndex = target.roundToInt()
                    .coerceIn(valueRange.start.roundToInt(), valueRange.endInclusive.roundToInt())
                onSettled(settledIndex)
            }
        }
    }

    
    fun snapToValue(value: Float) {
        isDragging = false
        releaseJob?.cancel()
        pressJob?.cancel()
        val clamped = value.coerceIn(valueRange)
        animationScope.launch {
            mutatorMutex.mutate {
                valueAnimation.snapTo(clamped)
                velocityAnimation.snapTo(0f)
                pressProgressAnimation.snapTo(0f)
                scaleXAnimation.snapTo(initialScale)
                scaleYAnimation.snapTo(initialScale)
            }
            val settledIndex = clamped.roundToInt()
                .coerceIn(valueRange.start.roundToInt(), valueRange.endInclusive.roundToInt())
            onSettled(settledIndex)
        }
    }

    private fun updateVelocity(currentValue: Float) {
        velocityTracker.addPosition(nowMillis(), Offset(currentValue, 0f))
        val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1e-6f)
        val targetVelocity = velocityTracker.calculateVelocity().x / span
        animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            velocityAnimation.snapTo(targetVelocity)
        }
    }
}