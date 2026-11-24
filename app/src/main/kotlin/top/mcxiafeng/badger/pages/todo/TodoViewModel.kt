package top.mcxiafeng.badger.pages.todo

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.todo.LocalTodo
import top.mcxiafeng.badger.data.todo.LocalTodoRepository
import top.mcxiafeng.badger.data.todo.TodoConflictException

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TodoViewModel @Inject constructor(
    private val repository: LocalTodoRepository,
    private val contactsRepository: ContactRepository,
) : ViewModel() {
    private val status = MutableStateFlow(TodoStatus())
    private val contacts = MutableStateFlow<List<Contact>>(emptyList())
    private val query = MutableStateFlow("")
    private val contactQuery = MutableStateFlow("")
    private val choices = MutableStateFlow(TodoContactChoices())
    internal val contactChoices = choices.asStateFlow()
    private var contactJob: Job? = null
    private var searchJob: Job? = null
    val uiState = combine(repository.items, contacts, query, status, ::todoUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, TodoUiState())

    init {
        reload()
        retryContactSearch()
    }

    fun reload() {
        Log.d(TAG, "Reloading tasks and observing account contacts")
        contactJob?.cancel()
        contactJob = viewModelScope.launch {
            status.update { it.copy(loading = true) }
            contactsRepository.getAllContacts().catch { error ->
                Log.e(TAG, "Contact observation failed", error)
                status.update { it.copy(loading = false, error = "无法读取联系人，请重试") }
            }.collect { current ->
                contacts.value = current.filter { it.id > 0 }
                loadTasks()
            }
        }
    }

    private suspend fun loadTasks() {
        try {
            repository.load()
            status.update { it.copy(loaded = true, loading = false, error = null) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Task reload failed", error)
            status.update { it.copy(loading = false, error = "无法读取待办，原文件已保留，请重试") }
        }
    }

    fun search(value: String) {
        query.value = value.trim()
        Log.d(TAG, "Updated task search; active=${value.isNotBlank()}")
    }

    internal fun searchContacts(value: String) {
        val text = value.trim()
        if (contactQuery.value != text) {
            contactQuery.value = text
            choices.update { it.copy(query = text, loading = true, error = null) }
        }
        Log.d(TAG, "Updated association search; active=${text.isNotBlank()}")
    }

    internal fun retryContactSearch() {
        Log.d(TAG, "Starting association search observation")
        searchJob?.cancel()
        choices.update { it.copy(loading = true, error = null) }
        searchJob = observeContactSearch()
    }

    private fun observeContactSearch() = viewModelScope.launch {
        contactQuery.debounce(CONTACT_SEARCH_DELAY_MS).distinctUntilChanged().flatMapLatest { text ->
            // Reuse the target's indexed contact-search API instead of adding a LIKE query.
            contactsRepository.searchContacts(text).onEach { result ->
                if (contactQuery.value == text) choices.value = TodoContactChoices(
                    query = text, contacts = result.filter { it.id > 0 },
                )
            }.catch { error ->
                Log.e(TAG, "Association search failed", error)
                if (contactQuery.value == text) choices.value = TodoContactChoices(
                    query = text, error = "无法读取关联联系人，请重试",
                )
            }
        }.collect {}
    }

    internal fun save(draft: TodoDraft, original: LocalTodo?, onSaved: () -> Unit) = mutate(
        failure = "保存失败，填写内容已保留", success = onSaved,
    ) { repository.save(draft.toTodo(original), original) }

    fun toggle(id: String) = mutate("未能修改完成状态，请重试") { repository.toggle(id) }

    fun delete(id: String, onDeleted: () -> Unit) = mutate(
        failure = "删除失败，请重试", success = onDeleted,
    ) { repository.delete(id) }

    private fun mutate(failure: String, success: () -> Unit = {}, operation: suspend () -> Unit) {
        if (status.value.saving) {
            Log.d(TAG, "Ignored duplicate task action while saving")
            return
        }
        status.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                operation()
                Log.d(TAG, "Task action completed")
                status.update { it.copy(error = null) }
                success()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Task action failed", error)
                val message = if (error is TodoConflictException) error.message else failure
                status.update { it.copy(error = message) }
            } finally {
                status.update { it.copy(saving = false) }
            }
        }
    }

    companion object {
        private const val TAG = "TodoViewModelTester"
        private const val CONTACT_SEARCH_DELAY_MS = 250L
    }
}
