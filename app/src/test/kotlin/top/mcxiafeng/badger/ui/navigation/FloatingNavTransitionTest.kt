package top.mcxiafeng.badger.ui.navigation

import android.app.Application
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.ui.FloatingTabIndicator
import top.mcxiafeng.badger.ui.IndicatorDeformation
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.indicatorBounds

private const val TRANSITION_SETTLE_MS = BadgerMotion.DURATION_INDICATOR + BadgerMotion.DURATION_INDICATOR_GLASS + 64L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class FloatingNavTransitionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun releasingSelectedTabCompletesEveryIdleValue() {
        val indicator = mountIndicator()
        val tab = compose.onNodeWithText(MainTab.MY_CARD.label)
        tab.performTouchInput { down(center) }
        compose.runOnIdle {
            assertThat(indicator.scale.value).isEqualTo(Offset(1.50f, 1.50f))
            assertThat(indicator.opticalProgress.value).isEqualTo(1f)
            assertThat(indicator.fillAlpha).isEqualTo(0f)
        }
        compose.mainClock.autoAdvance = false
        tab.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(80)
        assertRetreatIsContinuous(indicator)
        compose.mainClock.advanceTimeBy(TRANSITION_SETTLE_MS)
        assertIdleRemainsStable(indicator, MainTab.MY_CARD.ordinal)
    }

    @Test
    fun quickTapOnCurrentTabLeavesNoResidualTransition() {
        val indicator = mountIndicator()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText(MainTab.MY_CARD.label).performTouchInput { click() }
        compose.mainClock.advanceTimeBy(TRANSITION_SETTLE_MS)
        assertIdleRemainsStable(indicator, MainTab.MY_CARD.ordinal)
    }

    @Test
    fun dragReleaseCompletesEveryIdleValueAtTheOtherEnd() {
        val indicator = mountIndicator()
        val first = compose.onNodeWithText(MainTab.MY_CARD.label)
        first.performTouchInput { down(center) }
        first.performTouchInput { moveBy(Offset(width * 3f, 0f)) }
        compose.runOnIdle { assertThat(indicator.dragPage).isEqualTo(MainTab.MY_PAGE.ordinal) }
        compose.mainClock.autoAdvance = false
        first.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(80)
        assertRetreatIsContinuous(indicator)
        compose.mainClock.advanceTimeBy(TRANSITION_SETTLE_MS)
        compose.onNodeWithText(MainTab.MY_PAGE.label).assertIsSelected()
        assertIdleRemainsStable(indicator, MainTab.MY_PAGE.ordinal)
    }

    @Test
    fun aNewTouchDuringRetreatDoesNotLeaveStaleOpticsOrScale() {
        val indicator = mountIndicator()
        val first = compose.onNodeWithText(MainTab.MY_CARD.label)
        first.performTouchInput { down(center) }
        compose.mainClock.autoAdvance = false
        first.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(64)
        val target = compose.onNodeWithText(MainTab.MY_PAGE.label)
        target.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(64)
        target.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(TRANSITION_SETTLE_MS)
        target.assertIsSelected()
        assertIdleRemainsStable(indicator, MainTab.MY_PAGE.ordinal)
    }

    @Test
    fun rapidTabTapsCompleteOnlyTheLatestTransition() {
        val indicator = mountIndicator()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText(MainTab.MY_PAGE.label).performTouchInput { click() }
        compose.mainClock.advanceTimeBy(48)
        val latest = compose.onNodeWithText(MainTab.CONTACTS.label)
        latest.performTouchInput { click() }
        compose.mainClock.advanceTimeBy(TRANSITION_SETTLE_MS)
        latest.assertIsSelected()
        assertIdleRemainsStable(indicator, MainTab.CONTACTS.ordinal)
    }

    @Test
    fun endpointCentersRemainAtIdleCentersThroughoutTheRetreat() {
        lateinit var indicators: List<Pair<Int, FloatingTabIndicator>>
        compose.setContent {
            val scope = rememberCoroutineScope()
            indicators = remember {
                listOf(MainTab.MY_CARD.ordinal, MainTab.MY_PAGE.ordinal).map { it to FloatingTabIndicator(it, scope) }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { indicators.forEach { (page, indicator) -> indicator.press(page, page, page.toFloat()) } }
        compose.mainClock.advanceTimeBy(TRANSITION_SETTLE_MS)
        compose.runOnIdle { indicators.forEach { (page, indicator) -> indicator.releasePress(page) } }
        repeat(26) {
            compose.mainClock.advanceTimeBy(16)
            compose.runOnIdle {
                indicators.forEach { (page, indicator) -> assertEndpointCenter(indicator, page) }
            }
        }
        indicators.forEach { (page, indicator) -> assertIdleRemainsStable(indicator, page) }
    }

    private fun mountIndicator(): FloatingTabIndicator {
        lateinit var indicator: FloatingTabIndicator
        compose.setContent {
            val scope = rememberCoroutineScope()
            indicator = remember { FloatingTabIndicator(MainTab.MY_CARD.ordinal, scope) }
            NavigationFixture(indicator)
        }
        return indicator
    }

    private fun assertRetreatIsContinuous(indicator: FloatingTabIndicator) {
        compose.runOnIdle {
            val remainingScale = (indicator.scale.value.x - 1f) / 0.50f
            assertThat(remainingScale).isGreaterThan(0f)
            assertThat(remainingScale).isLessThan(1f)
            assertThat(indicator.opticalProgress.value).isAtLeast(0f)
            assertThat(indicator.opticalProgress.value).isAtMost(1f)
            assertThat(indicator.fillAlpha / 0.19f).isWithin(0.01f).of(1f - indicator.opticalProgress.value)
        }
    }

    private fun assertIdleRemainsStable(indicator: FloatingTabIndicator, page: Int) {
        compose.runOnIdle { assertIdle(indicator, page) }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertIdle(indicator, page) }
    }

    private fun assertIdle(indicator: FloatingTabIndicator, page: Int) {
        assertThat(indicator.position).isEqualTo(page.toFloat())
        assertThat(indicator.dragPage).isNull()
        assertThat(indicator.deformation).isEqualTo(IndicatorDeformation.Rest)
        assertThat(indicator.velocity).isEqualTo(0f)
        assertThat(indicator.renderedScale).isEqualTo(Offset(1f, 1f))
        assertThat(indicator.scale.value).isEqualTo(Offset(1f, 1f))
        assertThat(indicator.scale.targetValue).isEqualTo(Offset(1f, 1f))
        assertThat(indicator.scale.isRunning).isFalse()
        assertThat(indicator.opticalProgress.value).isEqualTo(0f)
        assertThat(indicator.opticalProgress.targetValue).isEqualTo(0f)
        assertThat(indicator.opticalProgress.isRunning).isFalse()
        assertThat(indicator.opticsActive).isFalse()
        assertThat(indicator.fillAlpha).isEqualTo(0.19f)
    }

    private fun assertEndpointCenter(indicator: FloatingTabIndicator, page: Int) {
        val track = Size(400f, 56f)
        LayoutDirection.entries.forEach { direction ->
            val idle = indicatorBounds(track, page.toFloat(), direction)
            val current = indicator.bounds(track, direction)
            assertThat((current.left + current.right) / 2f).isWithin(0.01f).of((idle.left + idle.right) / 2f)
            assertThat((current.top + current.bottom) / 2f).isWithin(0.01f).of((idle.top + idle.bottom) / 2f)
        }
    }
}
