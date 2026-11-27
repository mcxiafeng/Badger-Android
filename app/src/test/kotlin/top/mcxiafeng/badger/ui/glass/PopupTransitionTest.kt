package top.mcxiafeng.badger.ui.glass

import android.app.Application
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PopupTransitionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun bothDirectionsUse239msMonotonicCurvesWithExactEndpoints() {
        assertThat(popupMotionSpec(true).durationMillis).isEqualTo(239)
        assertThat(popupMotionSpec(false).durationMillis).isEqualTo(239)
        listOf(PopupEnterEasing, PopupExitEasing).forEach { easing ->
            assertThat(easing.transform(0f)).isEqualTo(0f)
            assertThat(easing.transform(1f)).isEqualTo(1f)
            // 曲线必须单调且限制在 0..1：过冲回摆=果冻感，已按动效重置否决。
            var previous = 0f
            for (i in 0..100) {
                val value = easing.transform(i / 100f)
                assertWithMessage("curve value at $i%").that(value).isAtLeast(0f)
                assertWithMessage("curve value at $i%").that(value).isAtMost(1f)
                assertWithMessage("curve monotonic at $i%").that(value).isAtLeast(previous)
                previous = value
            }
        }
    }

    @Test
    fun conditionallyRemovedWindowKeepsItsCompositionUntilExitAndFinishesExactlyOnce() {
        val open = mutableStateOf(true)
        var mounted = 0
        var disposed = 0
        var finished = 0
        compose.setContent {
            MiuixTheme {
                RetainedPopupHost {
                    if (open.value) WindowDialog(show = true, title = "保留到退出完成",
                        onDismissRequest = { open.value = false }, onDismissFinished = { finished++ }) {
                        DisposableEffect(Unit) { mounted++; onDispose { disposed++ } }
                        Text("窗口内容")
                    }
                }
            }
        }
        compose.onNodeWithText("窗口内容").assertExists()
        compose.runOnIdle { assertThat(mounted).isEqualTo(1) }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { open.value = false }
        compose.startPopupMotionFrames()
        compose.mainClock.advanceTimeBy(128)
        compose.runOnIdle {
            assertThat(disposed).isEqualTo(0)
            assertThat(finished).isEqualTo(0)
        }
        compose.mainClock.advanceTimeBy(128)
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            assertWithMessage(ShadowLog.getLogsForTag("PopupTransitionTester").joinToString { it.msg }).that(disposed).isEqualTo(1)
            assertThat(finished).isEqualTo(1)
        }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertThat(finished).isEqualTo(1) }
    }

    @Test
    fun nestedPopupStartsClosingWithItsParentInsteadOfWaitingAnotherExitDuration() {
        val open = mutableStateOf(true)
        lateinit var childMotion: PopupMotionState
        var finished = 0
        compose.setContent {
            MiuixTheme {
                RetainedPopupHost {
                    if (open.value) RetainedPopup(true, { finished++ }) { parent ->
                        PopupWindow(parent, { open.value = false }) {
                            Text("父弹层")
                            RetainedPopup(true, { finished++ }) { child ->
                                childMotion = child
                                PopupWindow(child, {}) { Text("子弹层") }
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { open.value = false }
        compose.startPopupMotionFrames()
        compose.mainClock.advanceTimeBy(128)
        compose.runOnIdle {
            assertThat(childMotion.visible).isFalse()
            assertThat(finished).isEqualTo(0)
        }
        compose.mainClock.advanceTimeBy(240)
        compose.runOnIdle { assertThat(finished).isEqualTo(2) }
    }

    @Test
    fun reopeningDuringExitCancelsTheOldFinishAndRetainsTheSameWindowContent() {
        val open = mutableStateOf(true)
        lateinit var motion: PopupMotionState
        var disposed = 0
        var finished = 0
        compose.setContent {
            MiuixTheme {
                RetainedPopupHost {
                    RetainedPopup(open.value, onDismissFinished = { finished++ }) { state ->
                        motion = state
                        PopupWindow(state, onDismissRequest = { open.value = false }) {
                            DisposableEffect(Unit) { onDispose { disposed++ } }
                            Text("快速重开")
                        }
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { open.value = false }
        compose.startPopupMotionFrames()
        compose.mainClock.advanceTimeBy(128)
        compose.runOnIdle {
            assertThat(motion.progress.value).isGreaterThan(0f)
            open.value = true
        }
        compose.startPopupMotionFrames()
        compose.mainClock.advanceTimeBy(320)
        compose.runOnIdle {
            assertThat(motion.progress.value).isEqualTo(1f)
            assertThat(finished).isEqualTo(0)
            assertThat(disposed).isEqualTo(0)
            open.value = false
        }
        compose.startPopupMotionFrames()
        compose.mainClock.advanceTimeBy(320)
        compose.runOnIdle { assertThat(finished).isEqualTo(1) }
    }

}
