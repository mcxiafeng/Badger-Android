package top.mcxiafeng.badger.ui

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion

private const val MILLIS_PER_SECOND = 1_000f
private const val MAX_MONOTONE_SLOPE = 3f

/** Carry forward velocity through a retarget without overshooting the new tab. */
internal fun navPositionEasing(distance: Float, velocity: Float): Easing {
    if (distance * velocity <= 0f) return FastOutSlowInEasing
    val slope = (velocity * BadgerMotion.DURATION_INDICATOR / MILLIS_PER_SECOND / distance)
        .coerceIn(0f, MAX_MONOTONE_SLOPE)
    return Easing { fraction ->
        val t = fraction.coerceIn(0f, 1f)
        val remaining = 1f - t
        t * t * (3f - 2f * t) + slope * t * remaining * remaining
    }
}
