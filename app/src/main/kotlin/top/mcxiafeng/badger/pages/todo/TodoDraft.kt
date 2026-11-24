package top.mcxiafeng.badger.pages.todo

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.listSaver
import top.mcxiafeng.badger.data.todo.LocalTodo

@Immutable
internal data class TodoDraft(
    val title: String = "",
    val note: String = "",
    val date: String = "",
    val place: String = "",
    val contacts: List<Long> = emptyList(),
    val completed: Boolean = false,
    val associationGeneration: String = "",
) {
    fun toTodo(original: LocalTodo?): LocalTodo = (original ?: LocalTodo(title = title.trim())).copy(
        title = title.trim(), note = note, date = date, place = place,
        contactIds = contacts.distinct(), completed = completed, associationGeneration = associationGeneration,
    )

    companion object {
        fun from(item: LocalTodo) = TodoDraft(
            item.title, item.note, item.date, item.place, item.contactIds, item.completed, item.associationGeneration,
        )
    }
}

internal val TodoDraftSaver = listSaver<TodoDraft, Any>(
    save = { listOf(it.title, it.note, it.date, it.place, it.contacts.joinToString(","), it.completed, it.associationGeneration) },
    restore = { TodoDraft(
        it[0] as String, it[1] as String, it[2] as String, it[3] as String,
        (it[4] as String).split(',').filter(String::isNotEmpty).map(String::toLong),
        it[5] as Boolean, it[6] as String,
    ) },
)

/** Save the editor's baseline too: recreation must not silently accept a newer disk snapshot. */
internal val TodoSnapshotSaver = listSaver<LocalTodo, Any>(
    save = { listOf(it.id, it.title, it.note, it.date, it.place, it.contactIds.joinToString(","),
        it.completed, it.createdAt, it.associationGeneration) },
    restore = { LocalTodo(
        id = it[0] as String, title = it[1] as String, note = it[2] as String,
        date = it[3] as String, place = it[4] as String,
        contactIds = (it[5] as String).split(',').filter(String::isNotEmpty).map(String::toLong),
        completed = it[6] as Boolean, createdAt = it[7] as Long, associationGeneration = it[8] as String,
    ) },
)
