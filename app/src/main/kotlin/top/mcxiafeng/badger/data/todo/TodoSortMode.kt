package top.mcxiafeng.badger.data.todo

import androidx.compose.runtime.Immutable

@Immutable
internal data class TodoSortPreference(
    val mode: TodoSortMode = TodoSortMode.SCHEDULED,
    val manualOrder: List<String> = emptyList(),
)

internal fun List<LocalTodo>.inManualOrder(order: List<String>): List<LocalTodo> {
    if (order.isEmpty()) return this
    val positions = order.withIndex().associate { it.value to it.index }
    return sortedBy { positions[it.id] ?: Int.MAX_VALUE }
}

internal enum class TodoSortMode(val label: String) {
    CREATED("按创建时间"),
    UPDATED("按修改时间"),
    SCHEDULED("按待办时间"),
    MANUAL("手动拖动"),
    CREATED_ASCENDING("按创建时间"),
    UPDATED_ASCENDING("按修改时间"),
    SCHEDULED_DESCENDING("按待办时间");

    val category: TodoSortMode
        get() = when (this) {
            CREATED, CREATED_ASCENDING -> CREATED
            UPDATED, UPDATED_ASCENDING -> UPDATED
            SCHEDULED, SCHEDULED_DESCENDING -> SCHEDULED
            MANUAL -> MANUAL
        }

    val directionLabel: String?
        get() = when (this) {
            CREATED -> "新到旧"
            CREATED_ASCENDING -> "旧到新"
            UPDATED, SCHEDULED_DESCENDING -> "晚到早"
            UPDATED_ASCENDING, SCHEDULED -> "早到晚"
            MANUAL -> null
        }

    val directions: List<TodoSortMode>
        get() = when (category) {
            CREATED -> listOf(CREATED, CREATED_ASCENDING)
            UPDATED -> listOf(UPDATED, UPDATED_ASCENDING)
            SCHEDULED -> listOf(SCHEDULED_DESCENDING, SCHEDULED)
            else -> emptyList()
        }

    companion object {
        val categories: List<TodoSortMode> = listOf(CREATED, UPDATED, SCHEDULED, MANUAL)
    }
}
