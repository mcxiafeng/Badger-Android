package top.mcxiafeng.badger.pages.todo

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.todo.TodoSortMode
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.glass.RetainedPopupHost
import top.mcxiafeng.badger.ui.glass.flushPopupUi
import top.mcxiafeng.badger.ui.glass.startPopupMotionFrames
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TodoMoreMenuTest {
    @get:Rule val compose = createComposeRule()
    private val sorts = mutableListOf<TodoSortMode>()
    private var manages = 0
    private var selections = 0
    private var clears = 0
    private val completed = mutableStateOf(2)

    @Test fun sortingAndDirectionPopupsOverlapTheirOwnParentsWithoutCompoundingTheFirstScrim() {
        mount()
        openSort()
        val parent = compose.onNodeWithTag("todo_more_menu").fetchSemanticsNode().boundsInWindow
        val second = compose.onNodeWithTag("todo_sort_menu").fetchSemanticsNode().boundsInWindow
        assertThat(second.top).isAtLeast(parent.top)
        assertThat(second.top).isLessThan(parent.bottom)
        assertThat(alpha("todo_more_menu")).isEqualTo(MAX_SCRIM)
        TodoSortMode.categories.forEach { compose.onNodeWithText(it.label).assertExists() }
        openDirection(TodoSortMode.CREATED)
        compose.onAllNodes(isPopup()).assertCountEquals(3)
        val third = compose.onNodeWithTag("todo_direction_menu").fetchSemanticsNode().boundsInWindow
        assertThat(third.top).isAtLeast(second.top)
        assertThat(third.top).isLessThan(second.bottom)
        assertThat(alpha("todo_more_menu")).isEqualTo(MAX_SCRIM)
        assertThat(alpha("todo_sort_menu")).isEqualTo(MAX_SCRIM)
        compose.onNodeWithText("旧到新").performClick()
        compose.onNodeWithText("待办类型管理").assertDoesNotExist()
        compose.runOnIdle { assertThat(sorts).containsExactly(TodoSortMode.CREATED_ASCENDING) }
    }

    @Test fun everyDateCategoryOffersTheRequestedLabelsAndDispatchesItsDirection() {
        mount()
        for (category in TodoSortMode.categories.filter { it != TodoSortMode.MANUAL }) {
            openSort()
            openDirection(category)
            val labels = if (category == TodoSortMode.CREATED) listOf("新到旧", "旧到新") else listOf("晚到早", "早到晚")
            labels.forEach { compose.onNodeWithText(it).assertIsDisplayed() }
            compose.onNodeWithText(labels.first()).performClick()
        }
        compose.runOnIdle {
            assertThat(sorts).containsExactly(TodoSortMode.CREATED, TodoSortMode.UPDATED, TodoSortMode.SCHEDULED_DESCENDING).inOrder()
        }
    }

    @Test fun closingOneLevelRestoresOnlyItsDirectParent() {
        mount()
        openSort()
        openDirection(TodoSortMode.UPDATED)
        compose.onAllNodes(isPopup())[2].performTouchInput { click(bottomLeft) }
        compose.onNodeWithTag("todo_direction_menu").assertDoesNotExist()
        assertThat(alpha("todo_sort_menu")).isEqualTo(0f)
        assertThat(alpha("todo_more_menu")).isEqualTo(MAX_SCRIM)
        compose.onAllNodes(isPopup())[1].performTouchInput { click(bottomLeft) }
        compose.onNodeWithTag("todo_sort_menu").assertDoesNotExist()
        assertThat(alpha("todo_more_menu")).isEqualTo(0f)
        compose.onNodeWithText("多选").performClick()
        compose.runOnIdle { assertThat(selections).isEqualTo(1); assertThat(sorts).isEmpty() }
    }

    @Test fun scrimsStartFadingWhileTheirChildrenAreStillExiting() {
        mount()
        openSort()
        openDirection(TodoSortMode.CREATED)
        // Native popup windows need looper work and entering frames before the clock freezes.
        compose.flushPopupUi()
        compose.waitForIdle()
        compose.flushPopupUi()
        compose.mainClock.autoAdvance = false
        compose.startPopupMotionFrames()
        compose.onNodeWithContentDescription("收起按创建时间").performClick()
        advanceIntoExit()
        assertThat(alpha("todo_sort_menu")).isGreaterThan(0f)
        assertThat(alpha("todo_sort_menu")).isLessThan(MAX_SCRIM)
        assertThat(alpha("todo_more_menu")).isEqualTo(MAX_SCRIM)
        compose.onAllNodes(isPopup()).assertCountEquals(3)
        finishExit()
        assertThat(alpha("todo_sort_menu")).isEqualTo(0f)
        compose.onAllNodes(isPopup()).assertCountEquals(2)
        compose.onNodeWithContentDescription("收起排序方式").performClick()
        advanceIntoExit()
        assertThat(alpha("todo_more_menu")).isGreaterThan(0f)
        assertThat(alpha("todo_more_menu")).isLessThan(MAX_SCRIM)
        compose.onAllNodes(isPopup()).assertCountEquals(2)
        finishExit()
        assertThat(alpha("todo_more_menu")).isEqualTo(0f)
        compose.onAllNodes(isPopup()).assertCountEquals(1)
    }

    @Test fun typeManagementClosesTheMenuAndNavigatesExactlyOnce() {
        mount()
        open()
        compose.onNodeWithText("待办类型管理").performClick()
        compose.onNodeWithText("多选").assertDoesNotExist()
        compose.runOnIdle { assertThat(manages).isEqualTo(1) }
    }

    @Test fun manualSortingRemainsADirectActionAndTheCurrentDirectionClosesWithoutSavingAgain() {
        mount()
        openSort()
        openDirection(TodoSortMode.SCHEDULED)
        compose.onNodeWithText("早到晚").performClick()
        compose.onNodeWithText("多选").assertDoesNotExist()
        compose.runOnIdle { assertThat(sorts).isEmpty() }
        openSort()
        compose.onNodeWithText(TodoSortMode.MANUAL.label).performScrollTo().performClick()
        compose.runOnIdle { assertThat(sorts).containsExactly(TodoSortMode.MANUAL) }
    }

    @Test fun headingsCollapseOnlyTheirOwnLevelAndCanReopen() {
        mount()
        openSort()
        openDirection(TodoSortMode.CREATED)
        compose.onNodeWithContentDescription("收起按创建时间").performClick()
        compose.onNodeWithTag("todo_direction_menu").assertDoesNotExist()
        assertThat(alpha("todo_sort_menu")).isEqualTo(0f)
        compose.onNodeWithContentDescription("收起排序方式").performClick()
        compose.onNodeWithTag("todo_sort_menu").assertDoesNotExist()
        assertThat(alpha("todo_more_menu")).isEqualTo(0f)
        compose.onNodeWithText("排序方式").performClick()
        openDirection(TodoSortMode.UPDATED)
        compose.onNodeWithText("早到晚").performClick()
        compose.runOnIdle { assertThat(sorts).containsExactly(TodoSortMode.UPDATED_ASCENDING) }
    }

    private fun alpha(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().config[TodoMenuScrimAlpha]

    @Test fun clearCompletedClosesTheMenuAndIsDisabledWhenNothingIsCompleted() {
        mount()
        open()
        compose.onNodeWithText("清除已完成待办").assertIsDisplayed().performClick()
        compose.onNodeWithText("多选").assertDoesNotExist()
        compose.runOnIdle { assertThat(clears).isEqualTo(1) }

        completed.value = 0
        open()
        compose.onNodeWithText("清除已完成待办").performClick()
        compose.runOnIdle { assertThat(clears).isEqualTo(1) }
    }

    private fun open() {
        compose.onNodeWithContentDescription("更多待办操作").performClick()
        compose.onNodeWithText("多选").assertIsDisplayed()
    }

    private fun openSort() {
        open()
        compose.onNodeWithText("排序方式").performClick()
    }

    private fun openDirection(category: TodoSortMode) {
        compose.onNodeWithText(category.label).performScrollTo().performClick()
    }

    private fun advanceIntoExit() {
        compose.startPopupMotionFrames()
        compose.mainClock.advanceTimeBy(EXIT_SAMPLE_MS)
        compose.flushPopupUi()
    }

    private fun finishExit() {
        compose.mainClock.advanceTimeBy(BadgerMotion.DURATION_TOUCH_FEEDBACK + EXIT_SAMPLE_MS)
        compose.flushPopupUi()
    }

    private fun mount() {
        compose.setContent {
            MiuixTheme {
                RetainedPopupHost {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.size(BadgerSize.minimumTouchTarget)) {
                            TodoMoreMenu(TodoSortMode.SCHEDULED, true, { sorts += it }, { manages++ }, { selections++ },
                                onClearCompleted = { clears++ }, completedCount = completed.value)
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val MAX_SCRIM = 0.25f
        const val EXIT_SAMPLE_MS = 64L
    }
}
