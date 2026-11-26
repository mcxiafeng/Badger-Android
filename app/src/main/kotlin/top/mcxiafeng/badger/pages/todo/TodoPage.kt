package top.mcxiafeng.badger.pages.todo

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.mcxiafeng.badger.data.todo.TodoSortMode
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.components.Card
import top.mcxiafeng.badger.ui.components.SkeletonBox
import top.mcxiafeng.badger.ui.components.rememberSkeletonDelayVisible
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.glass.GlassActionGroup
import top.mcxiafeng.badger.ui.glass.IconButton
import top.mcxiafeng.badger.ui.glass.PageActionBar
import top.mcxiafeng.badger.ui.glass.Scaffold
import top.mcxiafeng.badger.ui.glass.TextButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.mcxiafeng.badger.ui.components.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun TodoRoute(
    onEdit: (String) -> Unit, onManageTypes: () -> Unit, isActive: Boolean,
    viewModel: TodoViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(searching) { if (searching) list.animateScrollToItem(0) }
    LaunchedEffect(query, viewModel) { viewModel.search(query) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.reload() }
    LaunchedEffect(Unit) { Log.d(TAG, "Full viewport; title pinned with actions; Gaussian blur and top opacity veil") }
    val closeSearch = { searching = false; query = ""; viewModel.search("") }
    BackHandler(enabled = isActive && (state.reordering || searching)) {
        if (state.reordering) viewModel.cancelManualSort() else closeSearch()
    }
    Scaffold(progressiveTopBlur = true, topBar = {
        PageActionBar(title = if (state.reordering) "手动排序" else "待办") { TodoActions(state, onSearch = {
            if (searching) closeSearch() else searching = true
            Log.d(TAG, "Changed task search visibility; expanded=$searching")
        }, onCreate = { onEdit("") }, onManageTypes = onManageTypes,
            onCancel = viewModel::cancelManualSort, onSort = { mode ->
                if (mode == TodoSortMode.MANUAL) {
                    focusManager.clearFocus(); closeSearch(); viewModel.beginManualSort()
                } else viewModel.setSortMode(mode)
            }) }
    }, floatingActionButton = {
        if (state.reordering) TextButton(text = if (state.saving) "保存中" else "完成",
            onClick = viewModel::finishManualSort, enabled = state.loaded && !state.saving,
            minHeight = BadgerSize.minimumTouchTarget, colors = ButtonDefaults.textButtonColorsPrimary(),
            modifier = Modifier.padding(bottom = LocalFloatingBarBottomPadding.current))
    }) { padding ->
        TodoContent(state, viewModel, onEdit, padding, list) { TodoHeader(state, searching, query, { query = it }, viewModel::reload) }
    }
}

@Composable
private fun TodoActions(
    state: TodoUiState, onSearch: () -> Unit, onCreate: () -> Unit, onManageTypes: () -> Unit,
    onCancel: () -> Unit, onSort: (TodoSortMode) -> Unit,
) {
    if (state.reordering) {
        TextButton(text = "取消", onClick = onCancel, enabled = !state.saving)
    } else {
        GlassActionGroup {
            IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "搜索待办") }
            IconButton(onClick = onManageTypes) { Icon(Icons.Default.Style, "管理待办类型") }
            IconButton(onClick = onCreate) { Icon(Icons.Default.Add, "新建待办") }
            TodoSortMenu(state.sort, state.loaded && !state.saving, onSort)
        }
    }
}

@Composable
private fun TodoHeader(state: TodoUiState, searching: Boolean, query: String, onQuery: (String) -> Unit, onRetry: () -> Unit) {
    AnimatedVisibility(searching) {
        TextField(cornerRadius = BadgerRadius.surface, value = query, onValueChange = onQuery, label = "搜索标题、说明、地点或类型", singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = BadgerSpacing.sm))
    }
    state.error?.let { TodoError(it, onRetry) }
}

@Composable
private fun TodoContent(
    state: TodoUiState, viewModel: TodoViewModel, onEdit: (String) -> Unit, padding: PaddingValues, list: LazyListState,
    header: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize().imePadding()) {
        if (state.pending.isNotEmpty() || state.completed.isNotEmpty()) {
            TodoList(state, viewModel, onEdit, padding, list, header)
        } else {
            Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + LocalFloatingBarBottomPadding.current)) {
                Column(Modifier.padding(horizontal = BadgerSpacing.xl)) { header() }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    // ON_RESUME 的 reload 会短暂置 loading=true；此分支仅在列表确实为空时到达，
                    // 骨架不会覆盖已加载的列表（列表非空走上方 TodoList）
                    if (state.loading) {
                        TodoListSkeleton(Modifier.fillMaxWidth())
                    } else {
                        TodoEmptyState(state, onEdit)
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoEmptyState(state: TodoUiState, onEdit: (String) -> Unit) {
    when {
        !state.loaded -> Text("待办暂时无法读取")
        state.query.isNotEmpty() -> Text("未找到匹配的待办")
        else -> Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BadgerSpacing.md)) {
            Text("还没有待办")
            if (!state.reordering) Text("新建待办", color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.clickable { onEdit("") }.heightIn(min = BadgerSize.minimumTouchTarget).padding(BadgerSpacing.md))
        }
    }
}

@Composable
internal fun TodoError(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(BadgerSpacing.xl), verticalArrangement = Arrangement.spacedBy(BadgerSpacing.sm)) {
        Text(message, color = MiuixTheme.colorScheme.error)
        Text("点击重试", color = MiuixTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onRetry))
    }
}

/** 待办骨架行数：与首屏可见行数接近，数据到达前占住列表高度 */
private const val TODO_SKELETON_ROW_COUNT = 4

/** 待办卡片骨架：与 TodoRow（48dp 图标 + 标题/摘要 + 箭头）同构 */
@Composable
private fun TodoListSkeleton(modifier: Modifier = Modifier) {
    if (!rememberSkeletonDelayVisible()) return
    Column(
        modifier = modifier.padding(horizontal = BadgerSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(BadgerSpacing.cardGap)
    ) {
        repeat(TODO_SKELETON_ROW_COUNT) {
            Card(cornerRadius = BadgerRadius.card, insideMargin = PaddingValues(BadgerSpacing.sm)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SkeletonBox(
                        Modifier
                            .size(BadgerSize.minimumTouchTarget)
                            .clip(RoundedCornerShape(BadgerRadius.chip))
                    )
                    Column(
                        Modifier.weight(1f).padding(horizontal = BadgerSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(BadgerSpacing.xs)
                    ) {
                        SkeletonBox(
                            Modifier.fillMaxWidth(0.7f).height(18.dp)
                                .clip(RoundedCornerShape(BadgerRadius.chip))
                        )
                        SkeletonBox(
                            Modifier.fillMaxWidth(0.5f).height(12.dp)
                                .clip(RoundedCornerShape(BadgerRadius.chip))
                        )
                    }
                    SkeletonBox(
                        Modifier
                            .size(BadgerSize.minimumTouchTarget)
                            .clip(RoundedCornerShape(BadgerRadius.chip))
                    )
                }
            }
        }
    }
}

private const val TAG = "TodoPageTester"
