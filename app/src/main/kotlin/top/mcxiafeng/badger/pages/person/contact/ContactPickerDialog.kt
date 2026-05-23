package top.mcxiafeng.badger.pages.person.contact

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactRepository
import top.mcxiafeng.badger.ui.components.AvatarPlaceholder
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.miuixShape
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 联系人选择器对话框（附加到已有联系人专用）
 *
 * 从已有联系人列表中搜索并选择目标联系人。
 *
 * @param repository 数据仓库
 * @param excludeContactId 需要排除的联系人ID（当前联系人自己）
 * @param onDismiss 关闭回调
 * @param onContactSelected 选中联系人回调
 */
@Composable
internal fun ContactDetailPickerDialog(
    repository: ContactRepository,
    excludeContactId: Long,
    onDismiss: () -> Unit,
    onContactSelected: (Contact) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val allContacts by repository.searchContacts(searchQuery)
        .collectAsState(initial = emptyList())
    // 排除当前联系人自己
    val contacts = remember(allContacts, excludeContactId) {
        allContacts.filter { it.id != excludeContactId }
    }

    WindowDialog(
        show = true,
        title = "选择联系人",
        onDismissRequest = onDismiss
    ) {
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            label = "搜索联系人"
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (contacts.isEmpty()) {
            Text(
                text = if (searchQuery.isEmpty()) "暂无其他联系人" else "未找到匹配的联系人",
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                contacts.forEach { contactItem ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(miuixShape(8.dp))
                            .clickable { onContactSelected(contactItem) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarPlaceholder(
                            name = contactItem.name,
                            size = 36
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = contactItem.name,
                            style = MiuixTheme.textStyles.body1
                        )
                    }
                }
            }
        }
    }
}