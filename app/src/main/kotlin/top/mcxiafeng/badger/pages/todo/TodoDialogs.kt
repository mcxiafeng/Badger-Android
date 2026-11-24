package top.mcxiafeng.badger.pages.todo

import android.app.DatePickerDialog
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun TodoDateField(value: String, enabled: Boolean, onChange: (String) -> Unit) {
    var selecting by rememberSaveable { mutableStateOf(false) }
    Card(cornerRadius = BadgerRadius.card, insideMargin = PaddingValues()) {
        ArrowPreference(title = "日期", summary = value.ifBlank { "未设置" }, enabled = enabled, onClick = {
            selecting = true
            Log.d(TAG, "Opened date picker")
        })
        if (value.isNotBlank()) Text("清除日期", color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.clickable(enabled = enabled) {
                Log.d(TAG, "Cleared task date")
                onChange("")
            }.padding(BadgerSpacing.lg))
    }
    if (selecting) TodoDatePicker(value, onChange) { selecting = false }
}

@Composable
private fun TodoDatePicker(value: String, onChange: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val date = remember(value) { if (value.isBlank()) LocalDate.now() else LocalDate.parse(value) }
    DisposableEffect(context, date) {
        val dialog = DatePickerDialog(context, { _, year, month, day ->
            Log.d(TAG, "Selected task date")
            onChange(LocalDate.of(year, month + CALENDAR_MONTH_OFFSET, day).toString())
        }, date.year, date.monthValue - CALENDAR_MONTH_OFFSET, date.dayOfMonth)
        dialog.setOnDismissListener { onDismiss() }
        dialog.show()
        onDispose {
            dialog.setOnDismissListener(null)
            dialog.dismiss()
        }
    }
}

@Composable
internal fun ConfirmTodoDialog(
    title: String, message: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit,
) {
    WindowDialog(show = true, title = title, onDismissRequest = {
        Log.d(TAG, "Dismissed task confirmation")
        onDismiss()
    }) {
        Text(message)
        Row(Modifier.fillMaxWidth().padding(top = BadgerSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.md)) {
            Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
            Button(onClick = {
                Log.d(TAG, "Confirmed task action")
                onConfirm()
            }, modifier = Modifier.weight(1f)) { Text(action) }
        }
    }
}

private const val TAG = "TodoDialogsTester"
private const val CALENDAR_MONTH_OFFSET = 1
