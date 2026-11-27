package top.mcxiafeng.badger.pages.todo

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import java.time.LocalDate
import java.time.LocalTime
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.glass.TextButton
import top.mcxiafeng.badger.ui.glass.WindowDialog
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.mcxiafeng.badger.ui.components.destructiveTextButtonColors
import top.yukonga.miuix.kmp.basic.Icon

@Composable
internal fun TodoDateField(value: String, enabled: Boolean, onChange: (String) -> Unit) {
    var selecting by rememberSaveable { mutableStateOf(false) }
    TodoOptionalField("日期", value, "未设置", enabled, { selecting = true }) { onChange("") }
    if (!selecting) return
    val context = LocalContext.current
    val date = remember(value) { if (value.isBlank()) LocalDate.now() else LocalDate.parse(value) }
    val update by rememberUpdatedState(onChange)
    DisposableEffect(context, date) {
        val dialog = DatePickerDialog(context, { _, year, month, day ->
            Log.d(TAG, "Selected task date")
            update(LocalDate.of(year, month + CALENDAR_MONTH_OFFSET, day).toString())
        }, date.year, date.monthValue - CALENDAR_MONTH_OFFSET, date.dayOfMonth)
        dialog.setOnDismissListener { selecting = false }
        dialog.show()
        onDispose {
            dialog.setOnDismissListener(null)
            dialog.dismiss()
        }
    }
}

@Composable
internal fun TodoTimePicker(value: LocalTime, onChange: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val update by rememberUpdatedState(onChange)
    val dismiss by rememberUpdatedState(onDismiss)
    DisposableEffect(context, value) {
        val dialog = TimePickerDialog(context, { _, hour, minute ->
            Log.d(TAG, "Selected task clock time")
            update(LocalTime.of(hour, minute).toString())
        }, value.hour, value.minute, true)
        dialog.setOnDismissListener { dismiss() }
        dialog.show()
        onDispose {
            dialog.setOnDismissListener(null)
            dialog.dismiss()
        }
    }
}

/**
 * One press target for the whole row: the clear action sits inside the component's end actions,
 * so the press/focus indication spans the full width instead of stopping before the close icon.
 */
@Composable
internal fun TodoOptionalField(
    title: String, value: String, placeholder: String, enabled: Boolean,
    onSelect: () -> Unit, onClear: () -> Unit,
) {
    BasicComponent(
        title = title, summary = value.ifBlank { placeholder }, enabled = enabled, onClick = onSelect,
        endActions = if (value.isNotBlank()) {
            { TodoClearAction(title, enabled, onClear) }
        } else null,
    )
}

@Composable
private fun TodoClearAction(title: String, enabled: Boolean, onClear: () -> Unit) {
    Box(Modifier.size(BadgerSize.minimumTouchTarget)
        .clickable(enabled = enabled, role = Role.Button, onClickLabel = "清除$title") {
            Log.d(TAG, "Cleared optional task field")
            onClear()
        }, contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Close, "清除$title")
    }
}

@Composable
internal fun ConfirmTodoDialog(
    title: String, message: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit,
) {
    WindowDialog(show = true, title = title, summary = message, onDismissRequest = onDismiss) {
        TodoDialogActions(action, onDismiss, onConfirm)
    }
}

@Composable
internal fun TodoDialogActions(
    action: String, onDismiss: () -> Unit, onConfirm: () -> Unit, enabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth().padding(top = BadgerSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.md)) {
        TextButton(text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
        TextButton(text = action, enabled = enabled, modifier = Modifier.weight(1f),
            colors = destructiveTextButtonColors(), onClick = {
                Log.d(TAG, "Confirmed task dialog action")
                onConfirm()
            })
    }
}

private const val TAG = "TodoDialogsTester"
private const val CALENDAR_MONTH_OFFSET = 1
