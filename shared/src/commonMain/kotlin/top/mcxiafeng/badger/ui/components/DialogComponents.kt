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

@Composable
fun DialogButtonRow(
    negativeText: String = "取消",
    positiveText: String = "确定",
    onNegative: () -> Unit,
    onPositive: () -> Unit,
    positiveEnabled: Boolean = true,
    isDestructive: Boolean = false
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        TextButton(
            text = negativeText,
            onClick = onNegative,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(20.dp))
        TextButton(
            text = positiveText,
            onClick = onPositive,
            modifier = Modifier.weight(1f),
            enabled = positiveEnabled,
            colors = if (isDestructive) {
                ButtonDefaults.textButtonColorsPrimary().copy(
                    color = MiuixTheme.colorScheme.error,
                    textColor = MiuixTheme.colorScheme.onError
                )
            } else ButtonDefaults.textButtonColorsPrimary()
        )
    }
}
