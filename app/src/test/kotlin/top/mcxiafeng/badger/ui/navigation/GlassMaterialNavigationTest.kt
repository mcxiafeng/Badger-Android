package top.mcxiafeng.badger.ui.navigation

import android.app.Application
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.ui.FloatingTabIndicator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class GlassMaterialNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun opaqueFloatingBarKeepsTouchSelectionPageSwipesAndDragCancellation() {
        compose.setContent { NavigationFixture(materialEnabled = false) }
        tab(MainTab.MY_PAGE).performTouchInput { click() }
        tab(MainTab.MY_PAGE).assertIsSelected()
        tab(MainTab.MY_CARD).performClick()
        compose.onNodeWithTag("pager").performTouchInput { swipeLeft() }
        tab(MainTab.CONTACTS).assertIsSelected()
        tab(MainTab.CONTACTS).performTouchInput {
            down(center)
            moveBy(Offset(width.toFloat(), 0f))
            cancel()
        }
        compose.waitForIdle()
        tab(MainTab.CONTACTS).assertIsSelected()
    }

    @Test
    fun flatIndicatorAndBarFollowDragWithoutScaleHaloOrRebound() {
        val indicator = mountFlatBar()
        val first = tab(MainTab.MY_CARD)
        first.performTouchInput { down(center) }
        first.performTouchInput { moveBy(Offset(width * 2.5f, 0f)) }
        compose.runOnIdle {
            assertThat(indicator.position).isGreaterThan(0f)
            assertThat(indicator.barFollow.translation.value).isGreaterThan(0f)
            assertFlatFeedback(indicator)
        }
        first.performTouchInput { cancel() }
        compose.runOnIdle {
            assertThat(indicator.position).isEqualTo(0f)
            assertThat(indicator.barFollow.translation.value).isEqualTo(0f)
            assertFlatFeedback(indicator)
        }
        first.assertIsSelected()
    }

    @Test
    fun flatTapAndDragKeepPositionTrackingAndCommitTheSelectedPage() {
        val indicator = mountFlatBar()
        tab(MainTab.MY_PAGE).performClick()
        tab(MainTab.MY_PAGE).assertIsSelected()
        compose.runOnIdle {
            assertThat(indicator.position).isEqualTo(MainTab.MY_PAGE.ordinal.toFloat())
            assertFlatFeedback(indicator)
        }
        tab(MainTab.MY_PAGE).performTouchInput {
            down(center)
            moveBy(Offset(-width * 3f, 0f))
            up()
        }
        tab(MainTab.MY_CARD).assertIsSelected()
        compose.runOnIdle {
            assertThat(indicator.position).isEqualTo(0f)
            assertFlatFeedback(indicator)
        }
    }

    private fun mountFlatBar(): FloatingTabIndicator {
        lateinit var indicator: FloatingTabIndicator
        compose.setContent {
            val scope = rememberCoroutineScope()
            indicator = remember { FloatingTabIndicator(MainTab.MY_CARD.ordinal, scope, effectsEnabled = false) }
            NavigationFixture(indicator, materialEnabled = false)
        }
        return indicator
    }

    private fun assertFlatFeedback(indicator: FloatingTabIndicator) {
        assertThat(indicator.scale.value).isEqualTo(Offset(1f, 1f))
        assertThat(indicator.renderedScale).isEqualTo(Offset(1f, 1f))
        assertThat(indicator.scale.isRunning).isFalse()
        assertThat(indicator.opticalProgress.value).isEqualTo(0f)
        assertThat(indicator.touchGlow.value).isEqualTo(0f)
        assertThat(indicator.barFeedback.scale.value).isEqualTo(1f)
    }

    private fun tab(tab: MainTab) = compose.onNodeWithText(tab.label)
}
