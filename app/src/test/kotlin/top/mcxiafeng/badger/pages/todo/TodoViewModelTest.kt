package top.mcxiafeng.badger.pages.todo

import android.app.Application
import androidx.lifecycle.ViewModelStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.data.todo.LocalTodo
import top.mcxiafeng.badger.data.todo.LocalTodoRepository
import top.mcxiafeng.badger.data.todo.TodoConflictException
import top.mcxiafeng.badger.data.todo.TodoSortMode
import top.mcxiafeng.badger.data.todo.TodoSortPreference
import top.mcxiafeng.badger.data.todo.TodoType
import top.mcxiafeng.badger.data.todo.TodoTypeConflictException
import top.mcxiafeng.badger.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TodoViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val repository = mockk<LocalTodoRepository>()
    private val contacts = mockk<ContactRepository>()
    private val items = MutableStateFlow<List<LocalTodo>>(emptyList())
    private val sort = MutableStateFlow(TodoSortPreference())
    private val viewModels = ViewModelStore()

    @Before fun setUp() {
        every { repository.items } returns items
        every { repository.types } returns MutableStateFlow(TodoType.presets)
        every { repository.sort } returns sort
        coEvery { repository.load() } returns Unit
        every { contacts.getAllContacts() } returns flowOf(listOf(Contact(id = 1, name = "甲")))
    }

    @After fun tearDown() { viewModels.clear() }

    @Test fun saveFailureRetainsDraftAndDoesNotNavigateThenRetrySucceeds() = runTest(main.testDispatcher) {
        coEvery { repository.save(any(), any()) } throws IOException("disk full")
        val vm = create()
        advanceUntilIdle()
        val draft = TodoDraft(title = "跟进", note = "原内容", contacts = listOf(1))
        var navigations = 0
        vm.save(draft, null) { navigations++ }
        advanceUntilIdle()
        assertEquals(0, navigations)
        assertFalse(vm.uiState.value.saving)
        assertTrue(vm.uiState.value.error!!.contains("填写内容已保留"))
        coEvery { repository.save(any(), any()) } returns Unit
        vm.save(draft, null) { navigations++ }
        advanceUntilIdle()
        assertEquals(1, navigations)
        assertEquals(null, vm.uiState.value.error)
        coVerify(exactly = 2) { repository.save(match { it.note == "原内容" && it.contactIds == listOf(1L) }, null) }
    }

    @Test fun duplicateSaveWhilePendingOnlyPersistsAndNavigatesOnce() = runTest(main.testDispatcher) {
        val pending = CompletableDeferred<Unit>()
        coEvery { repository.save(any(), any()) } coAnswers { pending.await() }
        val vm = create()
        advanceUntilIdle()
        var navigations = 0
        val save = { vm.save(TodoDraft(title = "跟进"), null) { navigations++ } }
        save()
        save()
        runCurrent()
        assertTrue(vm.uiState.value.saving)
        coVerify(exactly = 1) { repository.save(any(), any()) }
        pending.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, navigations)
        assertFalse(vm.uiState.value.saving)
    }

    @Test fun loadFailureShowsRetryStateAndConflictKeepsEditorOpen() = runTest(main.testDispatcher) {
        coEvery { repository.load() } throws IOException("bad file")
        val vm = create()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.loaded)
        assertTrue(vm.uiState.value.error!!.contains("原文件已保留"))
        coEvery { repository.load() } returns Unit
        vm.reload()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.loaded)
        assertEquals(null, vm.uiState.value.error)
        coEvery { repository.save(any(), any()) } throws TodoConflictException()
        var closed = false
        vm.save(TodoDraft(title = "旧编辑"), LocalTodo(id = "old", title = "旧标题")) { closed = true }
        advanceUntilIdle()
        assertFalse(closed)
        assertTrue(vm.uiState.value.error!!.contains("重新打开"))
    }

    @Test fun listProjectionKeepsTheFirstThreeContactsAndTruncatesAtACharacterBoundary() {
        val type = TodoType.presets.first()
        val contacts = (1L..4L).map { Contact(id = it, name = "Contact $it") }
        val firstFifteen = "a".repeat(14) + "🎨"
        val item = LocalTodo(title = firstFifteen + "尾", date = "2026-11-25", time = "09:00", place = "学校",
            typeId = type.id, contactIds = listOf(4, 3, 2, 1))
        val row = todoUiState(listOf(item), listOf(type), contacts, "", TodoStatus()).pending.single()
        assertEquals(firstFifteen + "…", row.title)
        assertEquals(item.title, row.item.title)
        assertEquals(listOf(4L, 3L, 2L), row.avatars.map { it.id })
        assertEquals(1, row.hiddenContacts)
        assertEquals(type, row.type)
        assertEquals("2026-11-25 09:00 · 学校", row.summary)
    }

    @Test fun unassociatedTitlesGetTwentyFiveCharactersWhileAssociatedOnesKeepFifteen() {
        val type = TodoType.presets.first()
        val contacts = listOf(Contact(id = 1, name = "甲"))
        val firstTwentyFive = "b".repeat(24) + "🎨"
        val bare = LocalTodo(id = "bare", title = firstTwentyFive + "尾")
        val linked = LocalTodo(id = "linked", title = firstTwentyFive + "尾", contactIds = listOf(1))
        val state = todoUiState(listOf(bare, linked), listOf(type), contacts, "", TodoStatus())
        val rows = state.pending.associateBy { it.item.id }
        // No avatars follow the text, so the whole 25-character budget is available.
        assertEquals(firstTwentyFive + "…", rows.getValue("bare").title)
        assertEquals(emptyList<Contact>(), rows.getValue("bare").avatars)
        // With an avatar the row keeps the previous 15-character budget.
        assertEquals("b".repeat(15) + "…", rows.getValue("linked").title)
        assertEquals(listOf(1L), rows.getValue("linked").avatars.map { it.id })
    }

    @Test fun titlesAtTheBoundaryAreKeptWholeInBothModes() {
        val exactBare = "c".repeat(25)
        val exactLinked = "d".repeat(15)
        val state = todoUiState(
            listOf(LocalTodo(id = "bare", title = exactBare),
                LocalTodo(id = "linked", title = exactLinked, contactIds = listOf(1))),
            listOf(TodoType.presets.first()), listOf(Contact(id = 1, name = "甲")), "", TodoStatus(),
        )
        val rows = state.pending.associateBy { it.item.id }
        assertEquals(exactBare, rows.getValue("bare").title)
        assertEquals(exactLinked, rows.getValue("linked").title)
    }

    @Test fun sortsEachSectionByAllSixDirectionsIncludingLegacyAndUndatedItems() {
        val early = LocalTodo(id = "early", title = "早", date = "2026-12-01", time = "09:05", createdAt = 10, updatedAt = 90)
        val late = LocalTodo(id = "late", title = "晚", date = "2026-12-01", time = "11:00", createdAt = 30, updatedAt = 60)
        val next = LocalTodo(id = "next", title = "次日", date = "2026-12-02", time = "08:00", createdAt = 50, updatedAt = 80)
        val legacy = LocalTodo(id = "legacy", title = "未设日期", createdAt = 70)
        val pending = listOf(late, next, early, legacy)
        val completed = pending.map { it.copy(id = "done-${it.id}", completed = true) }
        val expectedOrders = mapOf(
            TodoSortMode.SCHEDULED to listOf("early", "late", "next", "legacy"),
            TodoSortMode.SCHEDULED_DESCENDING to listOf("next", "late", "early", "legacy"),
            TodoSortMode.CREATED to listOf("legacy", "next", "late", "early"),
            TodoSortMode.CREATED_ASCENDING to listOf("early", "late", "next", "legacy"),
            TodoSortMode.UPDATED to listOf("early", "next", "legacy", "late"),
            TodoSortMode.UPDATED_ASCENDING to listOf("late", "legacy", "next", "early"),
            TodoSortMode.MANUAL to listOf("late", "next", "early", "legacy"),
        )
        for ((mode, expected) in expectedOrders) {
            val state = todoUiState(pending + completed, emptyList(), emptyList(), "", TodoStatus(sort = mode))
            assertEquals(mode.name, expected, state.pending.map { it.item.id })
            assertEquals(mode.name, expected.map { "done-$it" }, state.completed.map { it.item.id })
        }
    }

    @Test fun sortCategoriesExposeOrderedDirectionsAndCategoryLabels() {
        val expected = mapOf(
            TodoSortMode.CREATED to ("按创建时间" to listOf(TodoSortMode.CREATED, TodoSortMode.CREATED_ASCENDING)),
            TodoSortMode.UPDATED to ("按修改时间" to listOf(TodoSortMode.UPDATED, TodoSortMode.UPDATED_ASCENDING)),
            TodoSortMode.SCHEDULED to ("按待办时间" to listOf(TodoSortMode.SCHEDULED_DESCENDING, TodoSortMode.SCHEDULED)),
            TodoSortMode.MANUAL to ("手动拖动" to emptyList()),
        )
        assertEquals(expected.keys.toList(), TodoSortMode.categories)
        for ((category, values) in expected) {
            val (label, directions) = values
            assertEquals(category, category.category)
            assertEquals(label, category.label)
            assertEquals(directions, category.directions)
            for (direction in directions) {
                assertEquals(category, direction.category)
                assertEquals(label, direction.label)
                assertEquals(directions, direction.directions)
            }
        }
        assertEquals(listOf("新到旧", "旧到新"), TodoSortMode.CREATED.directions.map { it.directionLabel })
        assertEquals(listOf("晚到早", "早到晚"), TodoSortMode.UPDATED.directions.map { it.directionLabel })
        assertEquals(listOf("晚到早", "早到晚"), TodoSortMode.SCHEDULED.directions.map { it.directionLabel })
        assertEquals(null, TodoSortMode.MANUAL.directionLabel)
    }

    @Test fun scheduledDirectionsKeepUndatedItemsLastAndRetainDateOnlyTasks() {
        val pending = listOf(
            LocalTodo(id = "undated-old", title = "无日期旧", createdAt = 10),
            LocalTodo(id = "timed", title = "有时间", date = "2026-12-01", time = "09:00", createdAt = 20),
            LocalTodo(id = "date-only", title = "仅日期", date = "2026-12-01", createdAt = 30),
            LocalTodo(id = "undated-new", title = "无日期新", createdAt = 40),
        )
        val completed = pending.map { it.copy(id = "done-${it.id}", completed = true) }
        val expectedOrders = mapOf(
            TodoSortMode.SCHEDULED to listOf("date-only", "timed", "undated-new", "undated-old"),
            TodoSortMode.SCHEDULED_DESCENDING to listOf("timed", "date-only", "undated-new", "undated-old"),
        )
        for ((mode, expected) in expectedOrders) {
            val state = todoUiState(pending + completed, emptyList(), emptyList(), "", TodoStatus(sort = mode))
            assertEquals(mode.name, expected, state.pending.map { it.item.id })
            assertEquals(mode.name, expected.map { "done-$it" }, state.completed.map { it.item.id })
            assertEquals(pending + completed, state.items)
        }
    }

    @Test fun switchingAllDirectionsAndBackRetainsManualOrderWithinBothSections() = runTest(main.testDispatcher) {
        val pending = listOf(LocalTodo(id = "a", title = "甲"), LocalTodo(id = "b", title = "乙"))
        val completed = pending.map { it.copy(id = "done-${it.id}", completed = true) }
        items.value = pending + completed
        val manualOrder = listOf("done-b", "b", "done-a", "a")
        sort.value = TodoSortPreference(TodoSortMode.MANUAL, manualOrder)
        coEvery { repository.setSortMode(any()) } coAnswers {
            sort.value = sort.value.copy(mode = firstArg())
        }
        val vm = create()
        advanceUntilIdle()
        for (mode in TodoSortMode.entries) {
            vm.setSortMode(mode)
            advanceUntilIdle()
            assertEquals(mode, vm.uiState.value.sort)
            vm.setSortMode(TodoSortMode.MANUAL)
            advanceUntilIdle()
            assertEquals(listOf("b", "a"), vm.uiState.value.pending.map { it.item.id })
            assertEquals(listOf("done-b", "done-a"), vm.uiState.value.completed.map { it.item.id })
            assertEquals(manualOrder, sort.value.manualOrder)
        }
    }

    @Test fun manualMovesStayInTheDraftUntilSavedAndCancelRestoresOrder() = runTest(main.testDispatcher) {
        items.value = listOf(LocalTodo(id = "a", title = "甲"), LocalTodo(id = "b", title = "乙"),
            LocalTodo(id = "c", title = "丙", completed = true))
        sort.value = TodoSortPreference(TodoSortMode.MANUAL, listOf("a", "b", "c"))
        coEvery { repository.saveManualOrder(any()) } coAnswers {
            sort.value = TodoSortPreference(TodoSortMode.MANUAL, firstArg())
        }
        val vm = create()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.reordering)
        vm.beginManualSort()
        vm.move("c", "a", false)
        vm.move("b", "a", false)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.reordering)
        assertEquals(listOf("b", "a"), vm.uiState.value.pending.map { it.item.id })
        assertEquals(listOf("c"), vm.uiState.value.completed.map { it.item.id })
        coVerify(exactly = 0) { repository.saveManualOrder(any()) }
        vm.cancelManualSort()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.reordering)
        assertEquals(listOf("a", "b"), vm.uiState.value.pending.map { it.item.id })
        vm.beginManualSort()
        vm.move("b", "a", false)
        vm.finishManualSort()
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.saveManualOrder(listOf("b", "a", "c")) }
        assertFalse(vm.uiState.value.reordering)
        assertEquals(listOf("b", "a"), vm.uiState.value.pending.map { it.item.id })
    }

    @Test fun selectionDeletesAcrossBothSectionsAndFailureRetainsChoicesForRetry() = runTest(main.testDispatcher) {
        items.value = listOf(LocalTodo(id = "a", title = "甲"), LocalTodo(id = "b", title = "乙", completed = true),
            LocalTodo(id = "keep", title = "保留"))
        val vm = create()
        advanceUntilIdle()
        vm.beginSelection()
        vm.toggleSelection("a")
        vm.toggleSelection("b")
        vm.toggleSelection("missing")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.selecting)
        assertEquals(setOf("a", "b"), vm.uiState.value.selectedIds)
        vm.beginManualSort()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.reordering)
        coEvery { repository.deleteAll(any()) } throws IOException("disk full")
        vm.deleteSelected()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.selecting)
        assertEquals(setOf("a", "b"), vm.uiState.value.selectedIds)
        assertTrue(vm.uiState.value.error!!.contains("已保留选择"))
        coEvery { repository.deleteAll(any()) } coAnswers {
            val ids = firstArg<Set<String>>()
            items.value = items.value.filterNot { it.id in ids }
        }
        vm.deleteSelected()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.selecting)
        assertTrue(vm.uiState.value.selectedIds.isEmpty())
        assertEquals(listOf("keep"), vm.uiState.value.items.map { it.id })
        coVerify(exactly = 2) { repository.deleteAll(setOf("a", "b")) }
    }

    @Test fun selectionToggleExitAndExternalDeletionDoNotLeavePhantomSelectedRows() = runTest(main.testDispatcher) {
        items.value = listOf(LocalTodo(id = "a", title = "甲"), LocalTodo(id = "b", title = "乙"))
        val vm = create()
        advanceUntilIdle()
        vm.beginSelection()
        vm.toggleSelection("a")
        vm.toggleSelection("a")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.selectedIds.isEmpty())
        vm.toggleSelection("b")
        items.value = items.value.filterNot { it.id == "b" }
        advanceUntilIdle()
        assertTrue(vm.uiState.value.selectedIds.isEmpty())
        vm.deleteSelected()
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.deleteAll(any()) }
        vm.exitSelection()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.selecting)
        vm.beginManualSort()
        vm.beginSelection()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.reordering)
        assertFalse(vm.uiState.value.selecting)
    }

    @Test fun clearCompletedDeletesOnlyCompletedTasksAndSkipsWhenNoneRemain() = runTest(main.testDispatcher) {
        items.value = listOf(
            LocalTodo(id = "open", title = "未完成"),
            LocalTodo(id = "done-1", title = "已完成一", completed = true),
            LocalTodo(id = "done-2", title = "已完成二", completed = true),
        )
        coEvery { repository.deleteAll(any()) } coAnswers {
            val ids = firstArg<Set<String>>()
            items.value = items.value.filterNot { it.id in ids }
        }
        val vm = create()
        advanceUntilIdle()
        vm.clearCompleted()
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.deleteAll(setOf("done-1", "done-2")) }
        assertEquals(listOf("open"), vm.uiState.value.items.map { it.id })
        vm.clearCompleted()
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.deleteAll(any()) }
    }

    @Test fun clearCompletedFailureKeepsTheCompletedTasksForRetry() = runTest(main.testDispatcher) {
        items.value = listOf(LocalTodo(id = "done", title = "已完成", completed = true))
        coEvery { repository.deleteAll(any()) } throws IOException("disk full")
        val vm = create()
        advanceUntilIdle()
        vm.clearCompleted()
        advanceUntilIdle()
        assertEquals(listOf("done"), vm.uiState.value.items.map { it.id })
        assertTrue(vm.uiState.value.error!!.contains("已完成待办已保留"))
        assertFalse(vm.uiState.value.saving)
    }

    @Test fun pendingBatchDeletionBlocksSelectionChangesExitAndDuplicateDelete() = runTest(main.testDispatcher) {
        items.value = listOf(LocalTodo(id = "a", title = "甲"))
        val pending = CompletableDeferred<Unit>()
        coEvery { repository.deleteAll(any()) } coAnswers { pending.await() }
        val vm = create()
        advanceUntilIdle()
        vm.beginSelection()
        vm.toggleSelection("a")
        vm.deleteSelected()
        runCurrent()
        vm.exitSelection()
        vm.toggleSelection("a")
        vm.deleteSelected()
        runCurrent()
        assertTrue(vm.uiState.value.saving)
        assertTrue(vm.uiState.value.selecting)
        assertEquals(setOf("a"), vm.uiState.value.selectedIds)
        coVerify(exactly = 1) { repository.deleteAll(setOf("a")) }
        pending.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.saving)
        assertFalse(vm.uiState.value.selecting)
    }

    @Test fun deletingSelectedTypesWritesOnceAndExitsOnlyAfterSuccess() = runTest(main.testDispatcher) {
        val first = TodoType.custom("返图", TodoType.presets.first().color)
        val second = TodoType.custom("活动", TodoType.presets[1].color)
        val types = MutableStateFlow<List<TodoType>>(TodoType.presets + first + second)
        every { repository.types } returns types
        coEvery { repository.deleteTypes(any()) } coAnswers {
            val removed = firstArg<List<TodoType>>().map { it.id }.toSet()
            types.value = types.value.filterNot { it.id in removed }
        }
        val vm = create()
        advanceUntilIdle()
        var exited = 0
        vm.deleteTypes(listOf(first)) { exited++ }
        advanceUntilIdle()
        assertEquals(1, exited)
        assertEquals(listOf(second), vm.uiState.value.types.filterNot { it.isPreset })
        coVerify(exactly = 1) { repository.deleteTypes(listOf(first)) }
    }

    @Test fun failedTypeDeletionKeepsTheSelectionAndReportsTheConflict() = runTest(main.testDispatcher) {
        val type = TodoType.custom("返图", TodoType.presets.first().color)
        every { repository.types } returns MutableStateFlow(TodoType.presets + type)
        coEvery { repository.deleteTypes(any()) } throws TodoTypeConflictException()
        val vm = create()
        advanceUntilIdle()
        var exited = 0
        vm.deleteTypes(listOf(type)) { exited++ }
        advanceUntilIdle()
        assertEquals(0, exited)
        assertTrue(vm.uiState.value.error!!.contains("已被修改或删除"))
        assertFalse(vm.uiState.value.saving)
    }

    private fun create(): TodoViewModel = TodoViewModel(repository, contacts, mockk<TagRepository>())
        .also { viewModels.put("todo", it) }
}
