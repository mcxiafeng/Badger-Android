package top.mcxiafeng.badger.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/**
 * 统一底部弹窗组件
 *
 * 基于 miuix [WindowBottomSheet] 封装，提供一致的底部弹窗样式：
 * - 标题
 * - 内容区域（通过 [content] lambda 自定义）
 * - 可选底部按钮行（取消/确认）
 *
 * @param show 是否显示
 * @param title 弹窗标题
 * @param onDismissRequest 关闭回调
 * @param negativeText 取消按钮文字（null 则不显示）
 * @param positiveText 确认按钮文字（null 则不显示）
 * @param onNegative 取消按钮回调（默认调用 onDismissRequest）
 * @param onPositive 确认按钮回调
 * @param positiveEnabled 确认按钮是否可用
 * @param isDestructive 确认按钮是否为危险操作（红色）
 * @param showButtons 是否显示按钮行（默认 true）
 * @param content 弹窗内容
 */
@Composable
fun BadgerBottomSheet(
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
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!show) return
    WindowBottomSheet(
        show = show,
        title = title,
        onDismissRequest = onDismissRequest,
        defaultWindowInsetsPadding = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = BadgerSpacing.sm),
        ) {
            content()
            if (showButtons) {
                Spacer(modifier = Modifier.height(BadgerSpacing.md))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.md),
                ) {
                    if (negativeText != null) {
                        TextButton(
                            text = negativeText,
                            onClick = onNegative ?: onDismissRequest,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (positiveText != null) {
                        TextButton(
                            text = positiveText,
                            onClick = onPositive ?: {},
                            modifier = Modifier.weight(1f),
                            enabled = positiveEnabled,
                            colors = if (isDestructive) {
                                ButtonDefaults.textButtonColorsPrimary().copy(
                                    color = MiuixTheme.colorScheme.error,
                                    textColor = MiuixTheme.colorScheme.onError,
                                )
                            } else {
                                ButtonDefaults.textButtonColorsPrimary()
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 选择列表底部弹窗
 *
 * 适用于从列表中选择一项的场景。
 *
 * @param show 是否显示
 * @param title 弹窗标题
 * @param options 选项列表
 * @param selectedOption 当前选中的选项（null 表示未选中）
 * @param onOptionSelected 选项点击回调
 * @param onDismiss 关闭回调
 */
@Composable
fun <T> BadgerSelectionSheet(
    show: Boolean,
    title: String,
    options: List<T>,
    selectedOption: T?,
    onOptionSelected: (T) -> Unit,
    onDismiss: () -> Unit,
    optionLabel: (T) -> String = { it.toString() },
) {
    BadgerBottomSheet(
        show = show,
        title = title,
        onDismissRequest = onDismiss,
        showButtons = false,
    ) {
        options.forEach { option ->
            val label = optionLabel(option)
            val isSelected = option == selectedOption
            BadgerListItem(
                title = label,
                onClick = {
                    onOptionSelected(option)
                    onDismiss()
                },
                onClickLabel = "选择 $label",
                role = Role.RadioButton,
                endContent = if (isSelected) {
                    {
                        Text(
                            text = "✓",
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                } else null,
            )
        }
    }
}
