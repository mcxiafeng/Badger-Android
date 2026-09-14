package top.mcxiafeng.badger.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun BadgerDialog(
    show: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    summary: String? = null,
    negativeText: String? = "取消",
    positiveText: String? = "确定",
    onNegative: (() -> Unit)? = null,
    onPositive: (() -> Unit)? = null,
    positiveEnabled: Boolean = true,
    isDestructive: Boolean = false,
    showButtons: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!show) return
    WindowDialog(
        show = show,
        title = title,
        summary = summary,
        onDismissRequest = onDismissRequest,
    ) {
        content()
        if (showButtons) {
            Spacer(modifier = Modifier.height(BadgerSpacing.lg))
            DialogButtonRow(
                negativeText = negativeText ?: "",
                positiveText = positiveText ?: "",
                onNegative = onNegative ?: onDismissRequest,
                onPositive = onPositive ?: {},
                positiveEnabled = positiveEnabled,
                isDestructive = isDestructive,
            )
        }
    }
}

@Composable
fun BadgerConfirmDialog(
    show: Boolean,
    title: String,
    message: String,
    confirmText: String = "确定",
    cancelText: String = "取消",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    BadgerDialog(
        show = show,
        title = title,
        onDismissRequest = onDismiss,
        negativeText = cancelText,
        positiveText = confirmText,
        onNegative = onDismiss,
        onPositive = onConfirm,
        isDestructive = isDestructive,
    ) {
        top.yukonga.miuix.kmp.basic.Text(
            text = message,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onBackground,
        )
    }
}

@Composable
fun BadgerInputDialog(
    show: Boolean,
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    confirmText: String = "确定",
    cancelText: String = "取消",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    BadgerDialog(
        show = show,
        title = title,
        onDismissRequest = onDismiss,
        negativeText = cancelText,
        positiveText = confirmText,
        onNegative = onDismiss,
        onPositive = { onConfirm(value) },
        positiveEnabled = value.isNotBlank(),
    ) {
        top.yukonga.miuix.kmp.basic.TextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
