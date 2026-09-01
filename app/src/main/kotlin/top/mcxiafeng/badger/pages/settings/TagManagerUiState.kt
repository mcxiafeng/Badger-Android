package top.mcxiafeng.badger.pages.settings

import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity as Tag

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

enum class TagSortMode(val label: String) {
    Alphabetical("字母"),
    CreatedDesc("最新创建");

    fun sort(tags: List<Tag>): List<Tag> = when (this) {
        Alphabetical -> tags.sortedWith(compareBy({ it.pinyinInitial }, { it.name }))
        CreatedDesc -> tags.sortedByDescending { it.createTime }
    }
}

/** 标签管理页统一 UI 状态；搜索仍属于页面展示状态。 */
sealed interface TagManagerUiState {
    data object Loading : TagManagerUiState

    data class Success(
        val tags: List<Tag>,
        val filterMode: TagFilterMode = TagFilterMode.All,
        val sortMode: TagSortMode = TagSortMode.Alphabetical,
        val selectedIds: Set<Long> = emptySet(),
        val multiSelect: Boolean = false,
    ) : TagManagerUiState {
        /** 当前筛选 + 排序后的标签列表。 */
        val visibleTags: List<Tag>
            get() = sortMode.sort(tags.filter(filterMode::matches))

        /** 当前用户真正能看到的标签列表，同时应用搜索词。 */
        fun searchVisibleTags(query: String): List<Tag> {
            val q = query.trim()
            return if (q.isEmpty()) {
                visibleTags
            } else {
                visibleTags.filter { it.name.contains(q, ignoreCase = true) }
            }
        }
    }

    data class Error(val message: String) : TagManagerUiState
}

sealed interface TagManagerMessage {
    val text: String

    data class Info(override val text: String) : TagManagerMessage
    data class Error(override val text: String) : TagManagerMessage
}
