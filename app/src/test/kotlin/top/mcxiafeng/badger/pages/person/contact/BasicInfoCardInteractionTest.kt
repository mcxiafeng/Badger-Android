package top.mcxiafeng.badger.pages.person.contact

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BasicInfoCardInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun eachCellKeepsItsOwnTouchCallbackAndDisabledCellsCannotEdit() {
        val enabled = mutableStateOf(true)
        val edits = mutableListOf<String>()
        compose.setContent {
            MiuixTheme {
                BasicInfoCard(emptyList(), enabled = enabled.value) { key, _ -> edits += key }
            }
        }
        compose.onNodeWithText("性别").performTouchInput { click() }
        compose.onNodeWithText("生日").performTouchInput { click() }
        compose.runOnIdle {
            assertThat(edits).containsExactly("gender", "birthday").inOrder()
            enabled.value = false
        }
        compose.onNodeWithText("国家").performTouchInput { click() }
        compose.onNodeWithText("地区").performTouchInput { click() }
        compose.runOnIdle { assertThat(edits).containsExactly("gender", "birthday").inOrder() }
    }

    @Test
    fun cancellingAPressDoesNotOpenAnyCell() {
        val edits = mutableListOf<String>()
        compose.setContent {
            MiuixTheme { BasicInfoCard(emptyList()) { key, _ -> edits += key } }
        }
        compose.onNodeWithText("性别").performTouchInput {
            down(center)
            advanceEventTime(HOLD_DURATION_MS)
            cancel()
        }
        compose.runOnIdle { assertThat(edits).isEmpty() }
    }

    private companion object {
        const val HOLD_DURATION_MS = 800L
    }
}
