package top.mcxiafeng.badger.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.utils.BadgerLog
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

private const val TAG = "AlertTester"

/**
 * 通用弹窗：标题 + 说明 + 可选内容槽位 + [取消 | 确认] 双按钮。
 *
 * 内部复用 Miuix [WindowDialog]（手机端底部弹出、大屏居中，点外部/返回键即取消），
 * 调用方只需持有 `show` 状态并在回调里复位：
 *
 * ```
 * var showDeleteAlert by remember { mutableStateOf(false) }
 * if (showDeleteAlert) {
 *     Alert(
 *         show = true,
 *         title = "删除联系人",
 *         summary = "删除后不可恢复，确定要删除吗？",
 *         confirmText = "删除",
 *         onConfirm = { showDeleteAlert = false; /* 执行删除 */ },
 *         onDismiss = { showDeleteAlert = false },
 *     )
 * }
 * ``` *
 * 需要输入框等自定义内容时传 [content]（排在按钮行上方），配合 [positiveEnabled]
 * 可实现"输入为空时确认置灰"（用法见 CollectionEditDialog）。
 *
 * @param show            是否展示（三处复位路径：确认、取消、点外部/返回键）
 * @param title           标题（必填，说明要确认的操作）
 * @param summary         补充说明，可空
 * @param confirmText     确认按钮文案
 * @param cancelText      取消按钮文案
 * @param positiveEnabled 确认按钮是否可点（false 时置灰）
 * @param onConfirm       点确认按钮时回调（不含取消语义，show 由调用方复位）
 * @param onDismiss       点外部 / 返回键时回调（未传 [onCancel] 时取消按钮也走这里）
 * @param onCancel        点取消按钮时回调，与"点外部"分离（冲突二选一等需要区分取消与放弃的场景用）
 * @param content         按钮行上方的自定义内容（输入框等），可空
 */
@Composable
fun Alert(
    show: Boolean,
    title: String,
    summary: String? = null,
    confirmText: String = "确认",
    cancelText: String = "取消",
    positiveEnabled: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onCancel: (() -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    WindowDialog(
        show = show,
        title = title,
        summary = summary,
        onDismissRequest = {
            BadgerLog.d(TAG, "Alert dismissed by outside tap or back gesture")
            onDismiss()
        },
    ) {
        content?.invoke()
        if (content != null) {
            Spacer(modifier = Modifier.height(BadgerSpacing.lg))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.md),
        ) {
            TextButton(
                text = cancelText,
                onClick = {
                    BadgerLog.d(TAG, "Alert cancelled")
                    (onCancel ?: onDismiss)()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColors(),
            )
            TextButton(
                text = confirmText,
                onClick = {
                    BadgerLog.d(TAG, "Alert confirmed")
                    onConfirm()
                },
                enabled = positiveEnabled,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
