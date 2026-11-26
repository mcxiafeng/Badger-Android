package top.mcxiafeng.badger.pages.todo

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import java.time.Instant
import java.time.LocalTime
import top.mcxiafeng.badger.data.todo.TodoReminder
import top.mcxiafeng.badger.data.todo.at
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.glass.WindowDialog
import top.mcxiafeng.badger.ui.components.Card
import top.yukonga.miuix.kmp.basic.Text
import top.mcxiafeng.badger.ui.components.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun TodoScheduleFields(draft: TodoDraft, enabled: Boolean, onChange: (TodoDraft) -> Unit) {
    var selectTime by rememberSaveable { mutableStateOf(false) }
    var selectReminder by rememberSaveable { mutableStateOf(false) }
    val hasDate = draft.date.isNotBlank()
    Card(cornerRadius = BadgerRadius.surface, insideMargin = PaddingValues()) {
        TodoDateField(draft.date, enabled) { onChange(draft.withDate(it)) }
        TodoOptionalField("时间", draft.time, if (hasDate) "全天" else "先设置日期",
            enabled && hasDate, { selectTime = true }) { onChange(draft.copy(time = "")) }
        val reminder = draft.reminder
        val reminderSummary = reminder?.let {
            val day = if (it.daysBefore == 0L) "当天" else "提前 ${it.daysBefore} 天"
            val expired = if (hasDate && !it.at(draft.date).isAfter(Instant.now())) " · 已过提醒时间" else ""
            "$day ${it.time}$expired"
        }.orEmpty()
        TodoOptionalField("提醒", reminderSummary, if (hasDate) "不提醒" else "先设置日期",
            enabled && hasDate, { selectReminder = true }) { onChange(draft.copy(reminder = null)) }
        if (reminder != null && !draft.completed) TodoReminderPermission()
        draft.scheduleError?.let {
            Text(it, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.error,
                modifier = Modifier.padding(BadgerSpacing.lg))
        }
    }
    if (selectTime && hasDate) TodoTimePicker(
        value = draft.time.takeIf { it.isNotBlank() }?.let(LocalTime::parse) ?: DEFAULT_CLOCK,
        onChange = { onChange(draft.copy(time = it)) }, onDismiss = { selectTime = false },
    )
    if (selectReminder && hasDate) TodoReminderDialog(draft.reminder, onDismiss = { selectReminder = false }) {
        selectReminder = false
        onChange(draft.copy(reminder = it))
    }
}

@Composable
private fun TodoReminderDialog(value: TodoReminder?, onDismiss: () -> Unit, onConfirm: (TodoReminder) -> Unit) {
    var days by rememberSaveable { mutableStateOf((value?.daysBefore ?: DEFAULT_DAYS_BEFORE).toString()) }
    var clock by rememberSaveable { mutableStateOf(value?.time ?: DEFAULT_CLOCK.toString()) }
    var selectTime by rememberSaveable { mutableStateOf(false) }
    val dayCount = days.toIntOrNull()?.takeIf { it >= 0 }
    WindowDialog(show = true, title = "提醒时间", onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(BadgerSpacing.sm)) {
            TextField(cornerRadius = BadgerRadius.surface, value = days, onValueChange = { days = it }, label = "提前天数（0 为当天）",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                modifier = Modifier.fillMaxWidth())
            ArrowPreference(title = "提醒钟点", summary = clock, onClick = { selectTime = true })
        }
        TodoDialogActions("确认", onDismiss, enabled = dayCount != null, onConfirm = {
            if (dayCount != null) {
                Log.d(TAG, "Selected task reminder")
                onConfirm(TodoReminder(dayCount.toLong(), clock))
            }
        })
        if (selectTime) TodoTimePicker(LocalTime.parse(clock), { clock = it }, { selectTime = false })
    }
}

private const val TAG = "TodoScheduleFieldsTester"
private const val DEFAULT_DAYS_BEFORE = 1L
private val DEFAULT_CLOCK = LocalTime.of(9, 0)
