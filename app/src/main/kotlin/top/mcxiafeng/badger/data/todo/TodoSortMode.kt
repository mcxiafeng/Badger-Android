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
    CREATED("按创建日期"),
    UPDATED("按修改日期"),
    SCHEDULED("按待办日期"),
    MANUAL("手动拖动"),
}
