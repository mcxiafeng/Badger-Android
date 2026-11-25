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
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class FloatingNavBufferTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun pointerUpdatesAreBufferedAndReversalsStayBetweenTheVisiblePositionAndTarget() {
        val indicator = mountIndicator()
        compose.runOnIdle {
            indicator.dragTo(3f)
            assertThat(indicator.position).isEqualTo(0f)
        }
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
    fun fillGlassAndSizeFollowTheSameNonlinearProgressAcrossInterruptedPresses() {
        val indicator = mountIndicator()
        compose.runOnIdle { indicator.press(0, 0, 0f) }
        compose.mainClock.advanceTimeBy(80)
        var scaleBeforeRelease = Offset.Zero
        var glassBeforeRelease = 0f
        compose.runOnIdle {
            assertSharedProgress(indicator)
            val progress = indicator.opticalProgress.value
            assertThat(progress).isGreaterThan(0f)
            assertThat(progress).isLessThan(1f)
            assertThat(abs(progress - 64f / 150f)).isGreaterThan(0.05f)
            scaleBeforeRelease = indicator.scale.value
            glassBeforeRelease = progress
            indicator.releasePress(0)
            assertThat(indicator.scale.value).isEqualTo(scaleBeforeRelease)
            assertThat(indicator.opticalProgress.value).isEqualTo(glassBeforeRelease)
        }
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle {
            assertSharedProgress(indicator)
            val scaleBeforePress = indicator.scale.value
            val glassBeforePress = indicator.opticalProgress.value
            indicator.press(0, 0, 0f)
            assertThat(indicator.scale.value).isEqualTo(scaleBeforePress)
            assertThat(indicator.opticalProgress.value).isEqualTo(glassBeforePress)
        }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle {
            assertThat(indicator.scale.value).isEqualTo(Offset(1.50f, 1.50f))
            assertThat(indicator.opticalProgress.value).isEqualTo(1f)
            assertThat(indicator.fillAlpha).isEqualTo(0f)
            indicator.releasePress(0)
        }
        compose.mainClock.advanceTimeBy(200)
        assertIdle(indicator, 0f)
    }

    @Test
    fun releaseAndCancelReplaceBufferedFollowingWithTheOriginal150msSettle() {
        val indicator = mountIndicator()
        compose.runOnIdle { indicator.dragTo(2f) }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle {
            assertThat(indicator.position).isGreaterThan(0f)
            assertThat(indicator.position).isLessThan(2f)
            val beforeRelease = indicator.position
            indicator.finishDrag(2)
            assertThat(indicator.position).isEqualTo(beforeRelease)
        }
        compose.mainClock.advanceTimeBy(176)
        assertIdle(indicator, 2f)
        compose.runOnIdle { indicator.dragTo(0.5f) }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { indicator.finishDrag(2) }
        compose.mainClock.advanceTimeBy(176)
        assertIdle(indicator, 2f)
        compose.mainClock.advanceTimeBy(320)
        assertIdle(indicator, 2f)
    }

    @Test
    fun clickGlassPulseKeepsFillContinuousAndReturnsToBlackIdleFill() {
        val indicator = mountIndicator()
        var sequence = 0
        compose.runOnIdle { sequence = indicator.beginTap(3) }
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle {
            assertThat(indicator.opticalProgress.value).isGreaterThan(0f)
            assertThat(indicator.fillAlpha).isGreaterThan(0f)
            assertThat(indicator.fillAlpha).isLessThan(0.19f)
            assertThat(indicator.scale.value).isEqualTo(Offset(1f, 1f))
            assertThat(indicator.fillAlpha / 0.19f).isWithin(0.001f).of(1f - indicator.opticalProgress.value)
        }
        compose.mainClock.advanceTimeBy(128)
        compose.runOnIdle { indicator.finishTap(sequence, 3) }
        assertIdle(indicator, 3f)
    }

    @Test
    fun holdingAnotherTabEnlargesWithoutDraggingAndReleaseRetreatsAllValuesTogether() {
        val indicator = mountIndicator(withNavigation = true)
        val target = compose.onNodeWithText(MainTab.MY_PAGE.label)
        target.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle {
            assertThat(indicator.dragPage).isNull()
            assertThat(indicator.position).isEqualTo(3f)
            assertThat(indicator.velocity).isEqualTo(0f)
            assertThat(indicator.renderedScale).isEqualTo(Offset(1.50f, 1.50f))
            assertThat(indicator.opticalProgress.value).isEqualTo(1f)
            assertThat(indicator.fillAlpha).isEqualTo(0f)
        }
        target.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle {
            assertSharedProgress(indicator)
            assertThat(indicator.scale.value.x).isGreaterThan(1f)
            assertThat(indicator.scale.value.x).isLessThan(1.50f)
            assertThat(indicator.fillAlpha).isGreaterThan(0f)
            assertThat(indicator.fillAlpha).isLessThan(0.19f)
        }
        compose.mainClock.advanceTimeBy(120)
        target.assertIsSelected()
        assertIdle(indicator, 3f)
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

    private fun assertSharedProgress(indicator: FloatingTabIndicator) {
        val growth = (indicator.scale.value.x - 1f) / 0.50f
        assertThat(growth).isWithin(0.001f).of(indicator.opticalProgress.value)
        assertThat(indicator.fillAlpha / 0.19f).isWithin(0.001f).of(1f - growth)
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
