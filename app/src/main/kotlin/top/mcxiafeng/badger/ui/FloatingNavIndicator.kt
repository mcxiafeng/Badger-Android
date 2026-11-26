package top.mcxiafeng.badger.ui

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.designsystem.BadgerTouchFeedback
import top.mcxiafeng.badger.ui.designsystem.BadgerNavIndicator
import top.mcxiafeng.badger.ui.designsystem.addG2RoundRect
import top.mcxiafeng.badger.ui.glass.GlassBackdrop
import top.mcxiafeng.badger.ui.glass.GlassOpticalParameters
import top.mcxiafeng.badger.ui.glass.HorizontalTouchFollow
import top.mcxiafeng.badger.ui.glass.LocalGlassStyle
import top.mcxiafeng.badger.ui.glass.TouchScaleFeedback
import top.mcxiafeng.badger.ui.glass.timedSpringRecoveryEasing
import top.mcxiafeng.badger.ui.glass.glassSurface
import top.mcxiafeng.badger.ui.navigation.MainTab
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

private const val TAG = "FloatingNavIndicatorTester"
internal const val DRAG_INDICATOR_SCALE = BadgerNavIndicator.PRESSED_SCALE
private const val INDICATOR_EDGE_DEPTH = 60f

/** The capsule keeps a little edge refraction but only a quarter of the default colour fringing. */
private const val INDICATOR_DISPERSION = 18f
private val IndicatorOptics = GlassOpticalParameters(depth = INDICATOR_EDGE_DEPTH, dispersion = INDICATOR_DISPERSION)
private const val HALF_TAB_SLOT = 0.5f
private const val NANOS_PER_SECOND = 1_000_000_000f
private const val MILLIS_PER_SECOND = 1_000f
private const val GLOW_IN_MS = 120
private const val GLOW_OUT_MS = 240
private const val INERTIA_RECOVERY_DAMPING = 0.55f
private const val INERTIA_RECOVERY_STIFFNESS = 300f
private val InertiaRecoveryEasing = timedSpringRecoveryEasing(INERTIA_RECOVERY_DAMPING, INERTIA_RECOVERY_STIFFNESS)
private val IndicatorRadius = 64.dp

internal enum class IndicatorDeformation { Rest, Pressed, Tapped }

/** Uniform press enlargement and velocity deformation are independent and compose multiplicatively. */
internal fun indicatorMotionScale(base: Offset, slotsPerSecond: Float): Offset {
    val strength = (abs(slotsPerSecond) / BadgerNavIndicator.FULL_DEFORMATION_SLOTS_PER_SECOND).coerceIn(0f, 1f)
    return Offset(base.x * (1f + BadgerNavIndicator.MAX_WIDTH_STRETCH * strength),
        base.y * (1f - BadgerNavIndicator.MAX_HEIGHT_SQUASH * strength))
}

/** How strongly a settle should deform the capsule, from its peak arrival speed in slots/second. */
internal fun settleInertiaStrength(slotsPerSecond: Float): Float {
    val span = BadgerNavIndicator.INERTIA_FULL_SLOTS_PER_SECOND - BadgerNavIndicator.INERTIA_MIN_SLOTS_PER_SECOND
    return ((slotsPerSecond - BadgerNavIndicator.INERTIA_MIN_SLOTS_PER_SECOND) / span).coerceIn(0f, 1f)
}

internal fun bufferedIndicatorPosition(position: Float, target: Float, elapsedSeconds: Float): Float {
    val response = 1f - exp(-elapsedSeconds.coerceAtLeast(0f) * MILLIS_PER_SECOND / BadgerNavIndicator.POINTER_BUFFER_MS)
    val next = position + (target - position) * response
    return if (abs(target - next) <= BadgerNavIndicator.POINTER_SNAP_DISTANCE) target else next
}

@Composable
internal fun Modifier.slidingTabIndicator(indicator: FloatingTabIndicator, backdrop: GlassBackdrop?): Modifier {
    val style = LocalGlassStyle.current
    val glass = style.opticsEnabled
    val direction = LocalLayoutDirection.current
    val color = Color.Black
    val activeColor = color.copy(alpha = BadgerNavIndicator.ACTIVE_FILL_ALPHA)
    val underlay = MiuixTheme.colorScheme.surface
    val optics = indicator.opticalProgress
    val idle = Modifier.drawWithCache {
        val path = Path()
        val stroke = Stroke(BadgerNavIndicator.solidOutlineWidth.toPx())
        val outlined = style.enabled && style.floatingNavigation && !style.materialEnabled
        onDrawBehind {
            path.rewind()
            path.addG2RoundRect(indicator.bounds(size, direction))
            drawPath(path, color.copy(alpha = if (style.materialEnabled) indicator.fillAlpha else BadgerNavIndicator.IDLE_FILL_ALPHA))
            if (outlined && optics.value > 0f) drawPath(path, Color.White.copy(alpha = optics.value), style = stroke)
        }
    }
    val lens = if (glass) Modifier.glassSurface(
        radius = IndicatorRadius, tint = activeColor, underlay = underlay, backdrop = backdrop, frosted = false,
        opacity = { optics.value }, edgeGlow = { optics.value },
        opticalParameters = { IndicatorOptics.faded(optics.value) },
        // Read the live capsule bounds inside the draw phase; querying them during composition
        // forced a full recomposition on every frame of the tab-switch animation.
        bounds = { indicator.bounds(size, direction) },
    ) else Modifier
    return then(idle).then(lens)
}

/** Repaint only tab content under the same moving capsule used by the fill and optical layer. */
@Composable
internal fun Modifier.indicatorContentTint(indicator: FloatingTabIndicator): Modifier {
    val direction = LocalLayoutDirection.current
    val color = MiuixTheme.colorScheme.primary
    return drawWithCache {
        val path = Path()
        val paint = Paint().apply { colorFilter = ColorFilter.tint(color) }
        onDrawWithContent {
            drawContent()
            path.rewind()
            path.addG2RoundRect(indicator.bounds(size, direction))
            clipPath(path) {
                val canvas = drawContext.canvas
                canvas.saveLayer(Rect(Offset.Zero, size), paint)
                try { this@onDrawWithContent.drawContent() } finally { canvas.restore() }
            }
        }
    }
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

internal class FloatingTabIndicator(initialPage: Int, private val scope: CoroutineScope) {
    var position by mutableFloatStateOf(initialPage.toFloat())
        private set
    var dragPage by mutableStateOf<Int?>(null)
        private set
    val scale = Animatable(Offset(1f, 1f), Offset.VectorConverter)
    val opticalProgress = Animatable(0f)
    val barFeedback = TouchScaleFeedback(scope, BadgerTouchFeedback.NAV_PRESSED_SCALE, BadgerTouchFeedback.NAV_RELEASE_MIN_SCALE)
    val barFollow = HorizontalTouchFollow(scope)
    private var surfacePressed = false
    private var motionActive = false
    private var requestedOptics = false
    private val hasTouch: Boolean get() = surfacePressed || pressedPage != null || dragPage != null

    /** Touch-point halo shown on the bar itself: above the icons, below the selection capsule. */
    val touchGlow = Animatable(0f)
    var glowPoint by mutableStateOf(Offset.Unspecified)
        private set
    private var glowJob: Job? = null

    /** 0 → 1 → 0 pulse that narrows and lifts the capsule once it arrives, then springs back. */
    private val settlePulse = Animatable(0f)
    private var settleInertiaStrength = 0f
    private var inertiaJob: Job? = null
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
    private var motionJob: Job? = null
    private var frameIntervalSeconds = 0f
    var velocity by mutableFloatStateOf(0f)
        private set

    val renderedScale: Offset get() = indicatorMotionScale(scale.value, velocity)
    val fillAlpha: Float get() = BadgerNavIndicator.IDLE_FILL_ALPHA * (1f - opticalProgress.value)

    fun bounds(size: Size, direction: LayoutDirection): RoundRect {
        val rendered = renderedScale
        val inertia = settlePulse.value * settleInertiaStrength
        return indicatorBounds(
            size, position, direction,
            rendered.x * (1f - BadgerNavIndicator.INERTIA_WIDTH_SHRINK * inertia),
            rendered.y * (1f + BadgerNavIndicator.INERTIA_HEIGHT_GROWTH * inertia),
        )
    }

    /** Brighten a soft halo at [point] while the bar is held down. */
    fun glowAt(point: Offset) {
        surfacePressed = true
        barFeedback.press()
        animateOptics(true)
        glowPoint = point
        if (glowJob?.isActive == true && touchGlow.targetValue == 1f) return
        glowJob?.cancel()
        glowJob = scope.launch { touchGlow.animateTo(1f, tween(GLOW_IN_MS, easing = FastOutSlowInEasing)) }
    }

    fun moveGlow(point: Offset) {
        glowPoint = point
    }

    fun glowOff() {
        surfacePressed = false
        barFeedback.release()
        animateOptics(hasTouch || motionActive)
        if (!touchGlow.isRunning && touchGlow.value == 0f) return
        glowJob?.cancel()
        glowJob = scope.launch { touchGlow.animateTo(0f, tween(GLOW_OUT_MS, easing = FastOutSlowInEasing)) }
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
        tapSequence++
        tapPage = null
        settleTarget = null
        stopSettleInertia()
        pressedPage = page
        pointerTarget = pointerPosition.coerceIn(0f, MainTab.entries.lastIndex.toFloat())
        animatePointerArrival()
        deformTo(deformation)
        Log.d(TAG, "Pointer down; page=$page; selected=$selectedPage; from=$position; absoluteTarget=$pointerTarget; smoothArrival=true; enlargeOnAnyTab=true; deformation=$deformation")
    }

    fun releasePress(page: Int) {
        if (pressedPage == null) return
        pressedPage = null
        if (dragPage == null && tapPage == null) {
            deformTo(IndicatorDeformation.Rest)
            settleTo(page)
        }
        Log.d(TAG, "Pointer released or cancelled; position=$position; deformation=$deformation")
    }

    fun beginTap(page: Int): Int {
        tapPage = page
        tapSequence++
        stopSettleInertia()
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
            stopSettleInertia()
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
        if (motionJob?.isActive == true) return
        val startPosition = position
        motionJob = scope.launch {
            var lastPosition = startPosition
            var lastFrame: Long? = null
            var peakSpeed = 0f
            Log.d(TAG, "Velocity tracking started; stretchMax=20%; squashMax=25%; fullSpeed=${BadgerNavIndicator.FULL_DEFORMATION_SLOTS_PER_SECOND}")
            try {
                do {
                    withFrameNanos { frame ->
                        val elapsed = lastFrame?.let { (frame - it) / NANOS_PER_SECOND } ?: frameIntervalSeconds
                        if (elapsed > 0f) frameIntervalSeconds = elapsed
                        velocity = if (elapsed > 0f) (position - lastPosition) / elapsed else 0f
                        peakSpeed = maxOf(peakSpeed, abs(velocity))
                        lastPosition = position
                        lastFrame = frame
                    }
                } while (settleJob?.isActive == true || velocity != 0f)
            } finally {
                velocity = 0f
                Log.d(TAG, "Velocity tracking finished; peakSlotsPerSecond=$peakSpeed; deformation=0; position=$position")
            }
        }
    }

    private fun finishMotion() {
        motionJob?.cancel()
        motionJob = null
        velocity = 0f
        motionActive = false
        animateOptics(hasTouch)
    }

    private fun animateOptics(active: Boolean) {
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
                        position = bufferedIndicatorPosition(position, pointerTarget, (frame - previousFrame) / NANOS_PER_SECOND)
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
        val sequence = ++motionSequence
        settleJob?.cancel()
        followingPointer = false
        val target = pointerTarget
        approachingPointer = true
        settleJob = scope.launch {
            try {
                animate(position, target, animationSpec = tween(BadgerMotion.DURATION_INDICATOR, easing = FastOutSlowInEasing)) { value, _ ->
                    position = value
                }
            } finally {
                if (sequence == motionSequence) {
                    approachingPointer = false
                    finishMotion()
                    Log.d(TAG, "Pointer arrival finished; position=$position; absoluteTarget=$pointerTarget; velocity=0")
                    if (pressedPage != null || dragPage != null) followPointer()
                }
            }
        }
        observeMotion()
    }

    private fun animateTo(page: Int) {
        settleTarget = page
        val sequence = ++motionSequence
        settleJob?.cancel()
        approachingPointer = false
        followingPointer = false
        val target = page.toFloat()
        if (position == target) {
            finishMotion()
            return
        }
        settleJob = scope.launch {
            var peakSpeed = 0f
            animate(position, target,
                animationSpec = tween(BadgerMotion.DURATION_INDICATOR, easing = FastOutSlowInEasing)) { value, speed ->
                position = value
                peakSpeed = maxOf(peakSpeed, abs(speed))
            }
            if (sequence == motionSequence) {
                finishMotion()
                val strength = settleInertiaStrength(peakSpeed)
                if (strength > 0f) playSettleInertia(strength)
                Log.d(TAG, "Position settled; page=$page; peakSlotsPerSecond=$peakSpeed; inertia=$strength; durationMs=${BadgerMotion.DURATION_INDICATOR}")
                // Resting without a touch returns to the flat idle selection in 100ms.
                animateOptics(hasTouch)
            }
        }
        observeMotion()
    }

    /** Narrow the capsule and let it grow taller as the arriving speed bleeds off, then recover. */
    private fun playSettleInertia(strength: Float) {
        inertiaJob?.cancel()
        settleInertiaStrength = strength
        inertiaJob = scope.launch {
            settlePulse.snapTo(0f)
            settlePulse.animateTo(0f, keyframes {
                durationMillis = BadgerMotion.DURATION_INDICATOR
                0f at 0 using FastOutSlowInEasing
                1f at BadgerNavIndicator.INERTIA_PEAK_MS using InertiaRecoveryEasing
                0f at BadgerMotion.DURATION_INDICATOR
            })
            settlePulse.snapTo(0f)
            settleInertiaStrength = 0f
            Log.d(TAG, "Settle inertia finished; strength=$strength; widthShrink=${BadgerNavIndicator.INERTIA_WIDTH_SHRINK * strength}; heightGrowth=${BadgerNavIndicator.INERTIA_HEIGHT_GROWTH * strength}")
        }
    }

    /** A new touch releases any in-flight inertia pulse smoothly instead of snapping the shape. */
    private fun stopSettleInertia() {
        if (settleInertiaStrength == 0f && !settlePulse.isRunning && settlePulse.value == 0f) return
        inertiaJob?.cancel()
        inertiaJob = scope.launch {
            settlePulse.animateTo(0f, tween(BadgerMotion.DURATION_INDICATOR, easing = FastOutSlowInEasing))
            settleInertiaStrength = 0f
        }
    }
}
