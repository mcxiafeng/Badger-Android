package top.mcxiafeng.badger.ui.navigation

import android.app.Application
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.ui.FloatingTabIndicator
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.navPositionEasing

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class FloatingNavTapContinuityTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun repeatingTheSameTapTargetPreservesTheOriginalFrameTimeline() {
        val indicators = mountIndicators()
        compose.runOnIdle { indicators.forEach { it.beginTap(3) } }
        compose.mainClock.advanceTimeBy(96)
        compose.runOnIdle { indicators[0].beginTap(3) }
        repeat(8) {
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                assertThat(indicators[0].position).isWithin(0.0001f).of(indicators[1].position)
                assertThat(indicators[0].velocity).isWithin(0.01f).of(indicators[1].velocity)
            }
        }
    }

    @Test
    fun pointerUpHandsTravelToTheClickWithoutReversingOrRestartingIt() {
        val indicators = mountIndicators()
        compose.runOnIdle { indicators.forEach { it.press(3, 0, 3f) } }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle {
            indicators[0].releasePress(0, deferToClick = true)
            indicators[0].beginTap(3)
        }
        repeat(8) {
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                assertThat(indicators[0].position).isWithin(0.0001f).of(indicators[1].position)
            }
        }
    }

    @Test
    fun releaseWithoutAClickStillReturnsToTheCurrentPage() {
        val indicator = mountIndicators().first()
        compose.runOnIdle { indicator.press(3, 0, 3f) }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { indicator.releasePress(0, deferToClick = true) }
        compose.mainClock.advanceTimeBy(BadgerMotion.DURATION_INDICATOR + 64L)
        compose.runOnIdle {
            assertThat(indicator.position).isEqualTo(0f)
            assertThat(indicator.velocity).isEqualTo(0f)
        }
    }

    @Test
    fun retargetCurveCarriesForwardSpeedAndRemainsBoundedInBothDirections() {
        listOf(-1f, 1f).forEach { direction ->
            val easing = navPositionEasing(direction, direction * 4f)
            val firstFraction = 0.001f
            val initialSpeed = easing.transform(firstFraction) / firstFraction * direction *
                1_000f / BadgerMotion.DURATION_INDICATOR
            assertThat(initialSpeed).isWithin(0.05f).of(direction * 4f)
            var previous = 0f
            repeat(101) { step ->
                val value = easing.transform(step / 100f)
                assertThat(value).isAtLeast(previous)
                assertThat(value).isAtMost(1f)
                previous = value
            }
            assertThat(easing.transform(1f)).isEqualTo(1f)
        }
    }

    private fun mountIndicators(): List<FloatingTabIndicator> {
        lateinit var indicators: List<FloatingTabIndicator>
        compose.setContent {
            val scope = rememberCoroutineScope()
            indicators = remember(scope) { List(2) { FloatingTabIndicator(0, scope) } }
        }
        compose.mainClock.autoAdvance = false
        return indicators
    }
}
