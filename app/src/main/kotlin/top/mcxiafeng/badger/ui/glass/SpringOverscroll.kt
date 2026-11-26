package top.mcxiafeng.badger.ui.glass

import android.util.Log
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val TAG = "SpringOverscrollTester"

/**
 * Generous headroom: the edge keeps taking movement long after the content would normally stop,
 * so the limit itself still responds. Travel per pixel drops as the cap is approached.
 */
private val MaxOverscroll = 160.dp

/** Share of a leftover pixel that becomes travel at rest; resistance eats the rest as it grows. */
private const val OVERSCROLL_DAMPING = 0.5f

/** Gentle settle: lightly damped so it reads as rubber without visibly bouncing backwards. */
private const val SPRING_DAMPING_RATIO = 0.86f
private const val SPRING_STIFFNESS = 260f
private const val MIN_SETTLE_PX = 0.5f
private const val FAST_SETTLE_MS = 160
private const val NANOS_PER_SECOND = 1_000_000_000f
private const val DEFAULT_SCROLL_SAMPLE_NANOS = 16_000_000L
private const val MIN_SCROLL_SAMPLE_NANOS = 8_000_000L
private const val FULL_IMPACT_SPEED_PER_RANGE = 18f
private const val MIN_IMPACT_GAIN = 0.4f
private const val MAX_IMPACT_GAIN = 1.4f

/**
 * Shares one rubber band across every scroll container on a page, so a page with a list *and* a
 * secondary scroller still bounces as a single surface.
 */
internal val LocalSpringOverscroll = staticCompositionLocalOf<SpringOverscrollState?> { null }

/**
 * Rubber-band feedback for a scrolling container.
 *
 * Deliberately a nested-scroll connection rather than an [androidx.compose.foundation.OverscrollEffect]:
 * scroll containers only dispatch overscroll effects while they can still scroll, so a page holding
 * a single short task (the exact case that felt dead) would never fire one. Unconsumed nested-scroll
 * delta always arrives here, whether the list is short, empty, or already resting at an edge.
 *
 * Apply this to the scroll container itself, never to a container that also holds pinned chrome —
 * the translation is on this node, so anything sharing its layer moves with it.
 */
@Composable
internal fun Modifier.springOverscroll(enabled: Boolean = true): Modifier {
    if (!enabled) return this
    val shared = LocalSpringOverscroll.current
    val maxOffsetPx = with(LocalDensity.current) { MaxOverscroll.toPx() }
    val scope = rememberCoroutineScope()
    val own = remember(shared, maxOffsetPx) { shared ?: SpringOverscrollState(maxOffsetPx, scope) }
    LaunchedEffect(own) {
        Log.d(TAG, "Spring overscroll attached; maxOffsetDp=${MaxOverscroll.value}; shared=${shared != null}")
    }
    return this.nestedScroll(own.connection()).graphicsLayer { translationY = own.offset }
}

/** Provides one rubber band to every scroller rendered below it. */
@Composable
internal fun ProvideSpringOverscroll(
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    val maxOffsetPx = with(LocalDensity.current) { MaxOverscroll.toPx() }
    val scope = rememberCoroutineScope()
    val state = remember(maxOffsetPx) { SpringOverscrollState(maxOffsetPx, scope) }
    androidx.compose.runtime.CompositionLocalProvider(LocalSpringOverscroll provides state, content = content)
}

internal class SpringOverscrollState(
    private val maxOffsetPx: Float,
    private val scope: CoroutineScope,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    var offset by mutableFloatStateOf(0f)
        private set
    private var settleJob: Job? = null
    private var lastScrollNanos: Long? = null
    private var hasScrolledContent = false
    private var arrivalSpeed = 0f
    private var impactLogged = false
    private val connection = SpringOverscrollConnection(this)

    fun connection(): NestedScrollConnection = connection

    /** Content consumption distinguishes arriving at an edge from pulling an already resting edge. */
    fun onScroll(consumedY: Float, availableY: Float) {
        if (consumedY == 0f && availableY == 0f) return
        val now = nanoTime()
        if (consumedY != 0f) {
            val elapsed = lastScrollNanos?.let { now - it } ?: DEFAULT_SCROLL_SAMPLE_NANOS
            arrivalSpeed = abs(consumedY + availableY) * NANOS_PER_SECOND /
                elapsed.coerceAtLeast(MIN_SCROLL_SAMPLE_NANOS)
            hasScrolledContent = true
        }
        lastScrollNanos = now
        val strength = (arrivalSpeed / (maxOffsetPx * FULL_IMPACT_SPEED_PER_RANGE)).coerceIn(0f, 1f)
        val gain = if (hasScrolledContent) MIN_IMPACT_GAIN + (MAX_IMPACT_GAIN - MIN_IMPACT_GAIN) * strength else 1f
        if (hasScrolledContent && availableY != 0f && !impactLogged) {
            impactLogged = true
            Log.d(TAG, "Edge arrival; speedPxPerSecond=$arrivalSpeed; impactGain=$gain; capPx=$maxOffsetPx")
        }
        stretch(availableY, gain)
    }

    fun finishScroll() {
        lastScrollNanos = null
        hasScrolledContent = false
        arrivalSpeed = 0f
        impactLogged = false
        settle()
    }

    /** Pulling back releases the visible band first, so it stays attached to the finger. */
    fun unwind(availableY: Float): Float {
        val current = offset
        if (current == 0f || availableY == 0f || availableY * current > 0f) return 0f
        // Only the travel already on screen may be released; the rest must reach the list.
        val next = current + availableY
        val clamped = if (next * current < 0f) 0f else next
        val consumed = clamped - current
        settleJob?.cancel()
        offset = clamped
        return consumed
    }

    /** Leftover delta becomes travel; each further pixel moves the content less than the last. */
    fun stretch(availableY: Float) = stretch(availableY, impactGain = 1f)

    private fun stretch(availableY: Float, impactGain: Float) {
        val current = offset
        // Same direction adds travel; the opposite direction is [unwind]'s job.
        if (availableY == 0f || (current != 0f && availableY * current < 0f)) return
        // Quadratic falloff keeps the edge alive at the cap while making late pixels nearly free.
        val progress = (abs(current) / maxOffsetPx).coerceIn(0f, 1f)
        val resistance = (1f - progress) * (1f - progress)
        val next = (current + availableY * OVERSCROLL_DAMPING * impactGain * resistance).coerceIn(-maxOffsetPx, maxOffsetPx)
        if (next == current) return
        settleJob?.cancel()
        offset = next
    }

    /** Release settles back without a visible rebound. */
    fun settle() {
        val from = offset
        if (from == 0f) return
        settleJob?.cancel()
        settleJob = scope.launch {
            Log.d(TAG, "Settling from ${from}px")
            val spec = if (abs(from) < MIN_SETTLE_PX) {
                tween<Float>(FAST_SETTLE_MS, easing = FastOutSlowInEasing)
            } else {
                spring(dampingRatio = SPRING_DAMPING_RATIO, stiffness = SPRING_STIFFNESS,
                    visibilityThreshold = MIN_SETTLE_PX)
            }
            animate(from, 0f, animationSpec = spec) { value, _ -> offset = value }
            offset = 0f
        }
    }

    private class SpringOverscrollConnection(
        private val state: SpringOverscrollState,
    ) : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            Offset(0f, state.unwind(available.y))

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            state.onScroll(consumed.y, available.y)
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            state.finishScroll()
            return Velocity.Zero
        }
    }
}
