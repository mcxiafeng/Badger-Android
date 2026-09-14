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
