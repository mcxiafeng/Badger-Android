package top.mcxiafeng.badger.pages.settings.tags

import androidx.compose.runtime.Immutable
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
        Alphabetical -> tags.sortedWith(
            compareBy({ it.pinyinInitial }, { it.name })
        )
        CreatedDesc -> tags.sortedByDescending { it.createTime }
    }
}

@Immutable
sealed interface TagManagerUiState {
    data object Loading : TagManagerUiState

    data class Success(
        val tags: List<Tag>,
        val filterMode: TagFilterMode = TagFilterMode.All,
        val sortMode: TagSortMode = TagSortMode.Alphabetical,
        val selectedIds: Set<Long> = emptySet(),
        val multiSelect: Boolean = false,
    ) : TagManagerUiState {
        
        val visibleTags: List<Tag>
            get() = sortMode.sort(tags.filter(filterMode::matches))
    }

    data class Error(val message: String) : TagManagerUiState
}

