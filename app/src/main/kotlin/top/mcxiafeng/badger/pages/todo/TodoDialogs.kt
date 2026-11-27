package top.mcxiafeng.badger.pages.todo

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.glass.TextButton
import top.mcxiafeng.badger.ui.glass.WindowDialog
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun TodoDateField(value: String, enabled: Boolean, onChange: (String) -> Unit) {
    var selecting by rememberSaveable { mutableStateOf(false) }
    TodoOptionalField("日期", value, "未设置", enabled, { selecting = true }) { onChange("") }
    if (selecting) TodoDatePickerDialog(show = true, current = value, onDismiss = { selecting = false },
        onConfirm = {
            Log.d(TAG, "Selected task date")
            onChange(it)
            selecting = false
        })
}

/** Wheel date picker matching the contact birthday dialog: three NumberPicker columns. */
@Composable
internal fun TodoDatePickerDialog(show: Boolean, current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val (initYear, initMonth, initDay) = remember(current) { parseTaskDate(current) }
    val thisYear = remember { LocalDate.now().year }
    val minYear = MIN_PICKER_YEAR

    var year by remember(current, show) { mutableIntStateOf(initYear) }
    var month by remember(current, show) { mutableIntStateOf(initMonth) }
    var day by remember(current, show) { mutableIntStateOf(initDay) }

    val maxDay = remember(year, month) { daysInMonth(year, month) }
    val safeDay = if (day > maxDay) maxDay else day

    val slimTextStyle = MiuixTheme.textStyles.body1.copy(
        fontWeight = FontWeight.Light,
        fontSize = 16.sp,
    )

    if (!show) return
    WindowDialog(show = show, title = "选择日期", onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth().height(PICKER_HEIGHT.dp),
            horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.sm),
        ) {
            NumberPicker(
                value = year,
                onValueChange = { year = it },
                range = minYear..thisYear,
                label = { "${it}年" },
                textStyle = slimTextStyle,
                modifier = Modifier.weight(1f),
            )
            NumberPicker(
                value = month,
                onValueChange = { month = it },
                range = 1..12,
                label = { "${it}月" },
                textStyle = slimTextStyle,
                modifier = Modifier.weight(1f),
            )
            NumberPicker(
                value = safeDay,
                onValueChange = { day = it },
                range = 1..maxDay,
                label = { "${it}日" },
                textStyle = slimTextStyle,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(BadgerSpacing.xs))
        Text(
            text = String.format(Locale.US, "%04d-%02d-%02d", year, month, safeDay),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        )
        TodoDialogActions("确定", onDismiss, onConfirm = {
            onConfirm(String.format(Locale.US, "%04d-%02d-%02d", year, month, safeDay))
        })
    }
}

@Composable
internal fun TodoTimePicker(value: LocalTime, onChange: (String) -> Unit, onDismiss: () -> Unit) {
    TodoTimePickerDialog(show = true, current = value, onDismiss = onDismiss, onConfirm = {
        Log.d(TAG, "Selected task clock time")
        onChange(it)
        onDismiss()
    })
}

/** Wheel clock picker matching the date dialog: hour/minute NumberPicker columns. */
@Composable
internal fun TodoTimePickerDialog(show: Boolean, current: LocalTime, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var hour by remember(current, show) { mutableIntStateOf(current.hour) }
    var minute by remember(current, show) { mutableIntStateOf(current.minute) }

    val slimTextStyle = MiuixTheme.textStyles.body1.copy(
        fontWeight = FontWeight.Light,
        fontSize = 16.sp,
    )

    if (!show) return
    WindowDialog(show = show, title = "选择时间", onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth().height(PICKER_HEIGHT.dp),
            horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.sm),
        ) {
            NumberPicker(
                value = hour,
                onValueChange = { hour = it },
                range = 0..23,
                label = { String.format(Locale.US, "%02d时", it) },
                textStyle = slimTextStyle,
                modifier = Modifier.weight(1f),
            )
            NumberPicker(
                value = minute,
                onValueChange = { minute = it },
                range = 0..59,
                label = { String.format(Locale.US, "%02d分", it) },
                textStyle = slimTextStyle,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(BadgerSpacing.xs))
        Text(
            text = String.format(Locale.US, "%02d:%02d", hour, minute),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        )
        TodoDialogActions("确定", onDismiss, onConfirm = {
            onConfirm(String.format(Locale.US, "%02d:%02d", hour, minute))
        })
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
            colors = ButtonDefaults.textButtonColorsPrimary(), onClick = {
                Log.d(TAG, "Confirmed task dialog action")
                onConfirm()
            })
    }
}

private val TASK_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

private fun parseTaskDate(input: String): Triple<Int, Int, Int> {
    if (input.isBlank()) {
        val today = LocalDate.now()
        return Triple(today.year, today.monthValue, today.dayOfMonth)
    }
    return try {
        val date = LocalDate.parse(input, TASK_DATE_FORMAT)
        Triple(date.year, date.monthValue, date.dayOfMonth)
    } catch (error: Exception) {
        Log.e(TAG, "Task date could not be parsed; falling back to today", error)
        val today = LocalDate.now()
        Triple(today.year, today.monthValue, today.dayOfMonth)
    }
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
    else -> 31
}

private const val TAG = "TodoDialogsTester"
private const val MIN_PICKER_YEAR = 2000
private const val PICKER_HEIGHT = 160
