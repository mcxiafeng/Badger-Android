package top.mcxiafeng.badger.pages.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity

class TagManagerUiStateTest {

    private val tags = listOf(
        TagCacheEntity(id = 1L, name = "Alpha", source = "manual", createTime = 1L),
        TagCacheEntity(id = 2L, name = "Beta", source = "ai", createTime = 2L),
        TagCacheEntity(id = 3L, name = "Alphabet", source = "manual", createTime = 3L),
    )

    @Test
    fun `searchVisibleTags applies filter sort and query together`() {
        val state = TagManagerUiState.Success(
            tags = tags,
            filterMode = TagFilterMode.Manual,
            sortMode = TagSortMode.Alphabetical,
        )

        assertThat(state.searchVisibleTags("alph").map { it.id })
            .containsExactly(1L, 3L)
            .inOrder()
    }

    @Test
    fun `blank search returns all tags from current filter`() {
        val state = TagManagerUiState.Success(
            tags = tags,
            filterMode = TagFilterMode.Manual,
            sortMode = TagSortMode.Alphabetical,
        )

        assertThat(state.searchVisibleTags("  ").map { it.id })
            .containsExactly(1L, 3L)
            .inOrder()
    }

    @Test
    fun `search is case insensitive`() {
        val state = TagManagerUiState.Success(tags = tags)

        assertThat(state.searchVisibleTags("ALPHA").map { it.id })
            .containsExactly(1L, 3L)
            .inOrder()
    }
}
