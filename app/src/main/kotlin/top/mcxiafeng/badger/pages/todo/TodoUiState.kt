package top.mcxiafeng.badger.pages.todo

import android.icu.text.BreakIterator
import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.time.LocalTime
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.todo.LocalTodo
import top.mcxiafeng.badger.data.todo.TodoSortMode
import top.mcxiafeng.badger.data.todo.TodoType
import top.mcxiafeng.badger.data.todo.inManualOrder

@Immutable
internal data class TodoRowState(
    val item: LocalTodo,
    val title: String,
    val summary: String,
    val type: TodoType?,
    val avatars: List<Contact>,
    val hiddenContacts: Int,
)

@Immutable
internal data class TodoStatus(
    val loaded: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val sort: TodoSortMode = TodoSortMode.SCHEDULED,
    val manualOrder: List<String> = emptyList(),
    val reordering: Boolean = false,
)

@Immutable
data class TodoUiState internal constructor(
    internal val items: List<LocalTodo> = emptyList(),
    internal val pending: List<TodoRowState> = emptyList(),
    internal val completed: List<TodoRowState> = emptyList(),
    internal val contacts: List<Contact> = emptyList(),
    internal val types: List<TodoType> = TodoType.presets,
    internal val sort: TodoSortMode = TodoSortMode.SCHEDULED,
    internal val reordering: Boolean = false,
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
    items: List<LocalTodo>, types: List<TodoType>, contacts: List<Contact>, query: String, status: TodoStatus,
): TodoUiState {
    val contactsById = contacts.associateBy { it.id }
    val typesById = types.associateBy { it.id }
    val boundaries = BreakIterator.getCharacterInstance()
    val filtered = items.filter {
        it.title.contains(query, true) || it.note.contains(query, true) || it.place.contains(query, true) ||
            typesById[it.typeId]?.name?.contains(query, true) == true
    }
    fun row(item: LocalTodo): TodoRowState {
        val summary = listOf(
            listOf(item.date, item.time).filter(String::isNotBlank).joinToString(" "), item.place,
        ).filter(String::isNotBlank).joinToString(" · ")
        val associated = item.contactIds.mapNotNull(contactsById::get)
        return TodoRowState(
            item, item.title.forTodoCard(boundaries), summary, typesById[item.typeId],
            associated.take(TODO_CARD_AVATAR_COUNT), (associated.size - TODO_CARD_AVATAR_COUNT).coerceAtLeast(0),
        )
    }
    val visibleItems = if (status.reordering) items else filtered
    val (completed, pending) = visibleItems.sortedFor(status.sort, status.manualOrder).partition { it.completed }
    return TodoUiState(
        items = items, contacts = contacts, types = types, sort = status.sort, reordering = status.reordering,
        pending = pending.map(::row), completed = completed.map(::row),
        query = query, loaded = status.loaded, loading = status.loading,
        saving = status.saving, error = status.error,
    )
}

private fun List<LocalTodo>.sortedFor(mode: TodoSortMode, manualOrder: List<String>): List<LocalTodo> = when (mode) {
    TodoSortMode.CREATED -> sortedByDescending { it.createdAt }
    TodoSortMode.UPDATED -> sortedWith(compareByDescending<LocalTodo> { it.updatedAt ?: it.createdAt }
        .thenByDescending { it.createdAt })
    TodoSortMode.SCHEDULED -> sortedWith(compareBy<LocalTodo> { it.date.isBlank() }
        .thenBy { if (it.date.isBlank()) null else LocalDate.parse(it.date) }
        .thenBy { if (it.time.isBlank()) null else LocalTime.parse(it.time) }
        .thenByDescending { it.createdAt })
    TodoSortMode.MANUAL -> inManualOrder(manualOrder)
}

private fun String.forTodoCard(boundaries: BreakIterator): String {
    if (length <= TODO_CARD_TITLE_LENGTH) return this
    boundaries.setText(this)
    val end = boundaries.next(TODO_CARD_TITLE_LENGTH)
    return if (end == BreakIterator.DONE || end == length) this else take(end) + "…"
}

private const val TODO_CARD_TITLE_LENGTH = 15
private const val TODO_CARD_AVATAR_COUNT = 3
