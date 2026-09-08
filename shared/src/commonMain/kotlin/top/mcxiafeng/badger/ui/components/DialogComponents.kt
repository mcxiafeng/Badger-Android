package top.mcxiafeng.badger.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 对话框底部按钮行。
 *
 * - 双按钮（[negativeText] 非空）：取消 | 确认 各 weight(1f)，中间 20dp 间距。
 * - 单按钮（[negativeText] 为空）：[positiveText] 顶满 [fillMaxWidth]（规范：单个按钮必须顶满宽度）。
 * - [isDestructive] 时确认按钮用 error 色。
 */
@Composable
fun DialogButtonRow(
    negativeText: String = "取消",
    positiveText: String = "确定",
    onNegative: () -> Unit = {},
    onPositive: () -> Unit = {},
    positiveEnabled: Boolean = true,
    isDestructive: Boolean = false,
) {
    val positiveColors = if (isDestructive) {
        ButtonDefaults.textButtonColorsPrimary().copy(
            color = MiuixTheme.colorScheme.error,
            textColor = MiuixTheme.colorScheme.onError,
        )
    } else ButtonDefaults.textButtonColorsPrimary()

    if (negativeText.isBlank()) {
        // 单按钮：顶满宽度
        TextButton(
            text = positiveText,
            onClick = onPositive,
            modifier = Modifier.fillMaxWidth(),
            enabled = positiveEnabled,
            colors = positiveColors,
        )
        return
    }

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextButton(
            text = negativeText,
            onClick = onNegative,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(20.dp))
        TextButton(
            text = positiveText,
            onClick = onPositive,
            modifier = Modifier.weight(1f),
            enabled = positiveEnabled,
            colors = positiveColors,
        )
    }
}
