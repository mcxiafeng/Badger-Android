package top.mcxiafeng.badger.pages.todo

import androidx.compose.runtime.Immutable
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.todo.LocalTodo

@Immutable
internal data class TodoRowState(val item: LocalTodo, val summary: String)

@Immutable
internal data class TodoStatus(
    val loaded: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
)

@Immutable
data class TodoUiState internal constructor(
    internal val items: List<LocalTodo> = emptyList(),
    internal val pending: List<TodoRowState> = emptyList(),
    internal val completed: List<TodoRowState> = emptyList(),
    internal val contacts: List<Contact> = emptyList(),
    val query: String = "",
    val loaded: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
)

@Immutable
internal data class TodoContactChoices(
    val query: String = "",
    val contacts: List<Contact> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

internal fun todoUiState(
    items: List<LocalTodo>, contacts: List<Contact>, query: String, status: TodoStatus,
): TodoUiState {
    val names = contacts.associate { it.id to it.name }
    val filtered = items.filter {
        it.title.contains(query, true) || it.note.contains(query, true) || it.place.contains(query, true)
    }
    fun row(item: LocalTodo) = TodoRowState(item, listOf(
        item.date, item.place, item.contactIds.mapNotNull(names::get).joinToString("、"),
    ).filter(String::isNotBlank).joinToString(" · "))
    return TodoUiState(
        items = items, contacts = contacts,
        pending = filtered.filterNot { it.completed }
            .sortedWith(compareBy<LocalTodo> { it.date.ifBlank { UNDATED_SORT_KEY } }.thenByDescending { it.createdAt })
            .map(::row),
        completed = filtered.filter { it.completed }.sortedByDescending { it.createdAt }.map(::row),
        query = query, loaded = status.loaded, loading = status.loading,
        saving = status.saving, error = status.error,
    )
}

private const val UNDATED_SORT_KEY = "9999-12-31"
