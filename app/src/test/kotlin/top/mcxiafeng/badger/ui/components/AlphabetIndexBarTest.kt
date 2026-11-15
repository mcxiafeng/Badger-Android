package top.mcxiafeng.badger.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AlphabetIndexBarTest {
    @get:Rule val composeRule = createComposeRule()
    private val selections = mutableListOf<Pair<String, LetterIndexInteraction>>()
    private var horizontalMoves = 0
    private var neighborClicks = 0

    @Test
    fun compactBandUsesTheOriginalContactWidthAndMapsTapToTheVisibleMiddleLetter() {
        mount()
        val bounds = rail().fetchSemanticsNode().boundsInRoot
        assertThat(bounds.width).isWithin(1f).of(with(composeRule.density) { 28.dp.toPx() })
        assertThat(bounds.height).isLessThan(with(composeRule.density) { 150.dp.toPx() })
        rail().performTouchInput { click(center) }
        composeRule.runOnIdle {
            assertThat(selections).containsExactly("B" to LetterIndexInteraction.Tap)
        }
    }

    @Test
    fun previewKeepsACompactSquareInsteadOfDominatingTheSidebar() {
        assertPreviewSquare()
    }

    @Test
    fun unavailableLetterCaptionDoesNotStretchThePreviewFrame() {
        assertPreviewSquare(enabled = setOf("A", "C"))
    }

    @Test
    fun previewPreservesItsAspectRatioAtDoubleFontScale() {
        assertPreviewSquare(fontScale = 2f)
    }

    @Test
    fun touchToleranceDoesNotRequireAWideVisualRail() {
        mount()
        rail().performTouchInput { click(Offset(-8f, center.y)) }
        composeRule.runOnIdle { assertThat(selections).containsExactly("B" to LetterIndexInteraction.Tap) }
    }

    @Test
    fun expandedTouchAreaDoesNotStealClicksFromAnActualNeighbor() {
        mount(withNeighbor = true)
        rail().performTouchInput { click(Offset(-8f, center.y)) }
        composeRule.runOnIdle {
            assertThat(selections).isEmpty()
            assertThat(neighborClicks).isEqualTo(1)
        }
    }

    @Test
    fun verticalDragUsesImmediateNavigationAndVisitsTheLastLetter() {
        mount()
        rail().performTouchInput {
            swipe(Offset(center.x, 1f), Offset(center.x, height - 1f), durationMillis = 400)
        }
        composeRule.runOnIdle {
            assertThat(selections).isNotEmpty()
            assertThat(selections.last()).isEqualTo("C" to LetterIndexInteraction.Drag)
            assertThat(selections.all { it.second == LetterIndexInteraction.Drag }).isTrue()
            assertThat(selections.map { it.first }.distinct()).isEqualTo(selections.map { it.first })
        }
    }

    @Test
    fun horizontalMovementRemainsAvailableToTheParentPager() {
        mount()
        rail().performTouchInput {
            swipe(center, Offset(-100f, center.y), durationMillis = 400)
        }
        composeRule.runOnIdle {
            assertThat(selections).isEmpty()
            assertThat(horizontalMoves).isGreaterThan(0)
        }
    }

    @Test
    fun cancellingADragRemovesThePreviewImmediately() {
        mount()
        rail().performTouchInput {
            down(Offset(center.x, 1f))
            moveBy(Offset(0f, height / 2f))
            advanceEventTime(100)
            cancel()
        }
        composeRule.onNodeWithTag(PREVIEW_TAG, useUnmergedTree = true).assertDoesNotExist()
        composeRule.runOnIdle {
            assertThat(selections).isNotEmpty()
            assertThat(selections.all { it.second == LetterIndexInteraction.Drag }).isTrue()
        }
    }

    @Test
    fun aNewTapKeepsItsPreviewUntilItsOwnDeadline() {
        mount()
        composeRule.mainClock.autoAdvance = false
        rail().performTouchInput { click(Offset(center.x, 1f)) }
        composeRule.mainClock.advanceTimeBy(BadgerMotion.DURATION_FAST.toLong())
        rail().performTouchInput { click(Offset(center.x, height - 1f)) }
        composeRule.mainClock.advanceTimeBy(BadgerMotion.DURATION_FAST.toLong())
        composeRule.onNodeWithTag(PREVIEW_TAG, useUnmergedTree = true).assertExists()
        composeRule.mainClock.advanceTimeBy(BadgerMotion.DURATION_BASE.toLong() + FRAME_MS)
        composeRule.onNodeWithTag(PREVIEW_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun unavailableLettersDoNotNavigateAndAccessibilitySkipsThem() {
        mount(enabled = setOf("A", "C"))
        rail().performTouchInput { click(center) }
        composeRule.runOnIdle { assertThat(selections).isEmpty() }
        val actions = rail().fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertThat(actions.map { it.label }).containsExactly("定位到 A", "定位到 C")
        rail().performSemanticsAction(SemanticsActions.SetProgress) { set -> check(set(1f)) }
        composeRule.runOnIdle {
            assertThat(selections.last()).isEqualTo("C" to LetterIndexInteraction.Tap)
        }
    }

    @Test
    fun fullAlphabetFitsLargeFontsAndStillProvidesEveryAccessibleDestination() {
        val letters = listOf("⭐", "#") + ('A'..'Z').map(Char::toString)
        mount(letters = letters, enabled = letters.toSet(), fontScale = 2f)
        val bounds = rail().fetchSemanticsNode().boundsInRoot
        assertThat(bounds.height).isAtMost(with(composeRule.density) { 400.dp.toPx() })
        val actions = rail().fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertThat(actions).hasSize(letters.size)
        composeRule.runOnIdle { check(actions.last().action()) }
        composeRule.runOnIdle { assertThat(selections.last().first).isEqualTo("Z") }
    }

    @Test
    fun denseRailUsesEqualPixelRowsAtFractionalDensity() {
        val letters = listOf("⭐", "#") + ('A'..'Z').map(Char::toString)
        mount(letters = letters, enabled = letters.toSet(), fontScale = 2f, densityOverride = 2.625f)
        val bounds = rail().fetchSemanticsNode().boundsInRoot
        assertThat(bounds.height.toInt() % letters.size).isEqualTo(0)
        rail().performTouchInput {
            click(Offset(center.x, height - height.toFloat() / letters.size / 2))
        }
        composeRule.runOnIdle { assertThat(selections.last().first).isEqualTo("Z") }
    }

    @Test
    fun emptyAlphabetDoesNotCreateAnInteractiveControl() {
        mount(letters = emptyList(), enabled = emptySet())
        rail().assertDoesNotExist()
    }

    private fun assertPreviewSquare(enabled: Set<String> = setOf("A", "B", "C"), fontScale: Float = 1f) {
        mount(enabled = enabled, fontScale = fontScale)
        rail().performTouchInput {
            down(Offset(center.x, 1f))
            moveBy(Offset(0f, height / 2f))
        }
        val preview = composeRule.onNodeWithTag(PREVIEW_TAG, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val expected = with(composeRule.density) { (64.dp * fontScale).toPx() }
        assertThat(preview.width).isWithin(1f).of(expected)
        assertThat(preview.height).isWithin(1f).of(expected)
        rail().performTouchInput { up() }
    }

    private fun rail() = composeRule.onNodeWithContentDescription(DESCRIPTION)

    private fun mount(
        letters: List<String> = listOf("A", "B", "C"),
        enabled: Set<String> = letters.toSet(), fontScale: Float = 1f,
        withNeighbor: Boolean = false, densityOverride: Float? = null
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(densityOverride ?: density.density, fontScale)) {
                MiuixTheme {
                    var selected by remember { mutableStateOf<String?>(letters.firstOrNull()) }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(Modifier.width(200.dp).height(400.dp).pointerInput(Unit) {
                            detectHorizontalDragGestures { change, _ ->
                                change.consume()
                                horizontalMoves++
                            }
                        }, contentAlignment = Alignment.CenterEnd) {
                            if (withNeighbor) {
                                Box(Modifier.fillMaxSize().clickable { neighborClicks++ })
                            }
                            AlphabetIndexBar(letters, selected, modifier = Modifier.fillMaxHeight(),
                                enabledLetters = enabled, description = DESCRIPTION,
                                onSelectLetter = { letter, interaction ->
                                    selected = letter
                                    selections += letter to interaction
                                })
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val DESCRIPTION = "测试字母索引"
        const val PREVIEW_TAG = "alphabet_index_preview"
        const val FRAME_MS = 16L
    }
}
