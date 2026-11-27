package top.mcxiafeng.badger.data.todo

import android.content.Context
import android.util.Log
import dagger.hilt.android.scopes.ViewModelScoped
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.ContactDao
import top.mcxiafeng.badger.data.account.AccountScopedContext

/** Fixed account context, shared per-file lock, and read-before-write across all route instances. */
@ViewModelScoped
class LocalTodoRepository @Inject constructor(
    @AccountScopedContext private val context: Context,
    private val contacts: ContactDao,
) {
    private val storage = TodoFileStore(context.filesDir)
    private val shared = sharedStore(storage.key)
    val items = shared.items.asStateFlow()
    internal val types = shared.types.asStateFlow()
    internal val sort = shared.sort.asStateFlow()

    suspend fun load(): Unit = access {
        val generation = storage.generation()
        val current = storage.read(generation)
        val customTypes = storage.readTypes()
        val sort = storage.readSort()
        val ids = validContactIds()
        val safe = current.map { cleanLinks(it, ids, generation) }
        if (safe != current) storage.write(safe)
        shared.types.value = TodoType.presets + customTypes
        shared.sort.value = sort
        shared.items.value = safe
        TodoReminderScheduler.sync(context, emptyList(), safe)
        Log.d(TAG, "Loaded and checked task associations; count=${safe.size}")
    }

    /** A new task has no original. Updates compare the original editor snapshot under the lock. */
    suspend fun save(todo: LocalTodo, original: LocalTodo? = null): Unit = access {
        require(todo.id.isNotBlank() && todo.title.isNotBlank()) { "请填写待办标题" }
        val scheduleError = todoScheduleError(todo.date, todo.time, todo.reminder)
        require(scheduleError == null) { checkNotNull(scheduleError) }
        val generation = storage.generation()
        val ids = validContactIds()
        val current = readWithCleanLinks(ids, generation)
        val latest = current.find { it.id == todo.id }
        val expected = original?.let { cleanLinks(it, ids, generation) }
        if (latest != expected || (original != null && latest == null)) {
            Log.d(TAG, "Rejected stale task editor")
            throw TodoConflictException()
        }
        val typeId = todo.typeId.takeIf { id ->
            id.isNotEmpty() && (TodoType.presets + storage.readTypes()).any { it.id == id }
        }.orEmpty()
        val candidate = todo.copy(title = todo.title.trim(), typeId = typeId,
            associationGeneration = if (original == null) generation else todo.associationGeneration)
        val safe = cleanLinks(candidate, ids, generation)
        val changed = if (latest != null && safe != latest) safe.modified() else safe
        val next = if (latest == null) current + changed else current.map { if (it.id == changed.id) changed else it }
        commit(current, next)
        Log.d(TAG, "Saved task; create=${original == null}")
    }

    suspend fun toggle(id: String) = mutate { current, _, _ ->
        if (current.none { it.id == id }) throw TodoConflictException()
        Log.d(TAG, "Toggling task completion")
        current.map { if (it.id == id) it.copy(completed = !it.completed).modified() else it }
    }

    internal suspend fun setSortMode(mode: TodoSortMode): Unit = access {
        publishSort(storage.readSort().copy(mode = mode))
        Log.d(TAG, "Saved task sort mode: $mode")
    }

    internal suspend fun saveManualOrder(order: List<String>): Unit = access {
        val current = storage.read(storage.generation())
        // Retain tasks added since editing began and omit tasks that have since been removed.
        val ids = current.inManualOrder(order).map { it.id }
        publishSort(TodoSortPreference(TodoSortMode.MANUAL, ids))
        Log.d(TAG, "Committed manual task order; count=${ids.size}")
    }

    private fun publishSort(preference: TodoSortPreference) {
        storage.writeSort(preference)
        shared.sort.value = preference
    }

    private fun LocalTodo.modified() = copy(updatedAt = maxOf(System.currentTimeMillis(), (updatedAt ?: createdAt) + 1L))

    suspend fun delete(id: String) = mutate { current, _, _ ->
        Log.d(TAG, "Deleting task; exists=${current.any { it.id == id }}")
        current.filterNot { it.id == id }
    }

    internal suspend fun deleteAll(ids: Set<String>) = mutate { current, _, _ ->
        Log.d(TAG, "Deleting selected tasks atomically; requested=${ids.size}; existing=${current.count { it.id in ids }}")
        current.filterNot { it.id in ids }
    }

    internal suspend fun saveType(type: TodoType, original: TodoType? = null) = mutateTypes { current ->
        val name = type.name.trim()
        require(name.isNotEmpty() && !type.isPreset && type.color in 0xFF000000..0xFFFFFFFF)
        if (current.find { it.id == type.id } != original) throw TodoTypeConflictException()
        if ((TodoType.presets + current).any { it.id != type.id && it.name.equals(name, true) }) {
            throw TodoTypeConflictException("已有同名待办类型")
        }
        if (original == null) current + type.copy(name = name)
        else current.map { if (it.id == type.id) type.copy(name = name) else it }
    }

    internal suspend fun deleteTypes(types: List<TodoType>) = mutateTypes { current ->
        types.forEach { type -> if (current.find { it.id == type.id } != type) throw TodoTypeConflictException() }
        val ids = types.map { it.id }.toSet()
        Log.d(TAG, "Deleting task types; count=${ids.size}")
        current.filterNot { it.id in ids }
    }

    private suspend fun mutateTypes(change: (List<TodoType>) -> List<TodoType>): Unit = access {
        val next = change(storage.readTypes())
        storage.writeTypes(next)
        shared.types.value = TodoType.presets + next
    }

    private suspend fun mutate(
        change: (List<LocalTodo>, Set<Long>, String) -> List<LocalTodo>,
    ) = access {
        val generation = storage.generation()
        val ids = validContactIds()
        val current = readWithCleanLinks(ids, generation)
        commit(current, change(current, ids, generation))
    }

    private fun readWithCleanLinks(ids: Set<Long>, generation: String): List<LocalTodo> =
        storage.read(generation).map { cleanLinks(it, ids, generation) }

    private fun commit(previous: List<LocalTodo>, next: List<LocalTodo>) {
        storage.write(next)
        shared.items.value = next
        TodoReminderScheduler.sync(context, previous, next)
    }

    private suspend fun validContactIds(): Set<Long> =
        contacts.getAllContacts().first().asSequence().map { it.id }.filter { it > 0 }.toSet()

    private fun cleanLinks(todo: LocalTodo, ids: Set<Long>, generation: String): LocalTodo = todo.copy(
        contactIds = if (todo.associationGeneration == generation) todo.contactIds.filter { it in ids }.distinct()
        else emptyList(),
        associationGeneration = generation,
    )

    private suspend fun <T> access(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        shared.mutex.withLock {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Local task operation failed; keeping stored data", error)
                throw error
            }
        }
    }

    private class SharedStore {
        val mutex = Mutex()
        val items = MutableStateFlow<List<LocalTodo>>(emptyList())
        val types = MutableStateFlow(TodoType.presets)
        val sort = MutableStateFlow(TodoSortPreference())
    }

    companion object {
        private const val TAG = "LocalTodoRepositoryTester"
        private val stores = ConcurrentHashMap<String, SharedStore>()

        private fun sharedStore(key: String) = stores.getOrPut(key) { SharedStore() }
        internal fun reminderMutex(key: String): Mutex = sharedStore(key).mutex
    }
}

internal class TodoTypeConflictException(message: String = "待办类型已被修改或删除") : IllegalStateException(message)
