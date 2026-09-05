package top.mcxiafeng.badger.pages.person.contact.detail

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import top.mcxiafeng.badger.ui.components.BadgerInputDialog
import top.yukonga.miuix.kmp.basic.TextField

/**
 * 个人介绍编辑 Dialog
 *
 * 自由文本输入,保存后调 [onSave]（值为 null 表示清空）。
 * 不修改 bio 长度限制;用户可粘贴多行介绍。
 *
 * 基于 [BadgerInputDialog] 封装。
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
    var editText by remember(currentBio) { mutableStateOf(currentBio.orEmpty()) }

    BadgerInputDialog(
        show = show,
        title = "编辑个人介绍",
        value = editText,
        onValueChange = { editText = it },
        label = "个人介绍",
        confirmText = "保存",
        onConfirm = { value ->
            val trimmed = value.trim()
            onSave(trimmed.ifBlank { null })
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}
