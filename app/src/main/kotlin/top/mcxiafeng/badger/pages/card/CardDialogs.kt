package top.mcxiafeng.badger.pages.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactRepository
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 创建名片夹对话框
 */
@Composable
fun CreateCollectionDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    WindowDialog(
        show = true,
        title = "新建名片夹",
        onDismissRequest = onDismiss
    ) {
        TextField(
            value = name,
            onValueChange = { name = it },
            label = "名称",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = description,
            onValueChange = { description = it },
            label = "描述（可选）",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(20.dp))
            TextButton(
                text = "创建",
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), description.trim().ifBlank { null }) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}

/**
 * 联系人选择对话框（添加联系人到名片夹）
 */
@Composable
fun ContactSelectDialog(
    repository: ContactRepository,
    existingContactIds: Set<Long>,
    onDismiss: () -> Unit,
    onContactSelected: (Contact) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val contacts by repository.searchContacts(searchQuery).collectAsState(initial = emptyList())
    val available = contacts.filter { it.id !in existingContactIds }

    WindowDialog(
        show = true,
        title = "添加联系人",
        onDismissRequest = onDismiss
    ) {
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = "搜索",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.heightIn(max = 400.dp)
        ) {
            items(available, key = { it.id }) { contact ->
                BasicComponent(
                    title = contact.name,
                    summary = contact.note,
                    onClick = { onContactSelected(contact) },
                    startAction = {
                        ContactAvatar(name = contact.name, avatarUrl = contact.avatarUrl, size = 36)
                    }
                )
            }
            if (available.isEmpty()) {
                item {
                    Text(
                        text = if (searchQuery.isBlank()) "暂无联系人" else "未找到匹配的联系人",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
