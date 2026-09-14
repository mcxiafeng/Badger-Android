package top.mcxiafeng.badger.pages.settings.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity as Tag
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Tag
import top.mcxiafeng.badger.utils.formatEpochDate
import top.mcxiafeng.badger.utils.formatEpochDateTime
import top.mcxiafeng.badger.ui.components.BadgerEmptyStateSimple

private val LIST_BOTTOM_FAB_AVOIDANCE = 76.dp

@Composable
internal fun TagManagerSuccessBody(
    state: TagManagerUiState.Success,
    paddingValues: PaddingValues,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    showSearch: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onClickTag: (Tag) -> Unit,
    onLongClickTag: (Tag) -> Unit,
    onSetShowDot: (Long, Boolean) -> Unit,
    onClickColor: (Tag) -> Unit,
    onClickDelete: (Tag) -> Unit,
    onChangeFilter: (TagFilterMode) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val visible = remember(state, query) {
        val q = query.trim()
        if (q.isEmpty()) state.visibleTags
        else state.visibleTags.filter { it.name.contains(q, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        
        if (showSearch) {
            SearchBar(
                inputField = {
                    InputField(
                        query = query,
                        onQueryChange = onQueryChange,
                        onSearch = { onCloseSearch() },
                        expanded = true,
                        onExpandedChange = { if (!it) onCloseSearch() },
                        label = "搜索标签",
                    )
                },
                expanded = true,
                onExpandedChange = { if (!it) onCloseSearch() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            ) {}
        }

        
        
        
        val pagerState = rememberPagerState(
            initialPage = TagFilterMode.entries.indexOf(state.filterMode).coerceAtLeast(0),
        ) { TagFilterMode.entries.size }
        val primary = MiuixTheme.colorScheme.primary
        val cs = MiuixTheme.colorScheme
        
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }
                .collect { page ->
                    val newMode = TagFilterMode.entries[page]
                    if (newMode != state.filterMode) {
                        onChangeFilter(newMode)
                    }
                }
        }
        TabRowWithContour(
            tabs = TagFilterMode.entries.map { it.label },
            selectedTabIndex = pagerState.currentPage,
            onTabSelected = { idx ->
                
                onChangeFilter(TagFilterMode.entries[idx])
                scope.launch { pagerState.animateScrollToPage(idx) }
            },
            colors = TabRowDefaults.tabRowColors(
                backgroundColor = cs.surface,
                contentColor = cs.onSurfaceVariantSummary,
                selectedBackgroundColor = cs.surface,
                selectedContentColor = primary,
            ),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )

        
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.multiSelect) {
                Text(
                    text = "已选 ${state.selectedIds.size} / ${visible.size}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            } else {
                Text(
                    text = "共 ${visible.size} 个${if (state.tags.size != visible.size) " / 总 ${state.tags.size}" else ""}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }

        
        
        
        
        
        
        val pullState = rememberPullToRefreshState()
        PullToRefresh(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            pullToRefreshState = pullState,
            modifier = Modifier.weight(1f),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 0.dp,
                userScrollEnabled = true,
                beyondViewportPageCount = 0,
                contentPadding = PaddingValues(
                    top = 4.dp,
                    bottom = if (state.multiSelect) 4.dp else LIST_BOTTOM_FAB_AVOIDANCE,
                ),
                pageContent = { page ->
                    if (state.tags.isEmpty()) {
                        
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            BadgerEmptyStateSimple(
                                icon = Lucide.Tag,
                                title = "还没有标签",
                                subtitle = "标签用于分类与快速识别\n点击右下角 + 创建第一个标签",
                            )
                            Text(
                                text = "刷新同步云端数据",
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { onRefresh() }
                                    .padding(top = 12.dp),
                            )
                        }
                    } else if (visible.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            BadgerEmptyStateSimple(
                                icon = Lucide.Tag,
                                title = if (query.isNotEmpty()) "没有匹配的标签" else "当前筛选下没有标签",
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(visible, key = { it.id }) { tag ->
                                val isSelected = tag.id in state.selectedIds
                                TagManagerListRow(
                                    tag = tag,
                                    dateText = formatEpochDate(tag.createTime),
                                    multiSelect = state.multiSelect,
                                    selected = isSelected,
                                    onClick = { onClickTag(tag) },
                                    onLongClick = { onLongClickTag(tag) },
                                    onSetShowDot = { v -> onSetShowDot(tag.id, v) },
                                    onClickColor = { onClickColor(tag) },
                                    onClickDelete = { onClickDelete(tag) },
                                )
                            }
                        }
                    }
                    
                    @Suppress("UNUSED_EXPRESSION") page
                },
            )
        }
    }
}
