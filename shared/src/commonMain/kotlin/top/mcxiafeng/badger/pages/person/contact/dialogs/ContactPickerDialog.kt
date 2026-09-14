package top.mcxiafeng.badger.pages.person.contact.dialogs

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.ui.components.AvatarPlaceholder
import top.mcxiafeng.badger.ui.components.BadgerDialog
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.mcxiafeng.badger.utils.miuixShape

@Composable
internal fun ContactDetailPickerDialog(
    repository: ContactRepository,
    excludeContactId: Long,
    onDismiss: () -> Unit,
    onContactSelected: (Contact) -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val allContacts by repository.searchContacts(searchQuery)
        .collectAsState(initial = emptyList())
    
    val contacts = remember(allContacts, excludeContactId) {
        allContacts.filter { it.id != excludeContactId }
    }

    BadgerDialog(
        show = true,
        title = "选择联系人",
        onDismissRequest = onDismiss,
        showButtons = false,
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