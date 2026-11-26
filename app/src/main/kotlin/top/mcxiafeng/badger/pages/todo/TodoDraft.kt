package top.mcxiafeng.badger.pages.todo

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.Saver
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import top.mcxiafeng.badger.data.todo.LocalTodo
import top.mcxiafeng.badger.data.todo.TodoReminder
import top.mcxiafeng.badger.data.todo.todoScheduleError

@Immutable
internal data class TodoDraft(
    @SerializedName("title") val title: String = "",
    @SerializedName("note") val note: String = "",
    @SerializedName("date") val date: String = "",
    @SerializedName("place") val place: String = "",
    @SerializedName("contacts") val contacts: List<Long> = emptyList(),
    @SerializedName("completed") val completed: Boolean = false,
    @SerializedName("associationGeneration") val associationGeneration: String = "",
    @SerializedName("time") val time: String = "",
    @SerializedName("reminder") val reminder: TodoReminder? = null,
    @SerializedName("typeId") val typeId: String = "",
) {
    val scheduleError: String? get() = todoScheduleError(date, time, reminder)

    fun withDate(value: String): TodoDraft = if (value.isBlank()) copy(date = "", time = "", reminder = null)
    else copy(date = value)

    fun toTodo(original: LocalTodo?): LocalTodo = (original ?: LocalTodo(title = title.trim())).copy(
        title = title.trim(), note = note, date = date, place = place,
        contactIds = contacts.distinct(), completed = completed, associationGeneration = associationGeneration,
        time = time, reminder = reminder, typeId = typeId,
    )

    companion object {
        fun from(item: LocalTodo) = TodoDraft(
            title = item.title, note = item.note, date = item.date, place = item.place,
            contacts = item.contactIds, completed = item.completed, associationGeneration = item.associationGeneration,
            time = item.time, reminder = item.reminder, typeId = item.typeId,
        )
    }
}

private val draftGson = Gson()

internal val TodoDraftSaver = Saver<TodoDraft, String>(
    save = { draftGson.toJson(it) }, restore = { draftGson.fromJson(it, TodoDraft::class.java) },
)

/** Save the editor's baseline too: recreation must not accept a newer disk snapshot. */
internal val TodoSnapshotSaver = Saver<LocalTodo, String>(
    save = { draftGson.toJson(it) }, restore = { draftGson.fromJson(it, LocalTodo::class.java) },
)
