package top.mcxiafeng.badger.pages.todo

import android.app.Application
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.todo.LocalTodo
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TodoRowInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val viewModel = mockk<TodoViewModel>(relaxed = true)
    private val edits = mutableListOf<String>()
    private val row = TodoRowState(LocalTodo(id = "todo", title = "联系圈友"),
        "联系圈友", "明天 · 工作室", null, emptyList(), 0)

    @Test
    fun realTouchesKeepEditAndCompletionIndependent() {
        mount()
        compose.onNodeWithText(row.title).performTouchInput { click() }
        compose.onNodeWithContentDescription("完成待办").performTouchInput { click() }
        compose.runOnIdle {
            assertThat(edits).containsExactly(row.item.id)
            verify(exactly = 1) { viewModel.toggle(row.item.id) }
        }
    }

    @Test
    fun holdingThenCancellingTheTextDoesNotCompleteOrOpenTheTodo() {
        mount()
        val restingBounds = compose.onNodeWithTag(ROW_TAG).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText(row.title).performTouchInput {
            down(center)
            advanceEventTime(HOLD_DURATION_MS)
        }
        assertThat(compose.onNodeWithTag(ROW_TAG).fetchSemanticsNode().boundsInRoot).isEqualTo(restingBounds)
        compose.runOnIdle {
            assertThat(edits).isEmpty()
            verify(exactly = 0) { viewModel.toggle(any()) }
        }
        compose.onNodeWithText(row.title).performTouchInput { cancel() }
        compose.runOnIdle { assertThat(edits).isEmpty() }
    }

    @Test
    fun disabledRowCannotEditOrCompleteThroughTouch() {
        mount(enabled = false)
        compose.onNodeWithText(row.title).performTouchInput { click() }
        compose.onNodeWithContentDescription("完成待办").performTouchInput { click() }
        compose.runOnIdle {
            assertThat(edits).isEmpty()
            verify(exactly = 0) { viewModel.toggle(any()) }
        }
    }

    @Test
    fun sortingKeepsAccessibleReorderActionsWhileEditingAndCompletionStayDisabled() {
        var moves = 0
        mount(enabled = false, actions = listOf(CustomAccessibilityAction("上移待办") { moves++; true }))
        val actions = compose.onNodeWithContentDescription("拖动排序 ${row.item.title}")
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle {
            assertThat(actions.single().label).isEqualTo("上移待办")
            assertThat(actions.single().action()).isTrue()
        }
        compose.onNodeWithTag(ROW_TAG).performTouchInput { click() }
        compose.runOnIdle {
            assertThat(moves).isEqualTo(1)
            assertThat(edits).isEmpty()
            verify(exactly = 0) { viewModel.toggle(any()) }
        }
    }

    private fun mount(enabled: Boolean = true, actions: List<CustomAccessibilityAction>? = null) {
        compose.setContent {
            MiuixTheme {
                TodoRow(row, enabled, viewModel, { edits += it }, Modifier.testTag(ROW_TAG), actions)
            }
        }
    }

    private companion object {
        const val ROW_TAG = "todo_row"
        const val HOLD_DURATION_MS = 800L
    }
}
