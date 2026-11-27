package top.mcxiafeng.badger.pages.todo

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.todo.LocalTodo
import top.mcxiafeng.badger.ui.glass.RetainedPopupHost
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TodoPageInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val viewModel = mockk<TodoViewModel>(relaxed = true)
    private val row = TodoRowState(LocalTodo(id = "a", title = "待选择事项"), "待选择事项", "", null, emptyList(), 0)
    private val state = MutableStateFlow(TodoUiState(loaded = true, loading = false, items = listOf(row.item), pending = listOf(row)))
    private val edits = mutableListOf<String>()
    private var manages = 0
    private var back: OnBackPressedDispatcher? = null

    @Test fun moreSelectionWiresRowsDeletionVisibilityAndSystemBack() {
        every { viewModel.beginSelection() } answers { state.value = state.value.copy(selecting = true) }
        every { viewModel.toggleSelection("a") } answers { state.value = state.value.copy(selectedIds = setOf("a")) }
        every { viewModel.exitSelection() } answers { state.value = state.value.copy(selecting = false, selectedIds = emptySet()) }
        mount()
        compose.onNodeWithContentDescription("更多待办操作").performClick()
        compose.onNodeWithText("多选").performClick()
        compose.onNodeWithText("已选择 0 项").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("删除1项待办").assertCountEquals(0)
        compose.onNodeWithContentDescription("选择待办 待选择事项").performClick()
        compose.onNodeWithText("已选择 1 项").assertIsDisplayed()
        compose.onNodeWithContentDescription("删除1项待办").assertIsDisplayed()
        compose.runOnIdle { checkNotNull(back).onBackPressed() }
        compose.onNodeWithContentDescription("更多待办操作").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("删除1项待办").assertCountEquals(0)
        compose.runOnIdle {
            verify(exactly = 1) { viewModel.beginSelection(); viewModel.toggleSelection("a"); viewModel.exitSelection() }
            verify(exactly = 0) { viewModel.toggle(any()) }
            assertThat(edits).isEmpty()
        }
    }

    @Test fun circularCancelButtonExitsTheCurrentModeFromTheTopBar() {
        every { viewModel.beginSelection() } answers { state.value = state.value.copy(selecting = true) }
        every { viewModel.exitSelection() } answers { state.value = state.value.copy(selecting = false, selectedIds = emptySet()) }
        mount()
        compose.onNodeWithContentDescription("更多待办操作").performClick()
        compose.onNodeWithText("多选").performClick()
        compose.onNodeWithText("已选择 0 项").assertIsDisplayed()
        compose.onNodeWithContentDescription("取消").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("更多待办操作").assertIsDisplayed()
        compose.runOnIdle {
            verify(exactly = 1) { viewModel.beginSelection(); viewModel.exitSelection() }
        }
    }

    @Test fun searchCreateAndTypeManagementRetainTheirExistingActions() {
        mount()
        compose.onNodeWithContentDescription("搜索待办").performClick()
        compose.onNodeWithText("搜索标题、说明、地点或类型").assertIsDisplayed()
        compose.onNodeWithContentDescription("新建待办").performClick()
        compose.onNodeWithContentDescription("更多待办操作").performClick()
        compose.onNodeWithText("待办类型管理").performClick()
        compose.runOnIdle {
            assertThat(edits).containsExactly("")
            assertThat(manages).isEqualTo(1)
        }
    }

    private fun mount() {
        every { viewModel.uiState } returns state
        compose.setContent {
            MiuixTheme {
                RetainedPopupHost {
                    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                    SideEffect { back = dispatcher }
                    TodoRoute({ edits += it }, { manages++ }, isActive = true, viewModel = viewModel)
                }
            }
        }
    }
}
