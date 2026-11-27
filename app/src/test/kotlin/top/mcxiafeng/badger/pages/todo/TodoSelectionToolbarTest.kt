package top.mcxiafeng.badger.pages.todo

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.ui.glass.RetainedPopupHost
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TodoSelectionToolbarTest {
    @get:Rule val compose = createComposeRule()
    private val state = mutableStateOf(TodoUiState(selecting = true))
    private var deletes = 0

    @Test fun deleteOnlyAppearsAfterSelectionAndRequiresExplicitConfirmation() {
        mount()
        compose.onAllNodesWithContentDescription("删除1项待办").assertCountEquals(0)
        compose.runOnIdle { state.value = state.value.copy(selectedIds = setOf("a")) }
        compose.onNodeWithContentDescription("删除1项待办").assertIsDisplayed().performClick()
        compose.onNodeWithText("删除后无法恢复，关联的名片不受影响。").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertThat(deletes).isEqualTo(0) }
        compose.onNodeWithContentDescription("删除1项待办").performClick()
        compose.onNodeWithText("删除").performClick()
        compose.runOnIdle { assertThat(deletes).isEqualTo(1) }
        compose.onAllNodesWithText("删除 1 项待办？").assertCountEquals(0)
    }

    @Test fun deleteButtonLabelTracksTheSelectionCount() {
        mount()
        compose.runOnIdle { state.value = state.value.copy(selectedIds = setOf("a", "b", "c")) }
        compose.onNodeWithContentDescription("删除3项待办").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(selectedIds = setOf("a")) }
        compose.onAllNodesWithContentDescription("删除3项待办").assertCountEquals(0)
        compose.onNodeWithContentDescription("删除1项待办").assertIsDisplayed()
        compose.runOnIdle { assertThat(deletes).isEqualTo(0) }
    }

    @Test fun leavingSelectionClearsAConfirmationBeforeSelectingAgain() {
        mount()
        compose.runOnIdle { state.value = state.value.copy(selectedIds = setOf("a")) }
        compose.onNodeWithContentDescription("删除1项待办").performClick()
        compose.runOnIdle { state.value = TodoUiState(selecting = false) }
        compose.onAllNodesWithText("删除 1 项待办？").assertCountEquals(0)
        compose.runOnIdle { state.value = TodoUiState(selecting = true, selectedIds = setOf("b")) }
        compose.onNodeWithContentDescription("删除1项待办").assertIsDisplayed()
        compose.onAllNodesWithText("删除 1 项待办？").assertCountEquals(0)
        compose.runOnIdle { assertThat(deletes).isEqualTo(0) }
    }

    private fun mount() {
        compose.setContent {
            MiuixTheme {
                RetainedPopupHost {
                    Box(Modifier.fillMaxSize()) { TodoTopDeleteAction(state.value) { deletes++ } }
                }
            }
        }
    }
}
