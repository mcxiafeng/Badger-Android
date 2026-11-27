package top.mcxiafeng.badger.pages.todo

import android.util.Log
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import top.mcxiafeng.badger.data.todo.TodoSortMode
import top.mcxiafeng.badger.ui.designsystem.BadgerAlpha
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.glass.IconButton
import top.mcxiafeng.badger.ui.glass.ListPopupColumn
import top.mcxiafeng.badger.ui.glass.OverlayListPopup
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val TodoMenuScrimAlpha = SemanticsPropertyKey<Float>("TodoMenuScrimAlpha")

@Composable
internal fun TodoMoreMenu(
    sort: TodoSortMode, enabled: Boolean, onSort: (TodoSortMode) -> Unit,
    onManageTypes: () -> Unit, onSelect: () -> Unit, onClearCompleted: () -> Unit,
    completedCount: Int,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    var sorting by rememberSaveable { mutableStateOf(false) }
    val menuSurface = MiuixTheme.colorScheme.surfaceContainer
    val close: () -> Unit = {
        sorting = false
        open = false
        Log.d(TAG, "Closed all task menu levels")
    }
    Box {
        IconButton(onClick = {
            sorting = false
            open = true
            Log.d(TAG, "Opened task more menu")
        }, enabled = enabled) { Icon(Icons.Default.MoreHoriz, "更多待办操作") }
        OverlayListPopup(show = open, alignment = PopupPositionProvider.Align.End, onDismissRequest = close) {
            TodoMoreOptions(sort, open && sorting, menuSurface, onManageTypes = { close(); onManageTypes() },
                onSelect = { close(); onSelect() }, onOpenSort = {
                    sorting = true
                    Log.d(TAG, "Opened sorting-method submenu")
                }, onDismissSort = { sorting = false }, onSort = { mode ->
                    close()
                    if (mode == TodoSortMode.MANUAL || mode != sort) onSort(mode)
                }, onClearCompleted = { close(); onClearCompleted() }, completedCount = completedCount)
        }
    }
}

@Composable
private fun TodoMoreOptions(
    sort: TodoSortMode, sorting: Boolean, menuSurface: Color, onManageTypes: () -> Unit, onSelect: () -> Unit,
    onOpenSort: () -> Unit, onDismissSort: () -> Unit, onSort: (TodoSortMode) -> Unit,
    onClearCompleted: () -> Unit, completedCount: Int,
) {
    TodoMenuSurface(dimmed = sorting, tag = "todo_more_menu") {
        val options = listOf(
            TYPE_MANAGER_TITLE to onManageTypes,
            SORT_METHOD_TITLE to onOpenSort,
            "多选" to onSelect,
            CLEAR_COMPLETED_TITLE to onClearCompleted,
        )
        ListPopupColumn {
            options.forEachIndexed { index, (label, action) ->
                // Both rows lead somewhere further, so both carry the chevron; only the sorting row
                // opens a nested popup here, while type management navigates to its own page.
                val showsChevron = label == SORT_METHOD_TITLE || label == TYPE_MANAGER_TITLE
                val opensSubmenu = label == SORT_METHOD_TITLE
                val disabled = label == CLEAR_COMPLETED_TITLE && completedCount == 0
                Box(propagateMinConstraints = true) {
                    TodoMenuItem(label, index, options.size, expandable = showsChevron,
                        enabled = !disabled, onClick = {
                            if (disabled) {
                                Log.d(TAG, "Ignored clear-completed entry; nothing is completed")
                            } else action()
                        })
                    if (opensSubmenu) MiuixTheme(colors = MiuixTheme.colorScheme.copy(surfaceContainer = menuSurface)) {
                        TodoSortMenu(sorting, sort, onDismissSort, onSort)
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoSortMenu(show: Boolean, sort: TodoSortMode, onDismiss: () -> Unit, onSort: (TodoSortMode) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf<TodoSortMode?>(null) }
    val menuSurface = MiuixTheme.colorScheme.surfaceContainer
    LaunchedEffect(show) {
        if (!show) expanded = null
        Log.d(TAG, "Sorting-method menu visibility=$show; directionOpen=${expanded != null}")
    }
    TodoNestedPopup(show, onDismiss) {
        TodoMenuSurface(dimmed = show && expanded != null, tag = "todo_sort_menu") {
            ListPopupColumn {
                TodoMenuHeading(SORT_METHOD_TITLE, onDismiss)
                TodoSortMode.categories.forEachIndexed { index, category ->
                    Box(propagateMinConstraints = true) {
                        TodoMenuItem(category.label, index, TodoSortMode.categories.size,
                            expandable = category != TodoSortMode.MANUAL, selected = sort.category == category,
                            onClick = {
                                if (category == TodoSortMode.MANUAL) onSort(category) else expanded = category
                                Log.d(TAG, "Selected sorting category=$category; expanded=${expanded != null}")
                            })
                        if (category != TodoSortMode.MANUAL) MiuixTheme(colors = MiuixTheme.colorScheme.copy(surfaceContainer = menuSurface)) {
                            TodoDirectionMenu(show && expanded == category, category, sort,
                                onDismiss = { expanded = null }, onSort = { mode -> expanded = null; onSort(mode) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoDirectionMenu(
    show: Boolean, category: TodoSortMode, sort: TodoSortMode,
    onDismiss: () -> Unit, onSort: (TodoSortMode) -> Unit,
) {
    TodoNestedPopup(show, onDismiss) {
        Box(Modifier.testTag("todo_direction_menu"), propagateMinConstraints = true) {
            ListPopupColumn {
                TodoMenuHeading(category.label, onDismiss)
                category.directions.forEachIndexed { index, mode ->
                    TodoMenuItem(mode.directionLabel ?: mode.label, index, category.directions.size, selected = mode == sort,
                        onClick = {
                            Log.d(TAG, "Selected task sort direction=$mode")
                            onSort(mode)
                        })
                }
            }
        }
    }
}

/** Dim only this surface. The target changes immediately when its direct child starts closing. */
@Composable
internal fun TodoMenuSurface(dimmed: Boolean, tag: String, content: @Composable () -> Unit) {
    val scrim = animateFloatAsState(
        targetValue = if (dimmed) BadgerAlpha.NESTED_MENU_PARENT_SCRIM_ALPHA else 0f,
        animationSpec = tween(BadgerMotion.DURATION_TOUCH_FEEDBACK, easing = FastOutSlowInEasing),
        label = "TaskMenuParentScrim",
    )
    LaunchedEffect(dimmed) {
        Log.d(TAG, "Parent scrim transition started; level=$tag; dimmed=$dimmed; durationMs=${BadgerMotion.DURATION_TOUCH_FEEDBACK}")
    }
    Box(Modifier.testTag(tag).semantics { this[TodoMenuScrimAlpha] = scrim.value }
        .drawWithContent {
            drawContent()
            val alpha = scrim.value.coerceIn(0f, BadgerAlpha.NESTED_MENU_PARENT_SCRIM_ALPHA)
            if (alpha > 0f) drawRect(Color.Black, alpha = alpha)
        }, propagateMinConstraints = true) { content() }
}

/** Use the locked library's submenu chevron and independent selected marker. */
@Composable
private fun TodoMenuItem(
    label: String, index: Int, count: Int, expandable: Boolean = false, selected: Boolean = false,
    enabled: Boolean = true, onClick: () -> Unit,
) {
    DropdownImpl(item = DropdownItem(text = label, selected = selected, enabled = enabled), optionSize = count,
        isSelected = selected, index = index, hasSubmenu = expandable, onSelectedIndexChange = {
            Log.d(TAG, "Activated task menu entry; label=$label; expandable=$expandable; enabled=$enabled")
            onClick()
        })
}

@Composable
private fun TodoNestedPopup(show: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val position = remember { TodoSortPopupPositionProvider() }
    OverlayListPopup(show = show, alignment = PopupPositionProvider.Align.End, enableWindowDim = false,
        popupPositionProvider = position, onDismissRequest = {
            Log.d(TAG, "Dismissed a nested task popup")
            onDismiss()
        }, content = content)
}

@Composable
private fun TodoMenuHeading(title: String, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = BadgerSize.minimumTouchTarget)
        .clickable(onClickLabel = "收起$title", onClick = {
            Log.d(TAG, "Collapsed task submenu from heading; title=$title")
            onDismiss()
        }).semantics(mergeDescendants = true) { contentDescription = "收起$title" }
        .padding(horizontal = BadgerSpacing.xl, vertical = BadgerSpacing.md),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.md)) {
        Text(title, Modifier.weight(1f), style = MiuixTheme.textStyles.title4)
        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
    }
}

/** The wrapper owns shadow padding; align each visible child with the tapped parent row. */
internal class TodoSortPopupPositionProvider : PopupPositionProvider {
    override fun getMargins(): PaddingValues = ListPopupDefaults.DropdownPositionProvider.getMargins()

    override fun calculatePosition(
        anchorBounds: IntRect, windowBounds: IntRect, layoutDirection: LayoutDirection,
        popupContentSize: IntSize, popupMargin: IntRect, alignment: PopupPositionProvider.Align,
    ): IntOffset {
        val minX = windowBounds.left + popupMargin.left
        val minY = windowBounds.top + popupMargin.top
        val maxX = (windowBounds.right - popupMargin.right - popupContentSize.width).coerceAtLeast(minX)
        val maxY = (windowBounds.bottom - popupMargin.bottom - popupContentSize.height).coerceAtLeast(minY)
        val x = if (layoutDirection == LayoutDirection.Rtl) anchorBounds.left else anchorBounds.right - popupContentSize.width
        return IntOffset(x.coerceIn(minX, maxX), anchorBounds.top.coerceIn(minY, maxY))
    }
}

private const val SORT_METHOD_TITLE = "排序方式"
private const val TYPE_MANAGER_TITLE = "待办类型管理"
private const val CLEAR_COMPLETED_TITLE = "清除已完成待办"
private const val TAG = "TodoSortMenuTester"
