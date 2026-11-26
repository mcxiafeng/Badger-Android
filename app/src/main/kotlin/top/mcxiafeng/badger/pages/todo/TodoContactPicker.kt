package top.mcxiafeng.badger.pages.todo

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import top.mcxiafeng.badger.ui.glass.itemPressFeedback
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import top.mcxiafeng.badger.ui.designsystem.BadgerShapes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.Tag
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.mcxiafeng.badger.ui.components.SkeletonListPlaceholder
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.glass.IconButton
import top.mcxiafeng.badger.ui.glass.TextButton
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.mcxiafeng.badger.ui.components.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun TodoContactPicker(
    contacts: List<Contact>, selected: List<Long>, enabled: Boolean,
    viewModel: TodoViewModel, onContactClick: (Long) -> Unit, onChange: (List<Long>) -> Unit,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    val associated = remember(contacts, selected) {
        val byId = contacts.associateBy { it.id }
        selected.mapNotNull(byId::get)
    }
    val tagFlow = remember(selected, viewModel) { viewModel.observeContactTags(selected) }
    val tags by tagFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    Column(verticalArrangement = Arrangement.spacedBy(BadgerSpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("关联联系人", modifier = Modifier.weight(1f), style = MiuixTheme.textStyles.subtitle)
            TextButton(text = if (adding) "收起搜索" else "添加联系人", enabled = enabled,
                minHeight = BadgerSize.minimumTouchTarget, onClick = { adding = !adding })
        }
        if (adding) TodoInlineContactSearch(selected, enabled, viewModel) { id ->
            Log.d(TAG, "Added a contact to the draft")
            onChange((selected + id).distinct())
        }
        if (associated.isEmpty() && !adding) {
            Text("未关联联系人", color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2)
        }
        associated.forEach { contact ->
            key(contact.id) {
                TodoAssociatedContact(contact, tags[contact.id].orEmpty(), enabled, onContactClick) {
                    Log.d(TAG, "Removed a contact from the draft")
                    onChange(selected - contact.id)
                }
            }
        }
    }
}

@Composable
private fun TodoAssociatedContact(
    contact: Contact, tags: List<Tag>, enabled: Boolean, onContactClick: (Long) -> Unit, onRemove: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = BadgerSpacing.xs)
        .itemPressFeedback(enabled, BadgerRadius.inner),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.md)) {
        Row(Modifier.weight(1f).clickable(enabled = enabled, onClickLabel = "查看联系人 ${contact.name}",
            indication = null, interactionSource = remember { MutableInteractionSource() }) {
            Log.d(TAG, "Opened associated contact detail")
            onContactClick(contact.id)
        }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.md)) {
            TodoContactAvatar(contact)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BadgerSpacing.xs)) {
                Text(contact.name, style = MiuixTheme.textStyles.body1)
                if (tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(BadgerSpacing.xs)) {
                    tags.forEach { tag ->
                        Text(tag.name, style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.background(MiuixTheme.colorScheme.surfaceContainer,
                                BadgerShapes.Surface)
                                .padding(horizontal = BadgerSpacing.sm, vertical = BadgerSpacing.xs))
                    }
                }
            }
        }
        IconButton(onClick = onRemove, enabled = enabled,
            minWidth = BadgerSize.minimumTouchTarget, minHeight = BadgerSize.minimumTouchTarget) {
            Icon(Icons.Default.Close, "移除关联联系人 ${contact.name}")
        }
    }
}

@Composable
private fun TodoInlineContactSearch(
    selected: List<Long>, enabled: Boolean, viewModel: TodoViewModel, onAdd: (Long) -> Unit,
) {
    val choices by viewModel.contactChoices.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) { viewModel.searchContacts(query) }
    TextField(cornerRadius = BadgerRadius.surface, value = query, onValueChange = { query = it }, label = "搜索姓名、账号或标签",
        enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
    val available = remember(choices.contacts, selected) { choices.contacts.filterNot { it.id in selected } }
    when {
        choices.loading || choices.query != query.trim() -> SkeletonListPlaceholder(
            modifier = Modifier.fillMaxWidth().padding(horizontal = BadgerSpacing.xl),
            rowCount = TODO_PICKER_SKELETON_ROW_COUNT,
            avatar = false,
            summary = false
        )
        choices.error != null -> TodoError(checkNotNull(choices.error), viewModel::retryContactSearch)
        available.isEmpty() -> Text("没有可添加的联系人", style = MiuixTheme.textStyles.body2)
        else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = BadgerSize.minimumTouchTarget * CONTACT_SEARCH_ROWS),
            contentPadding = PaddingValues(top = BadgerSpacing.scrollContentTopGap)) {
            items(available, key = { it.id }) { contact ->
                BasicComponent(title = contact.name, startAction = { TodoContactAvatar(contact) },
                    endActions = { Icon(Icons.Default.Add, "添加联系人 ${contact.name}") },
                    enabled = enabled, onClick = { onAdd(contact.id) })
            }
        }
    }
}

@Composable
internal fun TodoContactAvatar(contact: Contact, size: Int = CONTACT_AVATAR_SIZE) {
    ContactAvatar(name = contact.name, avatarPath = contact.avatarPath, avatarUrl = contact.avatarUrl, size = size,
        modifier = Modifier.semantics { contentDescription = "${contact.name}的头像" })
}

private const val CONTACT_AVATAR_SIZE = 40
private const val CONTACT_SEARCH_ROWS = 5

/** 联系人选择弹窗骨架行数：与弹窗首屏联系人行数接近 */
private const val TODO_PICKER_SKELETON_ROW_COUNT = 5
private const val TAG = "TodoContactPickerTester"
