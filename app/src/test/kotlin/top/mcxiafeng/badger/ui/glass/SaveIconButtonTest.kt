package top.mcxiafeng.badger.ui.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SaveIconButtonTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun glassSaveKeepsItsWholeTouchTargetDuringPressAndRecovery() = assertStableTouchTarget(glass = true)

    @Test
    fun flatSaveKeepsItsWholeTouchTargetDuringPressAndRecovery() = assertStableTouchTarget(glass = false)

    @Test
    fun enablingSaveAfterTypingDispatchesTheLatestCallbackExactlyOnce() {
        val valid = mutableStateOf(false)
        val draft = mutableStateOf("")
        val saves = mutableListOf<String>()
        compose.setContent {
            MiuixTheme {
                CompositionLocalProvider(LocalGlassStyle provides GlassStyle(enabled = true)) {
                    val current = draft.value
                    SaveIconButton(ready = valid.value, enabled = valid.value, onClick = { saves += current })
                }
            }
        }
        val button = compose.onNodeWithContentDescription("保存")
        button.assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle {
            assertThat(saves).isEmpty()
            draft.value = "已填写标题"
            valid.value = true
        }
        button.assertIsEnabled().performTouchInput { click() }
        compose.runOnIdle { assertThat(saves).containsExactly("已填写标题") }
    }

    @Test
    fun cancellingAHeldSaveNeverDispatchesTheCallback() {
        var saves = 0
        compose.setContent { MiuixTheme { SaveIconButton(true, true, { saves++ }) } }
        val button = compose.onNodeWithContentDescription("保存")
        button.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        button.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(RECOVERY_MS)
        compose.runOnIdle { assertThat(saves).isEqualTo(0) }
    }

    private fun assertStableTouchTarget(glass: Boolean) {
        var saves = 0
        compose.setContent {
            MiuixTheme {
                CompositionLocalProvider(LocalGlassStyle provides GlassStyle(enabled = glass)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        SaveIconButton(true, true, { saves++ })
                    }
                }
            }
        }
        val button = compose.onNodeWithContentDescription("保存")
        button.assertWidthIsEqualTo(GlassButtonTarget).assertHeightIsEqualTo(GlassButtonTarget)
        val originalBounds = button.fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        button.performTouchInput { down(Offset(EDGE_INSET_PX, center.y)) }
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.runOnIdle { assertThat(button.fetchSemanticsNode().boundsInRoot).isEqualTo(originalBounds) }
        button.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.runOnIdle {
            assertThat(saves).isEqualTo(1)
            assertThat(button.fetchSemanticsNode().boundsInRoot).isEqualTo(originalBounds)
        }
        button.performTouchInput { click(Offset(EDGE_INSET_PX, center.y)) }
        compose.mainClock.advanceTimeBy(RECOVERY_MS)
        compose.runOnIdle {
            assertThat(saves).isEqualTo(2)
            assertThat(button.fetchSemanticsNode().boundsInRoot).isEqualTo(originalBounds)
        }
    }

    private companion object {
        const val EDGE_INSET_PX = 1f
        const val SETTLE_MS = BadgerMotion.DURATION_FAST + 48L
        const val RECOVERY_MS = BadgerMotion.DURATION_FAST * 2 + 64L
    }
}
