package top.mcxiafeng.badger.data.todo

import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
data class LocalTodo(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val note: String = "",
    /** ISO local date, or empty when no date was selected. */
    val date: String = "",
    val place: String = "",
    val contactIds: List<Long> = emptyList(),
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val associationGeneration: String = "",
)

class TodoConflictException : IllegalStateException("待办已被修改或删除，请返回列表后重新打开")
