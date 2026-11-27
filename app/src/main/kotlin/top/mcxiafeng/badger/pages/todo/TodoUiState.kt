package top.mcxiafeng.badger.pages.todo

import android.icu.text.BreakIterator
import android.util.Log
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
    val selecting: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
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
    internal val selecting: Boolean = false,
    internal val selectedIds: Set<String> = emptySet(),
    val query: String = "",
    val loaded: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
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
        // Without avatars eating the trailing space a card can show noticeably more title text.
        val titleLength = if (associated.isEmpty()) TODO_CARD_TITLE_LENGTH_BARE else TODO_CARD_TITLE_LENGTH
        return TodoRowState(
            item, item.title.forTodoCard(boundaries, titleLength), summary, typesById[item.typeId],
            associated.take(TODO_CARD_AVATAR_COUNT), (associated.size - TODO_CARD_AVATAR_COUNT).coerceAtLeast(0),
        )
    }
    val visibleItems = if (status.reordering) items else filtered
    val (completed, pending) = visibleItems.sortedFor(status.sort, status.manualOrder).partition { it.completed }
    return TodoUiState(
        items = items, contacts = contacts, types = types, sort = status.sort, reordering = status.reordering,
        pending = pending.map(::row), completed = completed.map(::row),
        selecting = status.selecting,
        selectedIds = if (status.selecting) status.selectedIds.intersect(items.map { it.id }.toSet()) else emptySet(),
        query = query, loaded = status.loaded, loading = status.loading,
        saving = status.saving, error = status.error,
    )
}

private fun List<LocalTodo>.sortedFor(mode: TodoSortMode, manualOrder: List<String>): List<LocalTodo> {
    Log.d("TodoUiStateTester", "Sorting task sections; mode=$mode; count=$size")
    return when (mode) {
        TodoSortMode.CREATED -> sortedByDescending { it.createdAt }
        TodoSortMode.CREATED_ASCENDING -> sortedBy { it.createdAt }
        TodoSortMode.UPDATED -> sortedWith(compareByDescending<LocalTodo> { it.updatedAt ?: it.createdAt }
            .thenByDescending { it.createdAt })
        TodoSortMode.UPDATED_ASCENDING -> sortedWith(compareBy<LocalTodo> { it.updatedAt ?: it.createdAt }
            .thenBy { it.createdAt })
        TodoSortMode.SCHEDULED -> sortedBySchedule(descending = false)
        TodoSortMode.SCHEDULED_DESCENDING -> sortedBySchedule(descending = true)
        TodoSortMode.MANUAL -> inManualOrder(manualOrder)
    }
}

private fun List<LocalTodo>.sortedBySchedule(descending: Boolean): List<LocalTodo> {
    val scheduled = compareBy<LocalTodo> { if (it.date.isBlank()) null else LocalDate.parse(it.date) }
        .thenBy { if (it.time.isBlank()) null else LocalTime.parse(it.time) }
    // Only reverse dated values; undated tasks stay last in both directions.
    return sortedWith(compareBy<LocalTodo> { it.date.isBlank() }
        .then(if (descending) scheduled.reversed() else scheduled)
        .thenByDescending { it.createdAt })
}

private fun String.forTodoCard(boundaries: BreakIterator, maxLength: Int): String {
    if (length <= maxLength) return this
    boundaries.setText(this)
    val end = boundaries.next(maxLength)
    return if (end == BreakIterator.DONE || end == length) this else take(end) + "…"
}

/** Title budget when the row shows contact avatars / the hidden-contact counter. */
private const val TODO_CARD_TITLE_LENGTH = 15
/** Title budget when nothing follows the text, so the line has the full card width. */
private const val TODO_CARD_TITLE_LENGTH_BARE = 25
private const val TODO_CARD_AVATAR_COUNT = 3
