package top.mcxiafeng.badger.ui.navigation

import android.app.Application
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.ui.FloatingTabIndicator
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.designsystem.BadgerNavIndicator
import top.mcxiafeng.badger.ui.designsystem.BadgerTouchFeedback
import kotlin.math.abs

private const val SETTLE_MS = BadgerMotion.DURATION_INDICATOR + BadgerMotion.DURATION_INDICATOR_GLASS + 64L

/** 释放后等待窗：条面 150ms 单段回收 + 指示器/页面 239ms 回位都要完成。 */
private const val BAR_RELEASE_MS = BadgerMotion.DURATION_INDICATOR + 64L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class FloatingNavBufferTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun pointerUpdatesAreBufferedAndReversalsStayBetweenTheVisiblePositionAndTarget() {
        val indicator = mountIndicator()
        compose.runOnIdle { indicator.dragTo(3f); assertThat(indicator.position).isEqualTo(0f) }
        compose.mainClock.advanceTimeBy(80)
        var previous = 0f
        compose.runOnIdle {
            previous = indicator.position
            assertThat(previous).isGreaterThan(0f)
            assertThat(previous).isLessThan(3f)
            indicator.dragTo(-1f)
            assertThat(indicator.position).isEqualTo(previous)
        }
        repeat(20) {
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                assertThat(indicator.position).isAtLeast(0f)
                assertThat(indicator.position).isAtMost(previous)
                previous = indicator.position
            }
        }
        compose.runOnIdle {
            assertThat(indicator.position).isEqualTo(0f)
            assertThat(indicator.velocity).isEqualTo(0f)
        }
    }

    @Test
    fun alternatingSmallPointerJitterIsAttenuatedWhileDragging() {
        val indicator = mountIndicator()
        compose.runOnIdle { indicator.dragTo(1f) }
        compose.mainClock.advanceTimeBy(320)
        compose.runOnIdle { assertThat(indicator.position).isEqualTo(1f) }
        repeat(12) { index ->
            compose.runOnIdle { indicator.dragTo(if (index % 2 == 0) 1.06f else 0.94f) }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { assertThat(abs(indicator.position - 1f)).isLessThan(0.04f) }
        }
        compose.runOnIdle { indicator.dragTo(1f) }
        compose.mainClock.advanceTimeBy(320)
        compose.runOnIdle {
            assertThat(indicator.position).isEqualTo(1f)
            assertThat(indicator.velocity).isEqualTo(0f)
        }
    }

    @Test
    fun glassFinishesIn100msWhileShapeContinuesIts239msNonlinearTransition() {
        val indicator = mountIndicator()
        compose.runOnIdle { indicator.press(0, 0, 0f) }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle {
            assertThat(indicator.opticalProgress.value).isGreaterThan(0f)
            assertThat(indicator.opticalProgress.value).isLessThan(1f)
        }
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle {
            assertThat(indicator.opticalProgress.value).isEqualTo(1f)
            assertThat(indicator.scale.value.x).isGreaterThan(BadgerNavIndicator.PRESSED_SCALE)
            assertThat(indicator.scale.value.x).isLessThan(1f)
        }
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle {
            assertThat(indicator.scale.value)
                .isEqualTo(Offset(BadgerNavIndicator.PRESSED_SCALE, BadgerNavIndicator.PRESSED_SCALE))
            indicator.releasePress(0)
        }
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle {
            val before = indicator.scale.value
            indicator.press(0, 0, 0f)
            assertThat(indicator.scale.value).isEqualTo(before)
        }
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.runOnIdle {
            assertThat(indicator.scale.value)
                .isEqualTo(Offset(BadgerNavIndicator.PRESSED_SCALE, BadgerNavIndicator.PRESSED_SCALE))
            assertThat(indicator.opticalProgress.value).isEqualTo(1f)
            indicator.releasePress(0)
        }
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        assertIdle(indicator, 0f)
    }

    @Test
    fun releaseAndCancelReplaceBufferedFollowingWithThe239msSettle() {
        val indicator = mountIndicator()
        compose.runOnIdle { indicator.dragTo(2f) }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle {
            val beforeRelease = indicator.position
            assertThat(beforeRelease).isGreaterThan(0f)
            assertThat(beforeRelease).isLessThan(2f)
            indicator.finishDrag(2)
            assertThat(indicator.position).isEqualTo(beforeRelease)
        }
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        assertIdle(indicator, 2f)
        compose.runOnIdle { indicator.dragTo(0.5f) }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { indicator.finishDrag(2) }
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        assertIdle(indicator, 2f)
    }

    @Test
    fun clickKeepsGlassUntilTravelStopsThenReturnsToBlackIdleFill() {
        val indicator = mountIndicator()
        var sequence = 0
        compose.runOnIdle { sequence = indicator.beginTap(3) }
        compose.mainClock.advanceTimeBy(128)
        compose.runOnIdle {
            assertThat(indicator.opticalProgress.value).isEqualTo(1f)
            assertThat(indicator.fillAlpha).isEqualTo(0f)
            assertThat(indicator.position).isLessThan(3f)
            assertThat(indicator.scale.value).isEqualTo(Offset(1f, 1f))
        }
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.runOnIdle { indicator.finishTap(sequence, 3) }
        assertIdle(indicator, 3f)
    }

    @Test
    fun holdingAnotherTabShrinksAndReleaseKeepsSelectionAndIndependentTiming() {
        val indicator = mountIndicator(withNavigation = true)
        val target = compose.onNodeWithText(MainTab.MY_PAGE.label)
        target.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.runOnIdle {
            assertThat(indicator.dragPage).isNull()
            assertThat(indicator.position).isEqualTo(3f)
            assertThat(indicator.renderedScale)
                .isEqualTo(Offset(BadgerNavIndicator.PRESSED_SCALE, BadgerNavIndicator.PRESSED_SCALE))
            assertThat(indicator.barFeedback.scale.value).isWithin(0.001f).of(BadgerTouchFeedback.NAV_PRESSED_SCALE)
            assertThat(indicator.opticalProgress.value).isEqualTo(1f)
        }
        target.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle {
            assertThat(indicator.scale.value.x).isGreaterThan(BadgerNavIndicator.PRESSED_SCALE)
            assertThat(indicator.scale.value.x).isLessThan(1f)
            assertThat(indicator.fillAlpha / 0.19f).isWithin(0.001f).of(1f - indicator.opticalProgress.value)
        }
        compose.mainClock.advanceTimeBy(BAR_RELEASE_MS)
        target.assertIsSelected()
        assertIdle(indicator, 3f)
        compose.runOnIdle { assertThat(indicator.barFeedback.scale.value).isEqualTo(1f) }
    }

    @Test
    fun barSurfacePressActivatesGlassAndWholeBarShrinksWithoutMovingSelection() {
        val indicator = mountIndicator()
        compose.runOnIdle { indicator.pressSurface() }
        compose.mainClock.advanceTimeBy(128)
        compose.runOnIdle {
            assertThat(indicator.opticalProgress.value).isEqualTo(1f)
            assertThat(indicator.position).isEqualTo(0f)
        }
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle {
            assertThat(indicator.barFeedback.scale.value).isWithin(0.001f).of(BadgerTouchFeedback.NAV_PRESSED_SCALE)
            indicator.releaseSurface()
        }
        compose.mainClock.advanceTimeBy(75)
        compose.runOnIdle {
            // 单段释放：条面从 0.98 回 1.0，绝不出现 0.95 谷底
            assertThat(indicator.barFeedback.scale.value).isGreaterThan(BadgerTouchFeedback.NAV_PRESSED_SCALE)
            assertThat(indicator.barFeedback.scale.value).isLessThan(1f)
        }
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle { assertThat(indicator.barFeedback.scale.value).isEqualTo(1f) }
        assertIdle(indicator, 0f)
    }

    private fun mountIndicator(withNavigation: Boolean = false): FloatingTabIndicator {
        lateinit var indicator: FloatingTabIndicator
        compose.setContent {
            val scope = rememberCoroutineScope()
            indicator = remember { FloatingTabIndicator(0, scope) }
            if (withNavigation) NavigationFixture(indicator)
        }
        compose.mainClock.autoAdvance = false
        return indicator
    }

    private fun assertIdle(indicator: FloatingTabIndicator, position: Float) = compose.runOnIdle {
        assertThat(indicator.position).isEqualTo(position)
        assertThat(indicator.dragPage).isNull()
        assertThat(indicator.velocity).isEqualTo(0f)
        assertThat(indicator.renderedScale).isEqualTo(Offset(1f, 1f))
        assertThat(indicator.opticsActive).isFalse()
        assertThat(indicator.fillAlpha).isEqualTo(0.19f)
    }
}
