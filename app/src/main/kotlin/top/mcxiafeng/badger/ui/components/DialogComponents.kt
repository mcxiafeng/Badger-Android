package top.mcxiafeng.badger.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing

@Composable
fun DialogButtonRow(
    negativeText: String? = null,
    positiveText: String? = null,
    onNegative: () -> Unit = {},
    onPositive: () -> Unit = {},
    positiveEnabled: Boolean = true,
    isDestructive: Boolean = false,
) {
    if (negativeText == null && positiveText == null) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.md),
        modifier = Modifier.fillMaxWidth(),
    ) {
        negativeText?.let { text ->
            TextButton(
                text = text,
                onClick = onNegative,
                modifier = if (positiveText != null) Modifier.weight(1f) else Modifier.fillMaxWidth(),
            )
        }

        positiveText?.let { text ->
            TextButton(
                text = text,
                onClick = onPositive,
                modifier = if (negativeText != null) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                enabled = positiveEnabled,
                colors = if (isDestructive) {
                    ButtonDefaults.textButtonColorsPrimary().copy(
                        color = MiuixTheme.colorScheme.error,
                        textColor = MiuixTheme.colorScheme.onError,
                    )
                } else ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
