package top.mcxiafeng.badger.pages.settings

/**
 * 标签管理页事件流。
 *
 * Composable 收集 [top.mcxiafeng.badger.pages.settings.TagManagerSettingsViewModel.events] 后
 * 把 Event 转发给 VM.onEvent。这里把用户意图扁平化为数据类，便于测试和复用。
 */
sealed interface TagManagerEvent {
    /** 切换筛选模式（全部 / 手动 / AI） */
    data class ChangeFilter(val mode: TagFilterMode) : TagManagerEvent

    /** 切换排序模式 */
    data class ChangeSort(val mode: TagSortMode) : TagManagerEvent

    /** 进入 / 退出多选模式（进入时多带一个初始选中行） */
    data class EnterMultiSelect(val initialSelectedId: Long? = null) : TagManagerEvent
    data object ExitMultiSelect : TagManagerEvent

    /** 多选模式下勾选/取消单条 */
    data class ToggleSelect(val tagId: Long) : TagManagerEvent
    data object SelectAll : TagManagerEvent
    data object ClearSelection : TagManagerEvent

    /** 触发新建标签（由 Composable 弹 Dialog 收集 name + color，再调 create） */
    data class Create(val name: String, val colorArgb: Long) : TagManagerEvent

    /** 重命名（带新名字。重复 / 空名校验在 VM 内部进行） */
    data class Rename(val tagId: Long, val newName: String) : TagManagerEvent

    /** 改色 (ARGB Long) */
    data class SetColor(val tagId: Long, val colorArgb: Long) : TagManagerEvent

    /** 改色点开关 */
    data class SetShowDot(val tagId: Long, val show: Boolean) : TagManagerEvent

    /** 单条强制删除 */
    data class ForceDelete(val tagId: Long) : TagManagerEvent

    /** 把 fromTag 的联系人关联转移到 toTag，再删 fromTag */
    data class Merge(val fromTagId: Long, val toTagId: Long) : TagManagerEvent

    /** 批量改色 / 删除（多选模式用） */
    data class BatchSetColor(val tagIds: List<Long>, val colorArgb: Long) : TagManagerEvent
    data class BatchDelete(val tagIds: List<Long>) : TagManagerEvent

    /** 退出页面前清空状态用 */
    data object Refresh : TagManagerEvent
}
