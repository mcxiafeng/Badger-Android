package top.mcxiafeng.badger.ui.navigation

import android.app.Application
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.ui.FloatingTabIndicator
import top.mcxiafeng.badger.ui.indicatorBounds
import top.mcxiafeng.badger.ui.indicatorMotionScale
import top.mcxiafeng.badger.ui.navTrackInsetPx
import top.mcxiafeng.badger.ui.settleInertiaStrength
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.designsystem.BadgerNavIndicator
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import androidx.compose.ui.graphics.Color
import top.mcxiafeng.badger.ui.navBarTint
import top.mcxiafeng.badger.ui.glass.GlassStyle
import top.mcxiafeng.badger.ui.glass.GlassGlowOutset
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val VELOCITY_SETTLE_MS = BadgerMotion.DURATION_INDICATOR + BadgerMotion.DURATION_INDICATOR_GLASS + 64L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class FloatingNavVelocityTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun stationaryPressOnlyEnlargesAndHoldingOtherTabDoesNotKeepItSquashed() {
        val indicator = mountIndicator()
        compose.runOnIdle { indicator.press(0, 0, 0f) }
        compose.mainClock.advanceTimeBy(VELOCITY_SETTLE_MS)
        compose.runOnIdle {
            assertThat(indicator.velocity).isEqualTo(0f)
            assertThat(indicator.renderedScale).isEqualTo(Offset(1.50f, 1.50f))
            indicator.releasePress(0)
            indicator.press(3, 0, 3f)
        }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle {
            assertThat(indicator.renderedScale.x).isGreaterThan(indicator.scale.value.x)
            assertThat(indicator.renderedScale.y).isLessThan(indicator.scale.value.y)
        }
        compose.mainClock.advanceTimeBy(VELOCITY_SETTLE_MS)
        compose.runOnIdle {
            assertThat(indicator.position).isEqualTo(3f)
            assertThat(indicator.velocity).isEqualTo(0f)
            assertThat(indicator.renderedScale).isEqualTo(Offset(1.50f, 1.50f))
            assertThat(indicator.opticalProgress.value).isEqualTo(1f)
            assertThat(indicator.fillAlpha).isEqualTo(0f)
            indicator.releasePress(0)
        }
        compose.mainClock.advanceTimeBy(VELOCITY_SETTLE_MS)
        assertRest(indicator, 0f)
    }

    @Test
    fun movingDragDeformsTheEnlargedCapsuleAndRestoresAfterBufferedMotionSettles() {
        val indicator = mountIndicator()
        compose.runOnIdle {
            indicator.press(0, 0, 0f)
            indicator.dragTo(0f)
        }
        compose.mainClock.advanceTimeBy(VELOCITY_SETTLE_MS)
        moveAndSample(indicator, 0.04f)
        var slowWidth = 0f
        compose.runOnIdle {
            slowWidth = indicator.renderedScale.x
            assertThat(slowWidth).isGreaterThan(1.50f)
            assertThat(indicator.renderedScale.y).isLessThan(1.50f)
        }
        moveAndSample(indicator, 1.40f)
        compose.runOnIdle {
            assertThat(indicator.renderedScale.x).isGreaterThan(slowWidth)
            assertThat(indicator.renderedScale.x).isWithin(0.0001f).of(1.80f)
            assertThat(indicator.renderedScale.y).isWithin(0.0001f).of(1.125f)
        }
        compose.mainClock.advanceTimeBy(320)
        compose.runOnIdle {
            assertThat(indicator.velocity).isEqualTo(0f)
            assertThat(indicator.renderedScale).isEqualTo(Offset(1.50f, 1.50f))
            assertThat(indicator.dragPage).isNotNull()
            indicator.releasePress(0)
            indicator.finishDrag(0)
        }
        compose.mainClock.advanceTimeBy(VELOCITY_SETTLE_MS)
        assertRest(indicator, 0f)
    }

    @Test
    fun accessibilityTapDeformsByTravelSpeedAndEndsWithinTheOriginalDuration() {
        val indicator = mountIndicator()
        var sequence = 0
        compose.runOnIdle { sequence = indicator.beginTap(3) }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle {
            assertThat(indicator.position).isGreaterThan(0f)
            assertThat(indicator.position).isLessThan(3f)
            assertThat(indicator.renderedScale.x).isGreaterThan(1f)
            assertThat(indicator.renderedScale.x).isAtMost(1.20f)
            assertThat(indicator.renderedScale.y).isLessThan(1f)
            assertThat(indicator.renderedScale.y).isAtLeast(0.75f)
        }
        compose.mainClock.advanceTimeBy(VELOCITY_SETTLE_MS)
        compose.runOnIdle { indicator.finishTap(sequence, 3) }
        assertRest(indicator, 3f)
    }

    @Test
    fun speedDeformationIsSymmetricBoundedAndSeparateFromUniformEnlargement() {
        listOf(Offset(1f, 1f), Offset(1.50f, 1.50f)).forEach { base ->
            assertThat(indicatorMotionScale(base, 0f)).isEqualTo(base)
            val slow = indicatorMotionScale(base, 2f)
            val fast = indicatorMotionScale(base, 8f)
            assertThat(fast.x).isGreaterThan(slow.x)
            assertThat(fast.y).isLessThan(slow.y)
            assertThat(indicatorMotionScale(base, -8f)).isEqualTo(fast)
            val capped = indicatorMotionScale(base, 100f)
            assertThat(capped.x / base.x).isWithin(0.0001f).of(1.20f)
            assertThat(capped.y / base.y).isWithin(0.0001f).of(0.75f)
        }
    }

    @Test
    fun idleBoundsShrinkNinePercentAndAllRenderedStatesKeepTheSameEndpointCenters() {
        val track = Size(400f, 100f)
        LayoutDirection.entries.forEach { direction ->
            listOf(0f, 3f).forEach { page ->
                val idle = indicatorBounds(track, page, direction)
                assertThat(idle.width).isWithin(0.0001f).of(91f)
                assertThat(idle.height).isWithin(0.0001f).of(91f)
                val scale = indicatorMotionScale(Offset(1.50f, 1.50f), 100f)
                val moving = indicatorBounds(track, page, direction, scale.x, scale.y)
                assertThat(moving.width / idle.width).isWithin(0.0001f).of(1.80f)
                assertThat(moving.height / idle.height).isWithin(0.0001f).of(1.125f)
                assertThat((moving.left + moving.right) / 2f).isWithin(0.0001f).of((idle.left + idle.right) / 2f)
                assertThat((moving.top + moving.bottom) / 2f).isWithin(0.0001f).of((idle.top + idle.bottom) / 2f)
            }
        }
    }

    @Test
    fun settleInertiaScalesWithArrivalSpeedAndStopsUnderThreshold() {
        val min = BadgerNavIndicator.INERTIA_MIN_SLOTS_PER_SECOND
        val full = BadgerNavIndicator.INERTIA_FULL_SLOTS_PER_SECOND
        assertThat(settleInertiaStrength(0f)).isEqualTo(0f)
        assertThat(settleInertiaStrength(min - 0.5f)).isEqualTo(0f)
        assertThat(settleInertiaStrength(min)).isEqualTo(0f)
        assertThat(settleInertiaStrength(full)).isEqualTo(1f)
        assertThat(settleInertiaStrength(full * 4f)).isEqualTo(1f)
        assertThat(settleInertiaStrength((min + full) / 2f)).isWithin(0.0001f).of(0.5f)
    }

    @Test
    fun settledCapsuleNarrowsThenSpringsBackAfterArrival() {
        val indicator = mountIndicator()
        val track = Size(400f, 56f)
        val idle = indicatorBounds(track, 3f, LayoutDirection.Ltr)
        compose.runOnIdle { indicator.beginTap(3) }
        compose.mainClock.advanceTimeBy(BadgerMotion.DURATION_INDICATOR + 32L)
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle {
            val deforming = indicator.bounds(track, LayoutDirection.Ltr)
            assertThat(deforming.width).isLessThan(idle.width)
            assertThat(deforming.height).isGreaterThan(idle.height)
        }
        compose.mainClock.advanceTimeBy(1200)
        compose.runOnIdle {
            val restored = indicator.bounds(track, LayoutDirection.Ltr)
            assertThat(restored.width).isWithin(0.5f).of(idle.width)
            assertThat(restored.height).isWithin(0.5f).of(idle.height)
        }
    }

    @Test
    fun inwardTabSpacingKeepsTouchTargetsAndMaximumMovingGlowInsideTheScreen() {
        listOf(1f, 2.55f, 2.625f, 3.5f).forEach { density ->
            listOf(260f, 320f, 360f, 411f, 448f, 600f).forEach { viewport ->
                val screen = (viewport * density).roundToInt()
                val bar = minOf((400f * density).roundToInt(), screen - 2 * (BadgerSpacing.xl.value * density).roundToInt())
                val target = ceil(BadgerSize.minimumTouchTarget.value * density).toInt()
                val inset = navTrackInsetPx(bar, target, (BadgerSpacing.md.value * density).roundToInt())
                val track = Size((bar - 2 * inset).toFloat(), 56f * density)
                assertThat(track.width / MainTab.entries.size).isAtLeast(target.toFloat())
                val scale = indicatorMotionScale(Offset(BadgerNavIndicator.PRESSED_SCALE, BadgerNavIndicator.PRESSED_SCALE), 100f)
                LayoutDirection.entries.forEach { direction ->
                    listOf(0f, 3f).forEach { page ->
                        val bounds = indicatorBounds(track, page, direction, scale.x, scale.y)
                        val origin = (screen - bar) / 2f + inset
                        val glow = GlassGlowOutset.value * density
                        assertThat(origin + bounds.left - glow).isAtLeast(0f)
                        assertThat(origin + bounds.right + glow).isAtMost(screen.toFloat())
                    }
                }
            }
        }
    }

    @Test
    fun onlyTheLightBarBackgroundUsesNinePercentBlack() {
        val light = navBarTint(GlassStyle(enabled = true, dark = false))
        assertThat(light.alpha).isWithin(0.001f).of(0.09f)
        assertThat(light.copy(alpha = 1f)).isEqualTo(Color.Black)
        val dark = GlassStyle(enabled = true, dark = true)
        assertThat(navBarTint(dark)).isEqualTo(dark.chromeTint)
    }

    private fun mountIndicator(): FloatingTabIndicator {
        lateinit var indicator: FloatingTabIndicator
        compose.setContent {
            val scope = rememberCoroutineScope()
            indicator = remember { FloatingTabIndicator(0, scope) }
        }
        compose.mainClock.autoAdvance = false
        return indicator
    }

    private fun moveAndSample(indicator: FloatingTabIndicator, position: Float) {
        compose.runOnIdle { indicator.dragTo(position) }
        compose.mainClock.advanceTimeBy(32)
    }

    private fun assertRest(indicator: FloatingTabIndicator, position: Float) = compose.runOnIdle {
        assertThat(indicator.position).isEqualTo(position)
        assertThat(indicator.velocity).isEqualTo(0f)
        assertThat(indicator.renderedScale).isEqualTo(Offset(1f, 1f))
        assertThat(indicator.opticsActive).isFalse()
    }
}
