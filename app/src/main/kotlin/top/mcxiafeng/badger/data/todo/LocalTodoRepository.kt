package top.mcxiafeng.badger.data.todo

import android.content.Context
import android.util.Log
import dagger.hilt.android.scopes.ViewModelScoped
import java.time.LocalDate
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
    @AccountScopedContext context: Context,
    private val contacts: ContactDao,
) {
    private val storage = TodoFileStore(context.filesDir)
    private val shared = stores.getOrPut(storage.key) { SharedStore() }
    val items = shared.items.asStateFlow()

    suspend fun load(): Unit = access {
        val generation = storage.generation()
        val current = storage.read(generation)
        val ids = validContactIds()
        val safe = current.map { cleanLinks(it, ids, generation) }
        if (safe != current) storage.write(safe)
        shared.items.value = safe
        Log.d(TAG, "Loaded and checked task associations; count=${safe.size}")
    }

    /** A new task has no original. Updates compare the original editor snapshot under the lock. */
    suspend fun save(todo: LocalTodo, original: LocalTodo? = null) = mutate { current, ids, generation ->
        require(todo.id.isNotBlank() && todo.title.isNotBlank()) { "请填写待办标题" }
        if (todo.date.isNotBlank()) LocalDate.parse(todo.date)
        val latest = current.find { it.id == todo.id }
        val expected = original?.let { cleanLinks(it, ids, generation) }
        if (latest != expected || (original != null && latest == null)) {
            Log.d(TAG, "Rejected stale task editor")
            throw TodoConflictException()
        }
        val candidate = todo.copy(title = todo.title.trim(),
            associationGeneration = if (original == null) generation else todo.associationGeneration)
        val safe = cleanLinks(candidate, ids, generation)
        Log.d(TAG, "Saving task; create=${original == null}")
        current.filterNot { it.id == safe.id } + safe
    }

    suspend fun toggle(id: String) = mutate { current, _, _ ->
        if (current.none { it.id == id }) throw TodoConflictException()
        Log.d(TAG, "Toggling task completion")
        current.map { if (it.id == id) it.copy(completed = !it.completed) else it }
    }

    suspend fun delete(id: String) = mutate { current, _, _ ->
        Log.d(TAG, "Deleting task; exists=${current.any { it.id == id }}")
        current.filterNot { it.id == id }
    }

    private suspend fun mutate(
        change: (List<LocalTodo>, Set<Long>, String) -> List<LocalTodo>,
    ) = access {
        val generation = storage.generation()
        val ids = validContactIds()
        val current = storage.read(generation).map { cleanLinks(it, ids, generation) }
        val next = change(current, ids, generation)
        storage.write(next)
        shared.items.value = next
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
    }

    companion object {
        private const val TAG = "LocalTodoRepositoryTester"
        private val stores = ConcurrentHashMap<String, SharedStore>()
    }
}
