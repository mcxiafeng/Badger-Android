package top.mcxiafeng.badger.pages.todo

import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import top.mcxiafeng.badger.data.todo.TodoSortMode
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider

@Composable
internal fun TodoSortMenu(sort: TodoSortMode, enabled: Boolean, onSort: (TodoSortMode) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }, enabled = enabled,
        minWidth = BadgerSize.minimumTouchTarget, minHeight = BadgerSize.minimumTouchTarget,
        modifier = Modifier.semantics { stateDescription = sort.label }) {
        Icon(Icons.AutoMirrored.Filled.Sort, "待办排序")
    }
    OverlayListPopup(show = open, alignment = PopupPositionProvider.Align.End, onDismissRequest = { open = false }) {
        ListPopupColumn {
            TodoSortMode.entries.forEachIndexed { index, mode ->
                DropdownImpl(text = mode.label, optionSize = TodoSortMode.entries.size, isSelected = mode == sort,
                    index = index, onSelectedIndexChange = {
                        open = false
                        if (mode == TodoSortMode.MANUAL || mode != sort) {
                            Log.d(TAG, "Selected task sort mode: $mode")
                            onSort(mode)
                        }
                    })
            }
        }
    }
}

private const val TAG = "TodoSortMenuTester"
