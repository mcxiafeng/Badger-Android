package top.mcxiafeng.badger.pages.person.contact

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.components.DialogButtonRow
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 个人介绍编辑 Dialog
 *
 * 自由文本输入,保存后调 [onSave]（值为 null 表示清空）。
 * 不修改 bio 长度限制;用户可粘贴多行介绍。
 *
 * @param currentBio 当前 bio（空字符串或 null 时显示空 TextField）
 */
@Composable
internal fun ContactDetailBioEditDialog(
    show: Boolean,
    currentBio: String?,
    onDismiss: () -> Unit,
    onSave: (newBio: String?) -> Unit,
) {
    if (!show) return
    WindowDialog(
        show = true,
        title = "编辑个人介绍",
        summary = "",
        onDismissRequest = onDismiss,
    ) {
        var editText by remember(currentBio) { mutableStateOf(currentBio.orEmpty()) }
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = editText,
                onValueChange = { editText = it },
                label = "个人介绍",
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minLines = 3,
                maxLines = 6,
            )
            Spacer(modifier = Modifier.height(16.dp))
            DialogButtonRow(
                positiveText = "保存",
                onNegative = onDismiss,
                onPositive = {
                    val trimmed = editText.trim()
                    onSave(trimmed.ifBlank { null })
                    onDismiss()
                }
            )
        }
    }
}
