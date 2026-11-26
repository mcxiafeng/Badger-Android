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

private const val PRESS_SETTLE_MS = BadgerMotion.DURATION_TOUCH_FEEDBACK + 48L
private const val RELEASE_SETTLE_MS = BadgerMotion.DURATION_TOUCH_FEEDBACK * 2 + 64L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class GlassTouchFeedbackTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun buttonAndItemUse115Then90Then100With239msForEachLeg() {
        val feedback = mountFeedback()
        compose.runOnIdle { feedback.press() }
        compose.mainClock.advanceTimeBy(128)
        compose.runOnIdle {
            assertThat(feedback.scale.value).isGreaterThan(1.09f)
            assertThat(feedback.scale.value).isLessThan(1.15f)
        }
        compose.mainClock.advanceTimeBy(PRESS_SETTLE_MS)
        compose.runOnIdle {
            assertThat(feedback.scale.value).isEqualTo(1.15f)
            feedback.release()
        }
        assertTwoReleaseLegs(feedback, trough = 0.90f, peak = 1.15f)
    }

    @Test
    fun barUses105Then95Then100With239msForEachLeg() {
        val feedback = mountFeedback(pressed = 1.05f, trough = 0.95f)
        compose.runOnIdle { feedback.press() }
        compose.mainClock.advanceTimeBy(PRESS_SETTLE_MS)
        compose.runOnIdle {
            assertThat(feedback.scale.value).isEqualTo(1.05f)
            feedback.release()
        }
        assertTwoReleaseLegs(feedback, trough = 0.95f, peak = 1.05f)
    }

    @Test
    fun rapidRepressCancelsTheOldRecoveryWithoutSnappingOrLeavingStaleScale() {
        val feedback = mountFeedback()
        compose.runOnIdle { feedback.press() }
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle { feedback.release() }
        compose.mainClock.advanceTimeBy(320)
        compose.runOnIdle {
            val current = feedback.scale.value
            feedback.press()
            assertThat(feedback.scale.value).isEqualTo(current)
        }
        compose.mainClock.advanceTimeBy(PRESS_SETTLE_MS)
        compose.runOnIdle {
            assertThat(feedback.scale.value).isEqualTo(1.15f)
            feedback.release()
            feedback.release()
        }
        compose.mainClock.advanceTimeBy(RELEASE_SETTLE_MS)
        compose.runOnIdle { assertThat(feedback.scale.value).isEqualTo(1f) }
    }

    @Test
    fun shortTapStillGetsTwoFullReleaseLegs() {
        val feedback = mountFeedback()
        compose.runOnIdle { feedback.press() }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { feedback.release() }
        assertTwoReleaseLegs(feedback, trough = 0.90f, peak = 1f)
    }

    @Test
    fun timedIndicatorRecoveryKeepsItsSpringOvershootAndExactEndpoints() {
        val recovery = timedSpringRecoveryEasing(dampingRatio = 0.55f, stiffness = 300f)
        assertThat(recovery.transform(0f)).isEqualTo(0f)
        assertThat(recovery.transform(1f)).isEqualTo(1f)
        assertThat((0..100).map { recovery.transform(it / 100f) }.any { it > 1f }).isTrue()
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
            assertThat(item.fetchSemanticsNode().boundsInRoot.width / originalItem.width).isWithin(0.001f).of(1.15f)
            assertThat(child.fetchSemanticsNode().boundsInRoot.width / originalChild.width).isWithin(0.001f).of(1.15f)
        }
        child.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(RELEASE_SETTLE_MS)
        compose.runOnIdle {
            assertThat(clicks).isEqualTo(0)
            assertThat(item.fetchSemanticsNode().boundsInRoot).isEqualTo(originalItem)
        }
    }

    private fun assertTwoReleaseLegs(feedback: TouchScaleFeedback, trough: Float, peak: Float) {
        compose.mainClock.advanceTimeBy(128)
        compose.runOnIdle {
            assertThat(feedback.scale.value).isGreaterThan(trough)
            assertThat(feedback.scale.value).isLessThan(peak)
        }
        compose.mainClock.advanceTimeBy(128)
        compose.runOnIdle { assertThat(feedback.scale.value).isWithin(0.002f).of(trough) }
        compose.mainClock.advanceTimeBy(128)
        compose.runOnIdle {
            assertThat(feedback.scale.value).isGreaterThan(trough)
            assertThat(feedback.scale.value).isLessThan(1f)
        }
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle {
            assertThat(feedback.scale.value).isEqualTo(1f)
            assertThat(feedback.scale.isRunning).isFalse()
        }
    }

    private fun mountFeedback(pressed: Float = 1.15f, trough: Float = 0.90f): TouchScaleFeedback {
        lateinit var feedback: TouchScaleFeedback
        compose.setContent {
            val scope = rememberCoroutineScope()
            feedback = remember(scope) { TouchScaleFeedback(scope, pressed, trough) }
        }
        compose.mainClock.autoAdvance = false
        return feedback
    }
}
