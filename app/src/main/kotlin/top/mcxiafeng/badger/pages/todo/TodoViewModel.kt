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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.Tag
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.data.todo.LocalTodo
import top.mcxiafeng.badger.data.todo.LocalTodoRepository
import top.mcxiafeng.badger.data.todo.TodoConflictException
import top.mcxiafeng.badger.data.todo.TodoSortMode
import top.mcxiafeng.badger.data.todo.TodoType
import top.mcxiafeng.badger.data.todo.TodoTypeConflictException
import top.mcxiafeng.badger.data.todo.inManualOrder

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TodoViewModel @Inject constructor(
    private val repository: LocalTodoRepository,
    private val contactsRepository: ContactRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {
    private val status = MutableStateFlow(TodoStatus())
    private val contacts = MutableStateFlow<List<Contact>>(emptyList())
    private val query = MutableStateFlow("")
    private var contactJob: Job? = null
    private val manualDraft = MutableStateFlow<List<String>?>(null)
    private val listStatus = combine(status, repository.sort, manualDraft) { current, preference, draft ->
        current.copy(
            sort = if (draft == null) preference.mode else TodoSortMode.MANUAL,
            manualOrder = draft ?: preference.manualOrder,
            reordering = draft != null,
        )
    }
    val uiState = combine(repository.items, repository.types, contacts, query, listStatus, ::todoUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, TodoUiState())

    init {
        reload()
    }

    fun reload() {
        // 联系人是 Room Flow：订阅保持活跃时 Room 会自动推送变更，
        // 页面返回/切 Tab 触发的 reload 只需重读待办文件，不重复建订阅
        if (contactJob?.isActive == true) {
            Log.d(TAG, "Reloading tasks only; contact observation still active")
            viewModelScope.launch { loadTasks() }
            return
        }
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

    internal fun observeContactTags(ids: List<Long>) =
        if (ids.isEmpty()) flowOf(emptyMap<Long, List<Tag>>())
        else tagRepository.observeTagsForContacts(ids).catch { error ->
            Log.e(TAG, "Associated contact tags could not be loaded", error)
            emit(emptyMap())
        }

    internal fun save(draft: TodoDraft, original: LocalTodo?, onSaved: () -> Unit) = mutate(
        failure = "保存失败，填写内容已保留", success = onSaved,
    ) {
        Log.d(TAG, "Saving task draft; editing=${original != null}")
        repository.save(draft.toTodo(original), original)
    }

    internal fun setSortMode(mode: TodoSortMode) = mutate("无法保存排序方式，请重试") {
        repository.setSortMode(mode)
    }

    internal fun beginManualSort() {
        if (!status.value.loaded || status.value.saving || status.value.selecting || manualDraft.value != null) return
        manualDraft.value = repository.items.value.inManualOrder(repository.sort.value.manualOrder).map { it.id }
        Log.d(TAG, "Opened manual task ordering draft")
    }

    internal fun move(id: String, anchorId: String, after: Boolean) {
        val draft = manualDraft.value ?: return
        if (status.value.saving || id == anchorId) return
        val current = repository.items.value
        val item = current.find { it.id == id } ?: return
        if (current.none { it.id == anchorId && it.completed == item.completed }) return
        val next = current.inManualOrder(draft).map { it.id }.toMutableList()
        next.remove(id)
        next.add(next.indexOf(anchorId) + if (after) 1 else 0, id)
        manualDraft.value = next
        Log.d(TAG, "Moved a task in the manual order draft")
    }

    internal fun finishManualSort() {
        val draft = manualDraft.value ?: return
        mutate("无法保存待办顺序，请重试", success = { manualDraft.value = null }) {
            repository.saveManualOrder(draft)
        }
    }

    internal fun cancelManualSort() {
        if (!status.value.saving) {
            manualDraft.value = null
            Log.d(TAG, "Discarded manual task ordering draft")
        }
    }

    internal fun beginSelection() {
        if (!status.value.loaded || status.value.saving || manualDraft.value != null) return
        status.update { it.copy(selecting = true, selectedIds = emptySet()) }
        Log.d(TAG, "Entered task selection mode")
    }

    internal fun exitSelection() {
        if (status.value.saving) return
        status.update { it.copy(selecting = false, selectedIds = emptySet()) }
        Log.d(TAG, "Exited task selection mode")
    }

    internal fun toggleSelection(id: String) {
        if (!status.value.selecting || status.value.saving || repository.items.value.none { it.id == id }) return
        status.update {
            it.copy(selectedIds = if (id in it.selectedIds) it.selectedIds - id else it.selectedIds + id)
        }
        Log.d(TAG, "Changed task selection; count=${status.value.selectedIds.size}")
    }

    /** Removes every completed task; the list keeps its pending items and the saved sort order. */
    internal fun clearCompleted() {
        val ids = repository.items.value.filter { it.completed }.map { it.id }.toSet()
        if (ids.isEmpty()) {
            Log.d(TAG, "Ignored clear-completed request; nothing is completed")
            return
        }
        Log.d(TAG, "Requested completed-task cleanup; count=${ids.size}")
        mutate("清除失败，已完成待办已保留，请重试") { repository.deleteAll(ids) }
    }

    internal fun deleteSelected() {
        if (!status.value.selecting) return
        val ids = status.value.selectedIds.intersect(repository.items.value.map { it.id }.toSet())
        if (ids.isEmpty()) return
        Log.d(TAG, "Requested selected task deletion; count=${ids.size}")
        mutate("删除失败，已保留选择，请重试", success = {
            status.update { it.copy(selecting = false, selectedIds = emptySet()) }
            Log.d(TAG, "Deleted selected tasks and exited selection")
        }) { repository.deleteAll(ids) }
    }

    fun toggle(id: String) = mutate("未能修改完成状态，请重试") { repository.toggle(id) }

    fun delete(id: String, onDeleted: () -> Unit) = mutate(
        failure = "删除失败，请重试", success = onDeleted,
    ) { repository.delete(id) }

    internal fun saveType(type: TodoType?, name: String, color: Long, onSaved: () -> Unit) = mutate(
        failure = "无法保存待办类型，请重试", success = onSaved,
    ) { repository.saveType(type?.copy(name = name, color = color) ?: TodoType.custom(name, color), type) }

    internal fun deleteTypes(types: List<TodoType>, onDeleted: () -> Unit) {
        if (types.isEmpty()) {
            Log.d(TAG, "Ignored task-type deletion; nothing is selected")
            return
        }
        Log.d(TAG, "Requested task-type deletion; count=${types.size}")
        mutate(failure = "无法删除待办类型，请重试", success = onDeleted) { repository.deleteTypes(types) }
    }

    private fun mutate(
        failure: String, success: () -> Unit = {}, cleanup: () -> Unit = {}, operation: suspend () -> Unit,
    ) {
        if (status.value.saving) {
            Log.d(TAG, "Ignored duplicate task action while saving")
            cleanup()
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
                val message = when (error) {
                    is TodoConflictException, is TodoTypeConflictException -> error.message
                    else -> failure
                }
                status.update { it.copy(error = message) }
            } finally {
                status.update { it.copy(saving = false) }
            }
        }.invokeOnCompletion { cleanup() }
    }

    companion object {
        private const val TAG = "TodoViewModelTester"
    }
}
