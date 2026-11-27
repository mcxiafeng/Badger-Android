package top.mcxiafeng.badger.pages.todo

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
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
import top.mcxiafeng.badger.data.todo.TodoType
import top.mcxiafeng.badger.ui.glass.RetainedPopupHost
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TodoTypeManagerPageTest {
    @get:Rule val compose = createComposeRule()
    private val viewModel = mockk<TodoViewModel>(relaxed = true)
    private val first = TodoType.custom("返图", TodoType.presets.first().color)
    private val second = TodoType.custom("活动", TodoType.presets[1].color)
    private val state = MutableStateFlow(
        TodoUiState(loaded = true, loading = false, types = TodoType.presets + first + second)
    )
    private var backs = 0
    private var back: OnBackPressedDispatcher? = null

    @Test fun toolbarShowsAddThenSelectAndExistingRowDeleteButtonsAreGone() {
        mount()
        compose.onNodeWithContentDescription("新建类型").assertIsDisplayed()
        compose.onNodeWithContentDescription("选择类型").assertIsDisplayed()
        // The per-row delete entry is gone: no delete affordance before selection mode.
        compose.onAllNodesWithContentDescription("删除").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("删除${first.name}").assertCountEquals(0)
    }

    @Test fun tappingACustomRowStillOpensTheEditorDialog() {
        mount()
        compose.onNodeWithText(first.name).performClick()
        compose.onNodeWithText("编辑类型").assertIsDisplayed()
        compose.onNodeWithText("类型名称").assertIsDisplayed()
    }

    @Test fun selectEntersCheckboxModeCountsRowsAndCancelsWithoutDeleting() {
        mount()
        compose.onNodeWithContentDescription("选择类型").performClick()
        compose.onNodeWithText("已选择 0 项").assertIsDisplayed()
        compose.onNodeWithText(first.name).performClick()
        compose.onNodeWithText(second.name).performClick()
        compose.onNodeWithText("已选择 2 项").assertIsDisplayed()
        compose.onNodeWithText(second.name).performClick()
        compose.onNodeWithText("已选择 1 项").assertIsDisplayed()
        // Preset rows stay read-only in selection mode.
        compose.onNodeWithText("红色").performClick()
        compose.onNodeWithText("已选择 1 项").assertIsDisplayed()
        compose.onNodeWithContentDescription("取消").performClick()
        compose.onNodeWithContentDescription("选择类型").assertIsDisplayed()
        compose.onAllNodesWithText("已选择 1 项").assertCountEquals(0)
        compose.runOnIdle { verify(exactly = 0) { viewModel.deleteTypes(any(), any()) } }
    }

    @Test fun systemBackLeavesSelectionModeBeforeThePage() {
        mount()
        compose.onNodeWithContentDescription("选择类型").performClick()
        compose.onNodeWithText("已选择 0 项").assertIsDisplayed()
        compose.runOnIdle { checkNotNull(back).onBackPressed() }
        compose.onNodeWithContentDescription("选择类型").assertIsDisplayed()
        compose.runOnIdle { assertThat(backs).isEqualTo(0) }
    }

    @Test fun deleteNeedsConfirmationAndDispatchesTheSelectedTypes() {
        mount()
        compose.onNodeWithContentDescription("选择类型").performClick()
        compose.onAllNodesWithText("删除 2 项类型？").assertCountEquals(0)
        compose.onNodeWithText(first.name).performClick()
        compose.onNodeWithText(second.name).performClick()
        compose.onNodeWithContentDescription("删除2项类型").performClick()
        compose.onNodeWithText("删除 2 项类型？").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { verify(exactly = 0) { viewModel.deleteTypes(any(), any()) } }
        compose.onNodeWithContentDescription("删除2项类型").performClick()
        compose.onNodeWithText("删除").performClick()
        compose.runOnIdle {
            verify(exactly = 1) { viewModel.deleteTypes(listOf(first, second), any()) }
        }
    }

    @Test fun deleteButtonLabelTracksTheSelectionAndClicksWithNoSelectionAreIgnored() {
        mount()
        compose.onNodeWithContentDescription("选择类型").performClick()
        compose.onNodeWithContentDescription("删除0项类型").performClick()
        compose.onAllNodesWithText("删除 0 项类型？").assertCountEquals(0)
        compose.onNodeWithText(first.name).performClick()
        compose.onNodeWithContentDescription("删除1项类型").assertIsDisplayed()
        compose.runOnIdle { verify(exactly = 0) { viewModel.deleteTypes(any(), any()) } }
    }

    @Test fun selectEntryIsInertWithoutCustomTypesAndSelectionExitsOnceTheListEmpties() {
        // Only presets: nothing is selectable, so the entry must not open selection mode.
        state.value = TodoUiState(loaded = true, loading = false, types = TodoType.presets)
        mount()
        compose.onNodeWithText("还没有自定义类型").assertIsDisplayed()
        compose.onNodeWithContentDescription("选择类型").performClick()
        compose.onAllNodesWithText("已选择 0 项").assertCountEquals(0)
        compose.onNodeWithContentDescription("新建类型").assertIsDisplayed()
        // With a custom type the mode opens; an external reload that empties the list must drop it.
        state.value = TodoUiState(loaded = true, loading = false, types = TodoType.presets + first)
        compose.onNodeWithContentDescription("选择类型").performClick()
        compose.onNodeWithText("已选择 0 项").assertIsDisplayed()
        state.value = TodoUiState(loaded = true, loading = false, types = TodoType.presets)
        compose.onAllNodesWithText("已选择 0 项").assertCountEquals(0)
        compose.onNodeWithContentDescription("新建类型").assertIsDisplayed()
        compose.onNodeWithText("还没有自定义类型").assertIsDisplayed()
    }

    private fun mount() {
        every { viewModel.uiState } returns state
        compose.setContent {
            MiuixTheme {
                RetainedPopupHost {
                    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                    SideEffect { back = dispatcher }
                    TodoTypeManagerPage(onBack = { backs++ }, viewModel = viewModel)
                }
            }
        }
    }
}
