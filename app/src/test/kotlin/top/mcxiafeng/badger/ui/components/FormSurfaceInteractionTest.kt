package top.mcxiafeng.badger.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.text.input.ImeAction
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.basic.ColorPalette as NativeColorPalette
import top.yukonga.miuix.kmp.basic.ColorPicker as NativeColorPicker
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class FormSurfaceInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun continuousTextFieldKeepsEditingFloatingLabelAndImeCallback() {
        var value by mutableStateOf("")
        var completions = 0
        compose.setContent {
            MiuixTheme {
                TextField(value, { value = it }, label = "名称", singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { completions++ }))
            }
        }
        val input = compose.onNode(hasSetTextAction())
        input.performTextInput("示例资料")
        compose.onNodeWithText("名称").assertIsDisplayed()
        input.performImeAction()
        compose.runOnIdle {
            assertThat(value).isEqualTo("示例资料")
            assertThat(completions).isEqualTo(1)
        }
    }

    @Test
    fun continuousSearchFieldKeepsSearchAndClearBehavior() {
        var query by mutableStateOf("")
        var submitted: String? = null
        var expanded by mutableStateOf(true)
        compose.setContent {
            MiuixTheme {
                InputField(query, { query = it }, { submitted = it }, expanded, { expanded = it }, label = "搜索")
            }
        }
        val input = compose.onNode(hasSetTextAction())
        input.performTextInput("标签")
        input.performImeAction()
        compose.runOnIdle {
            assertThat(submitted).isEqualTo("标签")
            assertThat(query).isEqualTo("标签")
            assertThat(expanded).isTrue()
        }
        compose.onNodeWithContentDescription("Search Cleanup").performClick()
        compose.runOnIdle { assertThat(query).isEmpty() }
    }

    @Test
    fun continuousSwitchPreferenceTogglesExactlyOnceForRowAndThumbClicks() {
        var checked by mutableStateOf(false)
        var changes = 0
        compose.setContent {
            MiuixTheme { SwitchPreference("开关", checked, { checked = it; changes++ }) }
        }
        compose.onNodeWithText("开关").performClick()
        compose.runOnIdle { assertThat(checked).isTrue(); assertThat(changes).isEqualTo(1) }
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).performClick()
        compose.runOnIdle { assertThat(checked).isFalse(); assertThat(changes).isEqualTo(2) }
    }

    @Test
    fun continuousSnackbarKeepsTheNativeHostActionResultAndDismissal() {
        val host = SnackbarHostState()
        var result: SnackbarResult? = null
        compose.setContent {
            MiuixTheme {
                LaunchedEffect(Unit) {
                    result = host.showSnackbar("已保存", actionLabel = "撤销", duration = SnackbarDuration.Indefinite)
                }
                SnackbarHost(host, Modifier.fillMaxSize())
            }
        }
        compose.onNodeWithText("撤销").assertIsDisplayed().performClick()
        compose.runOnIdle { assertThat(result).isEqualTo(SnackbarResult.ActionPerformed) }
        compose.onNodeWithText("已保存").assertDoesNotExist()
    }

    @Test
    fun continuousPaletteKeepsTheNativeColorSelectionCoordinates() {
        var native by mutableStateOf(Color.White)
        var adapted by mutableStateOf(Color.White)
        var nativeEmitted: Color? = null
        var adaptedEmitted: Color? = null
        compose.setContent {
            MiuixTheme {
                Row {
                    Box(Modifier.weight(1f).testTag("nativePalette")) {
                        NativeColorPalette(native, { native = it; nativeEmitted = it })
                    }
                    Box(Modifier.weight(1f).testTag("adaptedPalette")) {
                        ColorPalette(adapted, { adapted = it; adaptedEmitted = it })
                    }
                }
            }
        }
        compose.onNodeWithTag("nativePalette").performTouchInput { click(Offset(width * 0.5f, height * 0.35f)) }
        compose.onNodeWithTag("adaptedPalette").performTouchInput { click(Offset(width * 0.5f, height * 0.35f)) }
        compose.runOnIdle {
            assertThat(nativeEmitted).isNotNull()
            assertThat(adaptedEmitted).isEqualTo(nativeEmitted)
            assertThat(adapted).isEqualTo(native)
        }
    }

    @Test
    fun continuousColorPickerKeepsNativeSliderCoordinatesAndCallbacks() {
        var native by mutableStateOf(Color.Red)
        var adapted by mutableStateOf(Color.Red)
        var nativeEmitted: Color? = null
        var adaptedEmitted: Color? = null
        compose.setContent {
            MiuixTheme {
                Row {
                    Box(Modifier.weight(1f).testTag("nativePicker")) {
                        NativeColorPicker(native, { native = it; nativeEmitted = it })
                    }
                    Box(Modifier.weight(1f).testTag("adaptedPicker")) {
                        ColorPicker(adapted, { adapted = it; adaptedEmitted = it })
                    }
                }
            }
        }
        compose.onNodeWithTag("nativePicker").performTouchInput {
            swipe(Offset(width * 0.3f, height * 0.29f), Offset(width * 0.7f, height * 0.29f))
        }
        compose.onNodeWithTag("adaptedPicker").performTouchInput {
            swipe(Offset(width * 0.3f, height * 0.29f), Offset(width * 0.7f, height * 0.29f))
        }
        compose.runOnIdle {
            assertThat(nativeEmitted).isNotNull()
            assertThat(adaptedEmitted).isEqualTo(nativeEmitted)
            assertThat(adapted).isEqualTo(native)
        }
    }

    @Test
    fun continuousProgressKeepsItsAccessibilityRange() {
        compose.setContent { MiuixTheme { LinearProgressIndicator(progress = { 0.6f }) } }
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,
            ProgressBarRangeInfo(0.6f, 0f..1f))).assertExists()
    }
}
