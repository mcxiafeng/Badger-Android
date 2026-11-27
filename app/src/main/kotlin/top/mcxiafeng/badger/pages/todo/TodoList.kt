package top.mcxiafeng.badger.pages.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.glass.scrollTopFade
import top.mcxiafeng.badger.ui.glass.springOverscroll
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun TodoList(
    state: TodoUiState, viewModel: TodoViewModel, onEdit: (String) -> Unit, padding: PaddingValues, list: LazyListState,
    header: @Composable () -> Unit,
) {
    val direction = LocalLayoutDirection.current
    val bottomInset = LocalFloatingBarBottomPadding.current + padding.calculateBottomPadding() +
        if (state.reordering || state.selectedIds.isNotEmpty()) BadgerSize.floatingActionClearance else 0.dp
    val drag = with(LocalDensity.current) {
        val inset = bottomInset.toPx()
        val edge = BadgerSize.minimumTouchTarget.toPx()
        remember(list, inset, edge) { TodoDragState(list, inset, edge) }
    }
    val pending = remember(state.pending, state.reordering) {
        if (state.reordering) TodoDragSection(state.pending) else null
    }
    val completed = remember(state.completed, state.reordering) {
        if (state.reordering) TodoDragSection(state.completed) else null
    }
    val canEdit = state.loaded && !state.saving
    val dragId = drag.activeId
    LaunchedEffect(drag, dragId) { if (dragId != null) drag.autoScroll() }
    LazyColumn(
        modifier = Modifier.fillMaxSize().scrollTopFade().springOverscroll().then(if (state.reordering && canEdit)
            Modifier.todoDragInput(drag, pending, completed, viewModel::move) else Modifier),
        state = list,
        contentPadding = PaddingValues(start = BadgerSpacing.xl + padding.calculateStartPadding(direction),
            end = BadgerSpacing.xl + padding.calculateEndPadding(direction),
            top = padding.calculateTopPadding() + BadgerSpacing.md + BadgerSpacing.scrollContentTopGap,
            bottom = bottomInset + BadgerSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(BadgerSpacing.cardGap),
    ) {
        item(key = "todo_page_header", contentType = "page_header") { header() }
        todoGroup(state.pending, pending, drag, canEdit, viewModel, onEdit, state)
        if (state.completed.isNotEmpty()) {
            item(key = COMPLETED_HEADER_KEY) { SmallTitle("已完成") }
            todoGroup(state.completed, completed, drag, canEdit, viewModel, onEdit, state)
        }
    }
}

private fun LazyListScope.todoGroup(
    rows: List<TodoRowState>, section: TodoDragSection?, drag: TodoDragState,
    enabled: Boolean, viewModel: TodoViewModel, onEdit: (String) -> Unit, state: TodoUiState,
) {
    itemsIndexed(rows, key = { _, row -> row.item.id }) { index, row ->
        val id = row.item.id
        val active = drag.activeId == id
        val indicator = MiuixTheme.colorScheme.primary
        val placement = if (active) Modifier.zIndex(1f).graphicsLayer { translationY = drag.translationY } else Modifier.animateItem()
        val modifier = placement.drawWithContent {
            drawContent()
            if (drag.target?.id == id) {
                val width = BadgerSpacing.xs.toPx()
                val y = if (drag.target?.after == true) size.height - width / 2 else width / 2
                drawLine(indicator, Offset(0f, y), Offset(size.width, y), strokeWidth = width)
            }
        }
        val dragActions = if (section == null || rows.size < 2) null else if (enabled) {
            reorderActions(section, index, viewModel::move)
        } else emptyList()
        TodoRow(row, enabled && section == null, viewModel, onEdit, modifier, dragActions,
            selected = if (state.selecting) id in state.selectedIds else null,
            onSelect = { viewModel.toggleSelection(id) })
    }
}

private const val COMPLETED_HEADER_KEY = 0

private fun reorderActions(section: TodoDragSection, index: Int, onMove: (String, String, Boolean) -> Unit) = buildList {
    val id = section.ids[index]
    section.ids.getOrNull(index - 1)?.let { previous ->
        add(CustomAccessibilityAction("上移待办") { onMove(id, previous, false); true })
    }
    section.ids.getOrNull(index + 1)?.let { next ->
        add(CustomAccessibilityAction("下移待办") { onMove(id, next, true); true })
    }
}
