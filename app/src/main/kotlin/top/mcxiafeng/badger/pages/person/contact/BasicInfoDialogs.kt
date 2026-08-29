package top.mcxiafeng.badger.pages.person.contact

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.mcxiafeng.badger.ui.components.BadgerDialog
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 性别选择 Dialog(Miuix NumberPicker 滚轮)
 *
 * 三项:男 / 女 / 其他。NumberPicker 是滚轮数字选择器,通过 label 把数字映射成中文显示。
 * textStyle 用 FontWeight.Light 让选中项字体细。
 *
 * 基于 [BadgerDialog] 封装。
 */
@Composable
fun GenderPickerDialog(
    show: Boolean,
    current: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val options = listOf("男", "女", "其他")
    val initialIndex = options.indexOfFirst { it == current }.coerceAtLeast(0)
    var selectedIndex by remember(current, show) { mutableIntStateOf(initialIndex) }

    val slimTextStyle = MiuixTheme.textStyles.body1.copy(
        fontWeight = FontWeight.Light,
        fontSize = 18.sp,
    )

    BadgerDialog(
        show = show,
        title = "选择性别",
        onDismissRequest = onDismiss,
        onPositive = { onConfirm(options[selectedIndex]) },
    ) {
        NumberPicker(
            value = selectedIndex,
            onValueChange = { selectedIndex = it },
            range = 0..2,
            label = { options[it] },
            textStyle = slimTextStyle,
            modifier = Modifier.fillMaxWidth().height(160.dp),
        )
    }
}

/**
 * 生日选择 Dialog(年/月/日 3 列 NumberPicker)
 *
 * 基于 [BadgerDialog] 封装。
 */
@Composable
fun BirthdayPickerDialog(
    show: Boolean,
    current: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val (initYear, initMonth, initDay) = remember(current) { parseBirthday(current) }
    val thisYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val minYear = 1900

    var year by remember(current, show) { mutableIntStateOf(initYear) }
    var month by remember(current, show) { mutableIntStateOf(initMonth) }
    var day by remember(current, show) { mutableIntStateOf(initDay) }

    val maxDay = remember(year, month) { daysInMonth(year, month) }
    val safeDay = if (day > maxDay) maxDay else day

    val slimTextStyle = MiuixTheme.textStyles.body1.copy(
        fontWeight = FontWeight.Light,
        fontSize = 16.sp,
    )

    BadgerDialog(
        show = show,
        title = "选择生日",
        onDismissRequest = onDismiss,
        onPositive = {
            val formatted = String.format(Locale.US, "%04d-%02d-%02d", year, month, safeDay)
            onConfirm(formatted)
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(160.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NumberPicker(
                value = year,
                onValueChange = { year = it },
                range = minYear..thisYear,
                label = { "${it}" },
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
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = String.format(Locale.US, "%04d-%02d-%02d", year, month, safeDay),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun parseBirthday(input: String?): Triple<Int, Int, Int> {
    if (input.isNullOrBlank()) return Triple(Calendar.getInstance().get(Calendar.YEAR), 1, 1)
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = sdf.parse(input) ?: return Triple(Calendar.getInstance().get(Calendar.YEAR), 1, 1)
        val cal = Calendar.getInstance().apply { time = date }
        Triple(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    } catch (_: Exception) {
        Triple(Calendar.getInstance().get(Calendar.YEAR), 1, 1)
    }
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
    else -> 31
}