package top.mcxiafeng.badger.pages.settings.tags

import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity as Tag

/**
 * 标签管理筛选模式：控制可见标签的 source 维度。
 *
 * - [All] 同时显示手动 + AI
 * - [Manual] 仅 source == "manual"
 * - [Ai] 仅 source == "ai"
 */
enum class TagFilterMode(val label: String) {
    All("全部"),
    Manual("手动"),
    Ai("AI");

    fun matches(tag: Tag): Boolean = when (this) {
        All -> true
        Manual -> tag.source == "manual"
        Ai -> tag.source == "ai"
    }
}

/**
 * 标签管理排序模式。
 *
 * - [Alphabetical] 按 pinyinInitial + name（与 DAO 默认一致）
 * - [CreatedDesc] 按 createTime 倒序（最新优先）
 */
enum class TagSortMode(val label: String) {
    Alphabetical("字母"),
    CreatedDesc("最新创建");

    fun sort(tags: List<Tag>): List<Tag> = when (this) {
        Alphabetical -> tags.sortedWith(
            compareBy({ it.pinyinInitial }, { it.name })
        )
        CreatedDesc -> tags.sortedByDescending { it.createTime }
    }
}

/**
 * 标签管理页统一 UI 状态。
 *
 * - [Loading] 数据还在加载（首帧 / refresh）
 * - [Success] 列表可用；[filterMode] / [sortMode] 控制展示，[selectedIds] 是多选态下的勾选
 * - [Error] 异常态（一般通过 Snackbar 反馈瞬时错误；该 state 用于致命失败）
 */
sealed interface TagManagerUiState {
    data object Loading : TagManagerUiState

    data class Success(
        val tags: List<Tag>,
        val filterMode: TagFilterMode = TagFilterMode.All,
        val sortMode: TagSortMode = TagSortMode.Alphabetical,
        val selectedIds: Set<Long> = emptySet(),
        val multiSelect: Boolean = false,
    ) : TagManagerUiState {
        /** 按当前 filter + sort 渲染的展示列表 */
        val visibleTags: List<Tag>
            get() = sortMode.sort(tags.filter(filterMode::matches))
    }

    data class Error(val message: String) : TagManagerUiState
}

/**
 * 一次性 UI 消息（成功 / 失败反馈），用 Channel 上抛给 Composable 转 Snackbar。
 *
 * 持久状态走 StateFlow；瞬时反馈走 Channel，符合 NowInAndroid 模式。
 */
sealed interface TagManagerMessage {
    val text: String

    data class Info(override val text: String) : TagManagerMessage
    data class Error(override val text: String) : TagManagerMessage
}
