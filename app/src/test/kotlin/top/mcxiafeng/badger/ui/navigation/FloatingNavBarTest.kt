package top.mcxiafeng.badger.ui.navigation

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.ui.FloatingNavBar
import top.mcxiafeng.badger.ui.FloatingTabIndicator
import top.mcxiafeng.badger.ui.NavBarItem
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val PAGER_TAG = "pager"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class FloatingNavBarTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun tabsFollowTouchSelectionAccessibilityActionAndPageSwipe() {
        compose.setContent { NavigationFixture() }

        val tabBounds = MainTab.entries.map { tab(it).fetchSemanticsNode().boundsInRoot }
        tabBounds.zipWithNext { left, right -> assertThat(left.right).isEqualTo(right.left) }

        tab(MainTab.MY_CARD).assertIsSelected()
        tab(MainTab.MY_PAGE).performTouchInput { click() }
        compose.onNodeWithText(MainTab.MY_PAGE.name).assertIsDisplayed()
        tab(MainTab.MY_PAGE).assertIsSelected()
        tab(MainTab.MY_CARD).assertIsNotSelected()

        tab(MainTab.MY_CARD).performClick()
        compose.onNodeWithTag(PAGER_TAG).performTouchInput { swipeLeft() }
        tab(MainTab.CONTACTS).assertIsSelected()
        tab(MainTab.MY_PAGE).assertIsNotSelected()

        tab(MainTab.TODOS).performClick()
        tab(MainTab.TODOS).assertIsSelected()
        compose.onNodeWithText(MainTab.TODOS.name).assertIsDisplayed()
        compose.onNodeWithTag(PAGER_TAG).performTouchInput { swipeLeft() }
        tab(MainTab.MY_PAGE).assertIsSelected()
    }

    @Test
    @Config(qualifiers = "w400dp-h640dp")
    fun fourTabCapsuleKeepsOuterMarginsAndStaysCentered() {
        compose.setContent { NavigationFixture() }

        val pager = compose.onNodeWithTag(PAGER_TAG).fetchSemanticsNode().boundsInRoot
        val left = tab(MainTab.MY_CARD).fetchSemanticsNode().boundsInRoot.left
        val right = tab(MainTab.MY_PAGE).fetchSemanticsNode().boundsInRoot.right
        val marginPx = with(compose.density) { BadgerSpacing.xl.toPx() }
        val maxWidthPx = with(compose.density) { 400.dp.toPx() }
        assertThat(left - pager.left).isAtLeast(marginPx)
        assertThat(pager.right - right).isAtLeast(marginPx)
        assertThat(right - left).isAtMost(maxWidthPx)
        assertThat((left + right) / 2).isWithin(1f).of(pager.center.x)
    }

    @Test
    fun draggingAcrossTabsInRtlSelectsTheTabUnderTheReleasePoint() {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                NavigationFixture()
            }
        }
        val source = tab(MainTab.MY_CARD).fetchSemanticsNode().boundsInRoot.center
        val target = tab(MainTab.MY_PAGE).fetchSemanticsNode().boundsInRoot.center
        tab(MainTab.MY_CARD).performTouchInput { swipe(center, center + (target - source)) }

        tab(MainTab.MY_PAGE).assertIsSelected()
        compose.onNodeWithText(MainTab.MY_PAGE.name).assertIsDisplayed()
    }

    @Test
    fun indicatorMovesBeforeCrossingTheTabBoundaryAndSettlesFromReleasePosition() {
        lateinit var indicator: FloatingTabIndicator
        compose.setContent {
            val scope = rememberCoroutineScope()
            indicator = remember { FloatingTabIndicator(MainTab.MY_CARD.ordinal, scope) }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            indicator.dragBy(0.25f)
            assertThat(indicator.position).isEqualTo(0.25f)
            assertThat(indicator.dragPage).isEqualTo(MainTab.MY_CARD.ordinal)

            indicator.dragBy(0.5f)
            assertThat(indicator.position).isEqualTo(0.75f)
            assertThat(indicator.dragPage).isEqualTo(MainTab.CONTACTS.ordinal)

            indicator.finishDrag(MainTab.CONTACTS.ordinal)
            assertThat(indicator.position).isEqualTo(0.75f)
            assertThat(indicator.dragPage).isNull()
        }
        compose.mainClock.advanceTimeBy(250)
        compose.runOnIdle { assertThat(indicator.position).isEqualTo(1f) }
    }

    @Test
    fun cancellingABottomBarDragLeavesThePagerOnItsCurrentTab() {
        compose.setContent { NavigationFixture() }
        val firstTab = tab(MainTab.MY_CARD)
        val width = firstTab.fetchSemanticsNode().boundsInRoot.width
        firstTab.performTouchInput {
            down(center)
            moveBy(Offset(width, 0f))
        }
        firstTab.assertIsSelected()
        firstTab.performTouchInput { cancel() }

        firstTab.assertIsSelected()
        tab(MainTab.CONTACTS).performTouchInput { click() }
        tab(MainTab.CONTACTS).assertIsSelected()
    }

    @Test
    fun narrowLayoutAtDoubleFontScaleKeepsAllFourTabTargetsAccessible() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                Box(Modifier.width(260.dp)) { NavigationFixture() }
            }
        }
        MainTab.entries.forEach { tab ->
            tab(tab).assertIsDisplayed()
                .assertWidthIsAtLeast(BadgerSize.minimumTouchTarget)
                .assertHeightIsAtLeast(BadgerSize.minimumTouchTarget)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        }
    }

    @Test
    fun classicNavigationExposesFourSelectableTabs() {
        compose.setContent {
            MiuixTheme {
                var page by remember { mutableIntStateOf(MainTab.MY_CARD.ordinal) }
                Row(Modifier.fillMaxWidth()) {
                    MainTab.entries.forEach { destination ->
                        NavBarItem(destination.label, destination.icon, page == destination.ordinal,
                            onClick = { page = destination.ordinal })
                    }
                }
            }
        }
        MainTab.entries.forEach { destination ->
            tab(destination).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        }
        tab(MainTab.TODOS).performClick().assertIsSelected()
        tab(MainTab.MY_CARD).assertIsNotSelected()
        tab(MainTab.MY_PAGE).performClick().assertIsSelected()
    }

    private fun tab(tab: MainTab) = compose.onNodeWithText(tab.label)
}

@Composable
private fun NavigationFixture() {
    MiuixTheme {
        val pager = rememberPagerState { MainTab.entries.size }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            HorizontalPager(pager, Modifier.fillMaxWidth().height(100.dp).testTag(PAGER_TAG)) { page ->
                Text(MainTab.entries[page].name)
            }
            FloatingNavBar(pagerState = pager)
        }
    }
}
