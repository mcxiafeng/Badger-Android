package top.mcxiafeng.badger.pages.settings

/**
 * 标签管理页事件流。
 *
 * Composable 收集 UI 事件并转发给 ViewModel，事件本身不依赖 Repository。
 */
sealed interface TagManagerEvent {
    data class ChangeFilter(val mode: TagFilterMode) : TagManagerEvent
    data class ChangeSort(val mode: TagSortMode) : TagManagerEvent

    data class EnterMultiSelect(val initialSelectedId: Long? = null) : TagManagerEvent
    data object ExitMultiSelect : TagManagerEvent
    data class ToggleSelect(val tagId: Long) : TagManagerEvent
    /** Select only the IDs currently visible to the user, including the active search query. */
    data class SelectAll(val visibleTagIds: List<Long>) : TagManagerEvent
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
