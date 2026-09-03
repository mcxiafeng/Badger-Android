package top.mcxiafeng.badger.pages.settings.tags

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Search
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
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun TagManagerSuccessBody(
    state: TagManagerUiState.Success,
    paddingValues: PaddingValues,
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
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        // 1) 搜索条（折叠态）
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

        // 2) 筛选 TabRowWithContour（全部 / 手动 / AI）
        //    - 选中态用 Miuix 主题色（primary）做文字和下划线指示，区别于默认的 onBackground 灰
        //    - 双向绑定 HorizontalPager：点 Tab 切换 filter，滑动 body 也切换 filter
        val pagerState = rememberPagerState(
            initialPage = TagFilterMode.entries.indexOf(state.filterMode).coerceAtLeast(0),
        ) { TagFilterMode.entries.size }
        val primary = MiuixTheme.colorScheme.primary
        val cs = MiuixTheme.colorScheme
        // pager 滑动 → 通知 VM 切 filterMode
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
                // 点 Tab：先立即同步 filter（避免列表滞后），再让 pager 滚动对齐
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

        // 3) 多选 / 计数条
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

        // 4) 列表 / 空态 —— 包到 HorizontalPager 里支持左右滑动切换 Tab
        // [修复防御]: Scaffold 的 padding 已经避让了 topBar/bottomBar，但 FAB 不算 innerPadding，
        // 所以这里需要为 FAB 多留 76dp 高度，避免最后一行被 FAB 遮挡。
        // 多选态时 bottomBar 已占位，FAB 不显示，故 bottom 不再加 76dp。
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            pageSpacing = 0.dp,
            userScrollEnabled = true,
            beyondViewportPageCount = 0,
            contentPadding = PaddingValues(
                top = 4.dp,
                bottom = if (state.multiSelect) 4.dp else 76.dp,
            ),
            pageContent = { page ->
                if (state.tags.isEmpty()) {
                    TagEmptyState()
                } else if (visible.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (query.isNotEmpty()) "没有匹配的标签" else "当前筛选下没有标签",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
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
                                dateText = dateFmt.format(Date(tag.createTime)),
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
                // 抑制 page 未使用变量警告
                @Suppress("UNUSED_EXPRESSION") page
            },
        )
    }
}
