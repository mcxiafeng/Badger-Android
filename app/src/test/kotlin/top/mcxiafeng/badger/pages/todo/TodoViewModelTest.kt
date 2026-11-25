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
        every { contacts.searchContacts(any()) } returns flowOf(emptyList())
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

    @Test fun sortsEachSectionByTheSelectedModeIncludingLegacyAndUndatedItems() {
        val early = LocalTodo(id = "early", title = "早", date = "2026-12-01", time = "09:05", createdAt = 10, updatedAt = 90)
        val late = LocalTodo(id = "late", title = "晚", date = "2026-12-01", time = "11:00", createdAt = 30, updatedAt = 60)
        val legacy = LocalTodo(id = "legacy", title = "未设日期", createdAt = 70)
        val pending = listOf(late, early, legacy)
        val completed = pending.map { it.copy(id = "done-${it.id}", completed = true) }
        val expectedOrders = mapOf(
            TodoSortMode.SCHEDULED to listOf("early", "late", "legacy"),
            TodoSortMode.CREATED to listOf("legacy", "late", "early"),
            TodoSortMode.UPDATED to listOf("early", "legacy", "late"),
            TodoSortMode.MANUAL to listOf("late", "early", "legacy"),
        )
        for ((mode, expected) in expectedOrders) {
            val state = todoUiState(pending + completed, emptyList(), emptyList(), "", TodoStatus(sort = mode))
            assertEquals(expected, state.pending.map { it.item.id })
            assertEquals(expected.map { "done-$it" }, state.completed.map { it.item.id })
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

    private fun create(): TodoViewModel = TodoViewModel(repository, contacts, mockk<TagRepository>())
        .also { viewModels.put("todo", it) }
}
