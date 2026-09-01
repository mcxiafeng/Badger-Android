package top.mcxiafeng.badger.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 统一对话框组件
 *
 * 基于 miuix [WindowDialog] 封装，提供一致的对话框样式：
 * - 标题（由 WindowDialog 提供）
 * - 内容区域（通过 [content] lambda 自定义）
 * - 可选底部按钮行（取消/确认）
 *
 * @param show 是否显示
 * @param title 对话框标题
 * @param onDismissRequest 关闭回调
 * @param negativeText 取消按钮文字（null 则不显示）
 * @param positiveText 确认按钮文字（null 则不显示）
 * @param onNegative 取消按钮回调（默认调用 onDismissRequest）
 * @param onPositive 确认按钮回调
 * @param positiveEnabled 确认按钮是否可用
 * @param isDestructive 确认按钮是否为危险操作（红色）
 * @param showButtons 是否显示按钮行（默认 true）
 * @param content 对话框内容
 */
@Composable
fun BadgerDialog(
    show: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
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
        onDismissRequest = onDismissRequest,
    ) {
        content()
        if (showButtons && (negativeText != null || positiveText != null)) {
            Spacer(modifier = Modifier.height(BadgerSpacing.lg))
            DialogButtonRow(
                negativeText = negativeText,
                positiveText = positiveText,
                onNegative = onNegative ?: onDismissRequest,
                onPositive = onPositive ?: {},
                positiveEnabled = positiveEnabled,
                isDestructive = isDestructive,
            )
        }
    }
}

/**
 * 确认对话框
 *
 * 简化版对话框，适用于简单的确认/取消场景。
 */
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

/**
 * 输入对话框
 *
 * 带文本输入框的对话框，适用于重命名等场景。
 * 空白字符串不允许提交，但不会强制修改用户实际输入内容。
 */
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
        positiveEnabled = value.trim().isNotEmpty(),
    ) {
        top.yukonga.miuix.kmp.basic.TextField(
            value = value,
            onValueChange = onValueChange,
            label = label.ifBlank { placeholder },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
