package top.mcxiafeng.badger.ui.navigation

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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

    private fun tab(tab: MainTab) = compose.onNodeWithText(tab.label)
}
