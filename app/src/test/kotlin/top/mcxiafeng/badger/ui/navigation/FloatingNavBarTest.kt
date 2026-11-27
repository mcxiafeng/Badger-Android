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
import androidx.compose.ui.geometry.Size
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
import top.mcxiafeng.badger.ui.IndicatorDeformation
import top.mcxiafeng.badger.ui.NavBarItem
import top.mcxiafeng.badger.ui.indicatorBounds
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.designsystem.BadgerNavIndicator
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.glass.GlassStyle
import top.mcxiafeng.badger.ui.glass.LocalGlassStyle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val PAGER_TAG = "pager"
private const val NAV_SETTLE_MS = BadgerMotion.DURATION_INDICATOR + BadgerMotion.DURATION_INDICATOR_GLASS + 64L

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
        val insetPx = with(compose.density) { BadgerSpacing.xs.toPx() }
        assertThat(left - pager.left).isWithin(2f).of(marginPx + insetPx)
        assertThat(pager.right - right).isWithin(2f).of(marginPx + insetPx)
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
    fun indicatorBuffersBeforeTheTabBoundaryAndSettlesFromItsActualReleasePosition() {
        lateinit var indicator: FloatingTabIndicator
        compose.setContent {
            val scope = rememberCoroutineScope()
            indicator = remember { FloatingTabIndicator(MainTab.MY_CARD.ordinal, scope) }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            indicator.dragTo(0.25f)
            assertThat(indicator.position).isEqualTo(0f)
            assertThat(indicator.dragPage).isEqualTo(MainTab.MY_CARD.ordinal)
        }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle {
            assertThat(indicator.position).isGreaterThan(0f)
            assertThat(indicator.position).isLessThan(0.25f)
            indicator.dragTo(0.75f)
            assertThat(indicator.dragPage).isEqualTo(MainTab.CONTACTS.ordinal)
        }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle {
            val releasedFrom = indicator.position
            assertThat(releasedFrom).isGreaterThan(0.25f)
            assertThat(releasedFrom).isLessThan(0.75f)
            indicator.finishDrag(MainTab.CONTACTS.ordinal)
            assertThat(indicator.position).isEqualTo(releasedFrom)
            assertThat(indicator.dragPage).isNull()
        }
        compose.mainClock.advanceTimeBy(260)
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
    fun selectedTabShrinksSlightlyBeforeMovementAndCancellationRestoresIt() {
        lateinit var indicator: FloatingTabIndicator
        compose.setContent {
            val scope = rememberCoroutineScope()
            indicator = remember { FloatingTabIndicator(MainTab.MY_CARD.ordinal, scope) }
            NavigationFixture(indicator)
        }
        val selected = tab(MainTab.MY_CARD)
        selected.performTouchInput { down(center) }
        compose.runOnIdle {
            assertThat(indicator.deformation).isEqualTo(IndicatorDeformation.Pressed)
            assertThat(indicator.dragPage).isNull()
            assertThat(indicator.position).isEqualTo(MainTab.MY_CARD.ordinal.toFloat())
            assertThat(indicator.scale.value)
                .isEqualTo(Offset(BadgerNavIndicator.PRESSED_SCALE, BadgerNavIndicator.PRESSED_SCALE))
        }
        selected.performTouchInput { cancel() }
        compose.runOnIdle {
            assertThat(indicator.deformation).isEqualTo(IndicatorDeformation.Rest)
            assertThat(indicator.scale.value).isEqualTo(Offset(1f, 1f))
        }
        selected.assertIsSelected()
    }

    @Test
    fun tappingAnotherTabTracksTheTapUntilTransitionFinishes() {
        lateinit var indicator: FloatingTabIndicator
        compose.setContent {
            val scope = rememberCoroutineScope()
            indicator = remember { FloatingTabIndicator(MainTab.MY_CARD.ordinal, scope) }
            NavigationFixture(indicator)
        }
        compose.mainClock.autoAdvance = false
        val target = tab(MainTab.MY_PAGE)
        target.performTouchInput { down(center) }
        compose.runOnIdle {
            assertThat(indicator.deformation).isEqualTo(IndicatorDeformation.Pressed)
            assertThat(indicator.dragPage).isNull()
        }
        target.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { assertThat(indicator.deformation).isEqualTo(IndicatorDeformation.Tapped) }
        compose.mainClock.advanceTimeBy(600)
        target.assertIsSelected()
        compose.runOnIdle {
            assertThat(indicator.deformation).isEqualTo(IndicatorDeformation.Rest)
            assertThat(indicator.position).isEqualTo(MainTab.MY_PAGE.ordinal.toFloat())
        }
    }

    @Test
    fun draggingFromAnUnselectedTabKeepsPressShrinkAndCancelRestoresIt() {
        lateinit var indicator: FloatingTabIndicator
        compose.setContent {
            val scope = rememberCoroutineScope()
            indicator = remember { FloatingTabIndicator(MainTab.MY_CARD.ordinal, scope) }
            NavigationFixture(indicator)
        }
        val target = tab(MainTab.CONTACTS)
        target.performTouchInput { down(center) }
        compose.runOnIdle { assertThat(indicator.deformation).isEqualTo(IndicatorDeformation.Pressed) }
        target.performTouchInput { moveBy(Offset(width.toFloat(), 0f)) }
        compose.runOnIdle { assertThat(indicator.deformation).isEqualTo(IndicatorDeformation.Pressed) }
        target.performTouchInput { cancel() }
        compose.runOnIdle { assertThat(indicator.deformation).isEqualTo(IndicatorDeformation.Rest) }
        tab(MainTab.MY_CARD).assertIsSelected()
    }

    @Test
    fun clickPositionRunsAloneWithoutDeformationAndGlassFollowsOnlyRealTravel() {
        lateinit var indicator: FloatingTabIndicator
        compose.setContent {
            val scope = rememberCoroutineScope()
            indicator = remember { FloatingTabIndicator(MainTab.MY_CARD.ordinal, scope) }
        }
        compose.mainClock.autoAdvance = false
        var sequence = 0
        compose.runOnIdle {
            // At rest the capsule is a flat selection, not glass.
            assertThat(indicator.opticsActive).isFalse()
            sequence = indicator.beginTap(MainTab.MY_PAGE.ordinal)
        }
        compose.runOnIdle {
            // Any displacement turns the glass on, even without a finger on the bar.
            assertThat(indicator.opticsActive).isTrue()
        }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle {
            assertThat(indicator.position).isGreaterThan(0f)
            assertThat(indicator.position).isLessThan(MainTab.MY_PAGE.ordinal.toFloat())
            // 无速度形变：tap 位移期间选中框保持原始方形比例
            assertThat(indicator.renderedScale).isEqualTo(Offset(1f, 1f))
            assertThat(indicator.opticsActive).isTrue()
        }
        compose.mainClock.advanceTimeBy(220)
        compose.runOnIdle {
            indicator.finishTap(sequence, MainTab.MY_PAGE.ordinal)
            assertThat(indicator.position).isEqualTo(MainTab.MY_PAGE.ordinal.toFloat())
            assertThat(indicator.scale.value).isEqualTo(Offset(1f, 1f))
        }
        // The glass fades out once the capsule has settled, since no travel is left to light it.
        compose.mainClock.advanceTimeBy(260)
        compose.runOnIdle { assertThat(indicator.opticsActive).isFalse() }
    }

    @Test
    fun fingerPositionTracksAbsolutelyFromAnotherTabInLtr() {
        assertAbsolutePointerTracking(LayoutDirection.Ltr)
    }

    @Test
    fun fingerPositionTracksAbsolutelyFromAnotherTabInRtl() {
        assertAbsolutePointerTracking(LayoutDirection.Rtl)
    }

    private fun assertAbsolutePointerTracking(direction: LayoutDirection) {
        lateinit var indicator: FloatingTabIndicator
        compose.setContent {
            val scope = rememberCoroutineScope()
            indicator = remember { FloatingTabIndicator(MainTab.MY_CARD.ordinal, scope) }
            CompositionLocalProvider(LocalLayoutDirection provides direction) { NavigationFixture(indicator) }
        }
        tab(MainTab.MY_PAGE).performClick()
        val target = tab(MainTab.CONTACTS)
        val bounds = target.fetchSemanticsNode().boundsInRoot
        target.performTouchInput { down(Offset(width * 0.25f, center.y)) }
        val initial = if (direction == LayoutDirection.Ltr) 0.75f else 1.25f
        compose.runOnIdle { assertThat(indicator.position).isWithin(0.02f).of(initial) }
        // The finger stays at its screen point while the entire bar grows around its centre.
        // Read the rendered track after growth, then predict the tab under the injected screen point.
        val allBounds = MainTab.entries.map { tab(it).fetchSemanticsNode().boundsInRoot }
        val left = allBounds.minOf { it.left }
        val right = allBounds.maxOf { it.right }
        val heldBounds = target.fetchSemanticsNode().boundsInRoot
        val movementPx = heldBounds.width * 0.6f
        val visualSlotsFromTarget = (bounds.left + bounds.width * 0.25f + movementPx - heldBounds.center.x) / heldBounds.width
        val moved = MainTab.CONTACTS.ordinal + if (direction == LayoutDirection.Ltr) visualSlotsFromTarget else -visualSlotsFromTarget
        target.performTouchInput { moveBy(Offset(movementPx, 0f)) }
        compose.runOnIdle { assertThat(indicator.position).isWithin(0.02f).of(moved) }
        target.performTouchInput { moveTo(Offset(left - heldBounds.left, center.y)) }
        val leftEdge = if (direction == LayoutDirection.Ltr) 0f else 3f
        compose.runOnIdle { assertThat(indicator.position).isWithin(0.02f).of(leftEdge) }
        target.performTouchInput { moveTo(Offset(right - heldBounds.left, center.y)) }
        val rightEdge = if (direction == LayoutDirection.Ltr) 3f else 0f
        compose.runOnIdle { assertThat(indicator.position).isWithin(0.02f).of(rightEdge) }
        target.performTouchInput { cancel() }
        tab(MainTab.MY_PAGE).assertIsSelected()
        compose.runOnIdle { assertThat(indicator.position).isEqualTo(MainTab.MY_PAGE.ordinal.toFloat()) }
    }

    @Test
    fun releasingStartsOpticalFadeAndScaleRecoveryTogether() {
        lateinit var indicator: FloatingTabIndicator
        compose.setContent {
            val scope = rememberCoroutineScope()
            indicator = remember { FloatingTabIndicator(MainTab.MY_CARD.ordinal, scope) }
            NavigationFixture(indicator)
        }
        val selected = tab(MainTab.MY_CARD)
        selected.performTouchInput { down(center) }
        compose.runOnIdle {
            assertThat(indicator.scale.value.x).isWithin(0.001f).of(BadgerNavIndicator.PRESSED_SCALE)
            assertThat(indicator.opticalProgress.value).isEqualTo(1f)
        }
        compose.mainClock.autoAdvance = false
        selected.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle {
            val remainingScale = (indicator.scale.value.x - 1f) / (BadgerNavIndicator.PRESSED_SCALE - 1f)
            assertThat(remainingScale).isGreaterThan(0f)
            assertThat(remainingScale).isLessThan(1f)
            assertThat(indicator.opticalProgress.value).isLessThan(remainingScale)
            assertThat(indicator.fillAlpha / 0.19f).isWithin(0.01f).of(1f - indicator.opticalProgress.value)
        }
        compose.mainClock.advanceTimeBy(220)
        compose.runOnIdle {
            assertThat(indicator.scale.value).isEqualTo(Offset(1f, 1f))
            assertThat(indicator.opticalProgress.value).isEqualTo(0f)
        }
    }

    @Test
    fun pressingDistantTabAnimatesToTheFingerInsteadOfJumping() {
        lateinit var indicator: FloatingTabIndicator
        compose.setContent {
            val scope = rememberCoroutineScope()
            indicator = remember { FloatingTabIndicator(MainTab.MY_CARD.ordinal, scope) }
            NavigationFixture(indicator)
        }
        compose.mainClock.autoAdvance = false
        val target = tab(MainTab.MY_PAGE)
        target.performTouchInput { down(center) }
        compose.runOnIdle { assertThat(indicator.position).isEqualTo(0f) }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle {
            assertThat(indicator.position).isGreaterThan(0f)
            assertThat(indicator.position).isLessThan(3f)
        }
        compose.mainClock.advanceTimeBy(220)
        compose.runOnIdle { assertThat(indicator.position).isEqualTo(3f) }
        target.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(NAV_SETTLE_MS)
        tab(MainTab.MY_CARD).assertIsSelected()
        compose.runOnIdle { assertThat(indicator.position).isEqualTo(0f) }
    }

    @Test
    fun endpointCentersStayAnchoredRegardlessOfScaleAndLayoutDirection() {
        val track = Size(400f, 56f)
        LayoutDirection.entries.forEach { direction ->
            listOf(-1f, 0f, 3f, 4f).forEach { position ->
                val idle = indicatorBounds(track, position, direction)
                listOf(1f, 1.10f, 1.20f, 1.50f).forEach { scale ->
                    val bounds = indicatorBounds(track, position, direction, widthScale = scale, heightScale = scale)
                    val centerX = (bounds.left + bounds.right) / 2f
                    assertThat(centerX).isWithin(0.01f).of((idle.left + idle.right) / 2f)
                    assertThat((bounds.top + bounds.bottom) / 2f).isWithin(0.01f).of(track.height / 2f)
                    assertThat(centerX).isAtLeast(50f)
                    assertThat(centerX).isAtMost(350f)
                }
            }
        }
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

    @Test
    fun draggingBarFollowsHorizontallyWithinFiveDpAndReturnsOnCancel() {
        lateinit var indicator: FloatingTabIndicator
        compose.setContent {
            val scope = rememberCoroutineScope()
            indicator = remember { FloatingTabIndicator(MainTab.MY_CARD.ordinal, scope) }
            NavigationFixture(indicator)
        }
        compose.mainClock.autoAdvance = false
        val target = tab(MainTab.MY_CARD)
        target.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(NAV_SETTLE_MS)
        target.performTouchInput { moveBy(Offset(width.toFloat(), 0f)) }
        compose.mainClock.advanceTimeBy(NAV_SETTLE_MS)
        compose.runOnIdle {
            val limit = with(compose.density) { 5.dp.toPx() }
            assertThat(indicator.barFollow.translation.value).isGreaterThan(0f)
            assertThat(indicator.barFollow.translation.value).isAtMost(limit)
        }
        target.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(NAV_SETTLE_MS * 2)
        compose.runOnIdle {
            assertThat(indicator.barFollow.translation.value).isEqualTo(0f)
            assertThat(indicator.dragPage).isNull()
        }
        tab(MainTab.MY_CARD).assertIsSelected()
    }

    private fun tab(tab: MainTab) = compose.onNodeWithText(tab.label)
}

@Composable
internal fun NavigationFixture(indicator: FloatingTabIndicator? = null, materialEnabled: Boolean = true) {
    MiuixTheme {
        val pager = rememberPagerState { MainTab.entries.size }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            HorizontalPager(pager, Modifier.fillMaxWidth().height(100.dp).testTag(PAGER_TAG)) { page ->
                Text(MainTab.entries[page].name)
            }
            CompositionLocalProvider(LocalGlassStyle provides GlassStyle(enabled = true, materialEnabled = materialEnabled)) {
                FloatingNavBar(pagerState = pager, indicatorState = indicator)
            }
        }
    }
}
