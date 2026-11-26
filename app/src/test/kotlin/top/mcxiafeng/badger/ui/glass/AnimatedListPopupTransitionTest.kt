package top.mcxiafeng.badger.ui.glass

import android.app.Application
import android.view.KeyEvent
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val EXIT_MIDPOINT_MS = 176L // Beyond the old library's 150ms release, before 239ms.
private const val INTERRUPT_MS = 64L
private const val SETTLE_MS = BadgerMotion.DURATION_TOUCH_FEEDBACK + 64L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AnimatedListPopupTransitionTest {
    @get:Rule val compose = createComposeRule()
    private val attached = mutableStateOf(true)
    private val shown = mutableStateOf(true)
    private var mounted = 0
    private var disposed = 0
    private var finished = 0
    private var requested = 0
    private var selected = 0
    private var dismissFromContent: (() -> Unit)? = null
    private var popupRoot: View? = null

    @Test
    fun removedCallerRetainsMenuUntilTheWholeExitFinishesAndFinishesOnce() {
        mount()
        compose.runOnIdle { attached.value = false }
        compose.startPopupMotionFrames()
        advancePopupTimeBy(EXIT_MIDPOINT_MS)
        compose.runOnIdle {
            assertThat(mounted).isEqualTo(1)
            assertThat(disposed).isEqualTo(0)
            assertThat(finished).isEqualTo(0)
        }
        advancePopupTimeBy(SETTLE_MS)
        assertReleasedOnce()
        advancePopupTimeBy(SETTLE_MS)
        assertReleasedOnce()
    }

    @Test
    fun closingDuringOpeningCancelsEntryAndReleasesOnlyAfterTheExit() {
        mount(finishEnter = false)
        advancePopupTimeBy(INTERRUPT_MS)
        compose.runOnIdle { shown.value = false }
        compose.startPopupMotionFrames()
        advancePopupTimeBy(EXIT_MIDPOINT_MS)
        compose.runOnIdle {
            assertThat(mounted).isEqualTo(1)
            assertThat(disposed).isEqualTo(0)
            assertThat(finished).isEqualTo(0)
        }
        advancePopupTimeBy(SETTLE_MS)
        assertReleasedOnce()
    }

    @Test
    fun reversingAnExitCancelsItsCompletionAndKeepsTheOriginalContent() {
        mount()
        compose.runOnIdle { shown.value = false }
        compose.startPopupMotionFrames()
        advancePopupTimeBy(INTERRUPT_MS)
        compose.runOnIdle { shown.value = true }
        compose.startPopupMotionFrames()
        advancePopupTimeBy(SETTLE_MS)
        compose.runOnIdle {
            assertThat(mounted).isEqualTo(1)
            assertThat(disposed).isEqualTo(0)
            assertThat(finished).isEqualTo(0)
        }
        compose.onNodeWithText("选择").assertIsDisplayed().performClick()
        compose.runOnIdle { assertThat(selected).isEqualTo(1); shown.value = false }
        compose.startPopupMotionFrames()
        advancePopupTimeBy(SETTLE_MS)
        assertReleasedOnce()
    }

    @Test
    fun closingMenuHidesActionsAndIgnoresRepeatedContentDismissal() {
        mount()
        compose.runOnIdle { shown.value = false }
        compose.startPopupMotionFrames()
        advancePopupTimeBy(INTERRUPT_MS)
        compose.onNodeWithText("选择").assertDoesNotExist()
        compose.runOnIdle {
            checkNotNull(dismissFromContent).invoke()
            assertThat(requested).isEqualTo(0)
            assertThat(disposed).isEqualTo(0)
        }
        advancePopupTimeBy(SETTLE_MS)
        assertReleasedOnce()
    }

    @Test
    fun outsideTapRequestsDismissalEvenWithoutDimmingAndDoesNotReleaseEarly() {
        mount()
        compose.onNodeWithTag("popupHost").performTouchInput { click(bottomCenter) }
        compose.startPopupMotionFrames()
        advancePopupTimeBy(EXIT_MIDPOINT_MS)
        compose.runOnIdle {
            assertThat(requested).isEqualTo(1)
            assertThat(finished).isEqualTo(0)
            assertThat(disposed).isEqualTo(0)
        }
        advancePopupTimeBy(SETTLE_MS)
        assertReleasedOnce()
    }

    @Suppress("DEPRECATION")
    @Test
    fun nativeBackRequestsTheSameRetainedExit() {
        mount()
        compose.runOnIdle {
            val root = checkNotNull(popupRoot)
            root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
        compose.startPopupMotionFrames()
        advancePopupTimeBy(EXIT_MIDPOINT_MS)
        compose.runOnIdle {
            assertThat(requested).isEqualTo(1)
            assertThat(disposed).isEqualTo(0)
            assertThat(finished).isEqualTo(0)
        }
        advancePopupTimeBy(SETTLE_MS)
        assertReleasedOnce()
    }

    @Test
    fun initiallyHiddenMenuNeverMountsOrCallsDismissFinished() {
        shown.value = false
        mount()
        advancePopupTimeBy(SETTLE_MS)
        compose.runOnIdle {
            assertThat(mounted).isEqualTo(0)
            assertThat(finished).isEqualTo(0)
        }
        compose.runOnIdle { shown.value = true }
        compose.startPopupMotionFrames()
        advancePopupTimeBy(SETTLE_MS)
        compose.onNodeWithText("选择").assertIsDisplayed()
        compose.runOnIdle {
            assertThat(mounted).isEqualTo(1)
            assertThat(disposed).isEqualTo(0)
            assertThat(finished).isEqualTo(0)
        }
    }

    @Test
    fun popupModifierAndItemsShareTransparentSurfaceWithoutChangingPageTheme() {
        var host = Color.Unspecified
        var item = Color.Unspecified
        var page = Color.Unspecified
        compose.setContent {
            MiuixTheme {
                val color = MiuixTheme.colorScheme.surfaceContainer
                SideEffect { page = color }
                RetainedPopupHost {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.size(48.dp)) {
                            AnimatedListPopup(true, popupModifier = Modifier.composed {
                                val hostColor = MiuixTheme.colorScheme.surfaceContainer
                                SideEffect { host = hostColor }
                                this
                            }) {
                                val itemColor = MiuixTheme.colorScheme.surfaceContainer
                                SideEffect { item = itemColor }
                                ListPopupColumn { Text("主题探针") }
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("主题探针").assertIsDisplayed()
        compose.runOnIdle {
            assertThat(host.alpha).isEqualTo(0f)
            assertThat(item.alpha).isEqualTo(0f)
            assertThat(page.alpha).isEqualTo(1f)
        }
    }

    @Test
    fun requestedDimensionsReachTheLibraryColumnAndItsLastItemRemainsScrollable() {
        compose.setContent {
            MiuixTheme {
                RetainedPopupHost {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.size(48.dp)) {
                            AnimatedListPopup(true, minWidth = 232.dp, maxHeight = 144.dp) {
                                Box(Modifier.testTag("menuBounds"), propagateMinConstraints = true) {
                                    ListPopupColumn {
                                        repeat(12) { index -> Text("项目$index", Modifier.height(48.dp)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("menuBounds").assertWidthIsEqualTo(232.dp).assertHeightIsEqualTo(144.dp)
        compose.onNodeWithText("项目11").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun anchorConversionAccountsForBothDialogScreenOffsetAndInsetRoot() {
        val anchor = IntRect(700, 900, 748, 948)
        val rootScreen = IntOffset(500, 300)
        val rootWindow = IntOffset(0, 24)
        val converted = listPopupAnchorInWindow(anchor, rootScreen, rootWindow)
        assertThat(converted).isEqualTo(IntRect(200, 624, 248, 672))
        // Subtracting the root's window position yields the same physical-screen delta.
        assertThat(converted.topLeft - rootWindow).isEqualTo(anchor.topLeft - rootScreen)
    }

    private fun mount(finishEnter: Boolean = true) {
        if (!finishEnter) compose.mainClock.autoAdvance = false
        compose.setContent { Fixture() }
        compose.flushPopupUi()
        compose.waitForIdle()
        compose.flushPopupUi()
        compose.mainClock.autoAdvance = false
        // Native roots need layout/position callbacks as well as the retained host's frames.
        compose.startPopupMotionFrames()
        if (!finishEnter) compose.startPopupMotionFrames()
    }

    private fun advancePopupTimeBy(durationMillis: Long) {
        compose.flushPopupUi()
        compose.mainClock.advanceTimeBy(durationMillis)
        compose.flushPopupUi()
    }

    @Composable
    private fun Fixture() {
        MiuixTheme {
            RetainedPopupHost {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.size(48.dp)) {
                        if (attached.value) {
                            AnimatedListPopup(shown.value, Modifier.testTag("popupHost"),
                                enableWindowDim = false,
                                onDismissRequest = { requested++; shown.value = false },
                                onDismissFinished = { finished++ }) {
                                DisposableEffect(Unit) {
                                    mounted++
                                    onDispose { disposed++ }
                                }
                                val dismiss = LocalDismissState.current
                                val view = LocalView.current
                                SideEffect { dismissFromContent = dismiss; popupRoot = view.rootView }
                                ListPopupColumn {
                                    Text("选择", Modifier.height(48.dp).clickable { selected++ })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun assertReleasedOnce() {
        compose.runOnIdle {
            assertThat(disposed).isEqualTo(1)
            assertThat(finished).isEqualTo(1)
        }
    }
}
