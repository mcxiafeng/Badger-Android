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
import top.mcxiafeng.badger.ui.navTrackInsetPx
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
    fun stationaryPressOnlyShrinksAndTravelNeverDeformsTheCapsule() {
        val indicator = mountIndicator()
        compose.runOnIdle { indicator.press(0, 0, 0f) }
        compose.mainClock.advanceTimeBy(VELOCITY_SETTLE_MS)
        compose.runOnIdle {
            assertThat(indicator.velocity).isEqualTo(0f)
            assertThat(indicator.renderedScale)
                .isEqualTo(Offset(BadgerNavIndicator.PRESSED_SCALE, BadgerNavIndicator.PRESSED_SCALE))
            indicator.releasePress(0)
            indicator.press(3, 0, 3f)
        }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle {
            // 位移期间无速度形变：宽高等比且与真实缩放值一致
            assertThat(indicator.renderedScale).isEqualTo(indicator.scale.value)
            assertThat(indicator.renderedScale.x).isEqualTo(indicator.renderedScale.y)
        }
        compose.mainClock.advanceTimeBy(VELOCITY_SETTLE_MS)
        compose.runOnIdle {
            assertThat(indicator.position).isEqualTo(3f)
            assertThat(indicator.velocity).isEqualTo(0f)
            assertThat(indicator.renderedScale)
                .isEqualTo(Offset(BadgerNavIndicator.PRESSED_SCALE, BadgerNavIndicator.PRESSED_SCALE))
            assertThat(indicator.opticalProgress.value).isEqualTo(1f)
            assertThat(indicator.fillAlpha).isEqualTo(0f)
            indicator.releasePress(0)
        }
        compose.mainClock.advanceTimeBy(VELOCITY_SETTLE_MS)
        assertRest(indicator, 0f)
    }

    @Test
    fun accessibilityTapTravelsWithoutDeformingAndEndsWithinTheOriginalDuration() {
        val indicator = mountIndicator()
        var sequence = 0
        compose.runOnIdle { sequence = indicator.beginTap(3) }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle {
            assertThat(indicator.position).isGreaterThan(0f)
            assertThat(indicator.position).isLessThan(3f)
            // 无速度形变：tap 位移期间选中框保持原始方形比例
            assertThat(indicator.renderedScale).isEqualTo(Offset(1f, 1f))
        }
        compose.mainClock.advanceTimeBy(VELOCITY_SETTLE_MS)
        compose.runOnIdle { indicator.finishTap(sequence, 3) }
        assertRest(indicator, 3f)
    }

    @Test
    fun idleBoundsShrinkNinePercentAndPressedBoundsKeepTheSameEndpointCenters() {
        val track = Size(400f, 100f)
        LayoutDirection.entries.forEach { direction ->
            listOf(0f, 3f).forEach { page ->
                val idle = indicatorBounds(track, page, direction)
                assertThat(idle.width).isWithin(0.0001f).of(91f)
                assertThat(idle.height).isWithin(0.0001f).of(91f)
                val pressed = indicatorBounds(
                    track, page, direction, BadgerNavIndicator.PRESSED_SCALE, BadgerNavIndicator.PRESSED_SCALE,
                )
                assertThat(pressed.width / idle.width).isWithin(0.0001f).of(BadgerNavIndicator.PRESSED_SCALE)
                assertThat(pressed.height / idle.height).isWithin(0.0001f).of(BadgerNavIndicator.PRESSED_SCALE)
                assertThat((pressed.left + pressed.right) / 2f).isWithin(0.0001f).of((idle.left + idle.right) / 2f)
                assertThat((pressed.top + pressed.bottom) / 2f).isWithin(0.0001f).of((idle.top + idle.bottom) / 2f)
            }
        }
    }

    @Test
    fun inwardTabSpacingKeepsTouchTargetsAndMaximumPressedCapsuleInsideTheScreen() {
        listOf(1f, 2.55f, 2.625f, 3.5f).forEach { density ->
            listOf(260f, 320f, 360f, 411f, 448f, 600f).forEach { viewport ->
                val screen = (viewport * density).roundToInt()
                val bar = minOf((400f * density).roundToInt(), screen - 2 * (BadgerSpacing.xl.value * density).roundToInt())
                val target = ceil(BadgerSize.minimumTouchTarget.value * density).toInt()
                val inset = navTrackInsetPx(bar, target, (BadgerSpacing.md.value * density).roundToInt())
                val track = Size((bar - 2 * inset).toFloat(), 56f * density)
                assertThat(track.width / MainTab.entries.size).isAtLeast(target.toFloat())
                val scale = Offset(BadgerNavIndicator.PRESSED_SCALE, BadgerNavIndicator.PRESSED_SCALE)
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

    private fun assertRest(indicator: FloatingTabIndicator, position: Float) = compose.runOnIdle {
        assertThat(indicator.position).isEqualTo(position)
        assertThat(indicator.velocity).isEqualTo(0f)
        assertThat(indicator.renderedScale).isEqualTo(Offset(1f, 1f))
        assertThat(indicator.opticsActive).isFalse()
    }
}
