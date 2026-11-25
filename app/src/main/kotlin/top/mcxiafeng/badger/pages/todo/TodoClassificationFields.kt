package top.mcxiafeng.badger.pages.todo

import android.util.Log
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import top.mcxiafeng.badger.data.todo.TodoPriority
import top.mcxiafeng.badger.data.todo.TodoType
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.glass.WindowDialog
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.mcxiafeng.badger.ui.components.Card
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxPreference

@Composable
internal fun TodoClassificationFields(
    draft: TodoDraft, types: List<TodoType>, enabled: Boolean, onChange: (TodoDraft) -> Unit,
    onManageTypes: () -> Unit,
) {
    var selectPriority by rememberSaveable { mutableStateOf(false) }
    var selectType by rememberSaveable { mutableStateOf(false) }
    val activeType = types.find { it.id == draft.typeId }
    Card(cornerRadius = BadgerRadius.surface, insideMargin = PaddingValues()) {
        ArrowPreference(title = "优先级", summary = draft.priority.label, enabled = enabled,
            onClick = { selectPriority = true })
        ArrowPreference(title = "待办类型", summary = activeType?.name ?: "无类型", enabled = enabled,
            onClick = { selectType = true }, startAction = activeType?.let { { TodoTypeSwatch(it) } })
    }
    if (selectPriority) WindowDialog(show = true, title = "优先级", onDismissRequest = { selectPriority = false }) {
        TodoPriority.entries.forEach { priority ->
            CheckboxPreference(title = "${priority.marker}${priority.label}", checked = draft.priority == priority,
                onCheckedChange = {
                    selectPriority = false
                    Log.d(TAG, "Selected task priority")
                    onChange(draft.copy(priority = priority))
                })
        }
    }
    if (selectType) TodoTypePicker(types, activeType, onDismiss = { selectType = false }, onSelect = {
        selectType = false
        Log.d(TAG, "Selected task type")
        onChange(draft.copy(typeId = it))
    }, onManage = {
        selectType = false
        onManageTypes()
    })
}

@Composable
private fun TodoTypePicker(
    types: List<TodoType>, active: TodoType?, onDismiss: () -> Unit, onSelect: (String) -> Unit,
    onManage: () -> Unit,
) {
    WindowDialog(show = true, title = "待办类型", onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = BadgerSize.minimumTouchTarget * TYPE_PICKER_VISIBLE_ROWS)) {
            item { TodoTypeOption("无类型", checked = active == null) { onSelect("") } }
            items(types, key = { it.id }) { type ->
                TodoTypeOption(type.name, type, checked = active?.id == type.id) { onSelect(type.id) }
            }
        }
        ArrowPreference(title = "管理类型", onClick = onManage)
    }
}

@Composable
internal fun TodoTypeOption(
    title: String, type: TodoType? = null, checked: Boolean = false, onClick: (() -> Unit)? = null,
) {
    val swatch: (@Composable () -> Unit)? = if (type == null) null else { { TodoTypeSwatch(type) } }
    if (onClick == null) BasicComponent(title = title, startAction = swatch)
    else CheckboxPreference(title = title, checked = checked, startAction = swatch, onCheckedChange = { onClick() })
}

private const val TYPE_PICKER_VISIBLE_ROWS = 6
private const val TAG = "TodoClassificationFieldsTester"
