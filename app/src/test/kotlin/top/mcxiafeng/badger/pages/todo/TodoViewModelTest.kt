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
import top.mcxiafeng.badger.data.todo.LocalTodo
import top.mcxiafeng.badger.data.todo.LocalTodoRepository
import top.mcxiafeng.badger.data.todo.TodoConflictException
import top.mcxiafeng.badger.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TodoViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val repository = mockk<LocalTodoRepository>()
    private val contacts = mockk<ContactRepository>()
    private val items = MutableStateFlow<List<LocalTodo>>(emptyList())
    private val viewModels = ViewModelStore()

    @Before fun setUp() {
        every { repository.items } returns items
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

    private fun create(): TodoViewModel = TodoViewModel(repository, contacts).also { viewModels.put("todo", it) }
}
