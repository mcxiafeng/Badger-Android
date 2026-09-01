package top.mcxiafeng.badger.pages.settings

/**
 * 标签管理页事件流。
 *
 * Composable 将用户意图转发给 ViewModel；事件只携带 UI 已经确定的参数，
 * 避免让 ViewModel 反向依赖 Composable 的瞬时查询状态。
 */
sealed interface TagManagerEvent {
    data class ChangeFilter(val mode: TagFilterMode) : TagManagerEvent
    data class ChangeSort(val mode: TagSortMode) : TagManagerEvent

    data class EnterMultiSelect(val initialSelectedId: Long? = null) : TagManagerEvent
    data object ExitMultiSelect : TagManagerEvent

    data class ToggleSelect(val tagId: Long) : TagManagerEvent

    /**
     * 全选当前页面实际可见的标签。
     * UI 层负责把搜索 + 筛选后的 ID 传入，避免全选时误选被搜索隐藏的标签。
     */
    data class SelectAll(val visibleTagIds: Set<Long>) : TagManagerEvent
    data object ClearSelection : TagManagerEvent

    data class Create(val name: String, val colorArgb: Long) : TagManagerEvent
    data class Rename(val tagId: Long, val newName: String) : TagManagerEvent
    data class SetColor(val tagId: Long, val colorArgb: Long) : TagManagerEvent
    data class SetShowDot(val tagId: Long, val show: Boolean) : TagManagerEvent
    data class ForceDelete(val tagId: Long) : TagManagerEvent
    data class Merge(val fromTagId: Long, val toTagId: Long) : TagManagerEvent
    data class BatchSetColor(val tagIds: List<Long>, val colorArgb: Long) : TagManagerEvent
    data class BatchDelete(val tagIds: List<Long>) : TagManagerEvent

    data object Refresh : TagManagerEvent
}
