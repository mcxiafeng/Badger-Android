package top.mcxiafeng.badger.ui.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.designsystem.BadgerTouchFeedback

private const val PRESS_SETTLE_MS = BadgerMotion.DURATION_FAST + 48L
private const val RELEASE_SETTLE_MS = BadgerMotion.DURATION_FAST + 64L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class GlassTouchFeedbackTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun buttonShrinksTo97PercentIn150msAndReleaseReturnsInASingleLeg() {
        val feedback = mountFeedback()
        compose.runOnIdle { feedback.press() }
        compose.mainClock.advanceTimeBy(75)
        compose.runOnIdle {
            assertThat(feedback.scale.value).isGreaterThan(BadgerTouchFeedback.PRESSED_SCALE)
            assertThat(feedback.scale.value).isLessThan(1f)
        }
        compose.mainClock.advanceTimeBy(PRESS_SETTLE_MS)
        compose.runOnIdle {
            assertThat(feedback.scale.value).isWithin(0.001f).of(BadgerTouchFeedback.PRESSED_SCALE)
            feedback.release()
        }
        assertSingleReleaseLeg(feedback, from = BadgerTouchFeedback.PRESSED_SCALE)
    }

    @Test
    fun barShrinksTo98PercentIn150msAndReleaseAlsoUsesOneLeg() {
        val feedback = mountFeedback(BadgerTouchFeedback.NAV_PRESSED_SCALE)
        compose.runOnIdle { feedback.press() }
        compose.mainClock.advanceTimeBy(PRESS_SETTLE_MS)
        compose.runOnIdle {
            assertThat(feedback.scale.value).isWithin(0.001f).of(BadgerTouchFeedback.NAV_PRESSED_SCALE)
            feedback.release()
        }
        assertSingleReleaseLeg(feedback, from = BadgerTouchFeedback.NAV_PRESSED_SCALE)
    }

    @Test
    fun rapidRepressCancelsTheOldRecoveryWithoutSnappingOrLeavingStaleScale() {
        val feedback = mountFeedback()
        compose.runOnIdle { feedback.press() }
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle { feedback.release() }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle {
            val current = feedback.scale.value
            feedback.press()
            assertThat(feedback.scale.value).isEqualTo(current)
        }
        compose.mainClock.advanceTimeBy(PRESS_SETTLE_MS)
        compose.runOnIdle {
            assertThat(feedback.scale.value).isWithin(0.001f).of(BadgerTouchFeedback.PRESSED_SCALE)
            feedback.release()
            feedback.release()
        }
        compose.mainClock.advanceTimeBy(RELEASE_SETTLE_MS)
        compose.runOnIdle { assertThat(feedback.scale.value).isEqualTo(1f) }
    }

    @Test
    fun shortTapStillReleasesInOneLegWithoutAnyTrough() {
        val feedback = mountFeedback()
        compose.runOnIdle { feedback.press() }
        compose.mainClock.advanceTimeBy(32)
        var from = 1f
        compose.runOnIdle {
            from = feedback.scale.value
            feedback.release()
        }
        assertSingleReleaseLeg(feedback, from)
    }

    @Test
    fun compoundItemOwnsTheScaleInsteadOfMultiplyingItsChildButtonScale() {
        var clicks = 0
        compose.setContent {
            MiuixTheme {
                CompositionLocalProvider(LocalGlassStyle provides GlassStyle(enabled = true)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(160.dp, 80.dp).itemPressFeedback(true, 12.dp).testTag("itemOwner"),
                            contentAlignment = Alignment.Center) {
                            ItemPressFeedbackScope(true) {
                                IconButton(onClick = { clicks++ }, modifier = Modifier.testTag("itemChild")) { Text("操作") }
                            }
                        }
                    }
                }
            }
        }
        val item = compose.onNodeWithTag("itemOwner")
        val child = compose.onNodeWithTag("itemChild")
        val originalItem = item.fetchSemanticsNode().boundsInRoot
        val originalChild = child.fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        child.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(PRESS_SETTLE_MS)
        compose.runOnIdle {
            assertThat(item.fetchSemanticsNode().boundsInRoot.width / originalItem.width)
                .isWithin(0.001f).of(BadgerTouchFeedback.PRESSED_SCALE)
            assertThat(child.fetchSemanticsNode().boundsInRoot.width / originalChild.width)
                .isWithin(0.001f).of(BadgerTouchFeedback.PRESSED_SCALE)
        }
        child.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(RELEASE_SETTLE_MS)
        compose.runOnIdle {
            assertThat(clicks).isEqualTo(0)
            assertThat(item.fetchSemanticsNode().boundsInRoot).isEqualTo(originalItem)
        }
    }

    /** 松手单段 150ms 回到 1.0，中途值只升不降，绝不出现低于按压值的谷底。 */
    private fun assertSingleReleaseLeg(feedback: TouchScaleFeedback, from: Float) {
        compose.mainClock.advanceTimeBy(75)
        compose.runOnIdle {
            assertThat(feedback.scale.value).isGreaterThan(from)
            assertThat(feedback.scale.value).isLessThan(1f)
            assertThat(feedback.scale.value).isAtLeast(BadgerTouchFeedback.PRESSED_SCALE)
        }
        compose.mainClock.advanceTimeBy(RELEASE_SETTLE_MS)
        compose.runOnIdle {
            assertThat(feedback.scale.value).isEqualTo(1f)
            assertThat(feedback.scale.isRunning).isFalse()
        }
    }

    private fun mountFeedback(pressedScale: Float = BadgerTouchFeedback.PRESSED_SCALE): TouchScaleFeedback {
        lateinit var feedback: TouchScaleFeedback
        compose.setContent {
            val scope = rememberCoroutineScope()
            feedback = remember(scope) { TouchScaleFeedback(scope, pressedScale) }
        }
        compose.mainClock.autoAdvance = false
        return feedback
    }
}
