package top.mcxiafeng.badger.pages.person.contact

import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.glass.TextButton
import top.mcxiafeng.badger.ui.glass.WindowDialog
import top.yukonga.miuix.kmp.basic.Text
import top.mcxiafeng.badger.ui.components.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ContactDraftEditors(editor: String?, state: ContactDraftUiState, viewModel: CreateContactViewModel, onDismiss: () -> Unit) {
    when {
        editor == null -> Unit
        editor.startsWith("platform") -> ContactDraftPlatformEditor(editor.substringAfter(':', ""), state, viewModel, onDismiss)
        editor == "field" -> ContactDraftAddFieldEditor(state, viewModel, onDismiss)
        editor.startsWith("field:") -> state.draft.fields.firstOrNull { it.id == editor.substringAfter(':') }?.let {
            ContactDraftEditFieldEditor(it, viewModel, onDismiss)
        }
        editor == "tags" -> ContactDraftTagEditor(state, viewModel::setTags, onDismiss)
        editor == "collections" -> ContactDraftCollectionEditor(state, viewModel::setCollections, onDismiss)
        editor == "gender" -> GenderPickerDialog(true, state.draft.info(editor), onDismiss) {
            viewModel.updateInfo("gender", it); onDismiss()
        }
        editor == "birthday" -> BirthdayPickerDialog(true, state.draft.info(editor), onDismiss) {
            viewModel.updateInfo("birthday", it); onDismiss()
        }
        else -> ContactDraftPlaceEditor(editor, state.draft.info(editor), viewModel::updateInfo, onDismiss)
    }
}

@Composable
private fun ContactDraftEditFieldEditor(field: ContactDraftField, viewModel: CreateContactViewModel, onDismiss: () -> Unit) {
    var value by rememberSaveable(field.id) { mutableStateOf(field.value) }
    ContactDetailEditFieldDialog(true, field.display, value, { value = it }, onDismiss) {
        viewModel.stageField(field.key, field.customFieldId, field.name, it, field.id)
    }
}

@Composable
private fun ContactDraftAddFieldEditor(state: ContactDraftUiState, viewModel: CreateContactViewModel, onDismiss: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var value by rememberSaveable { mutableStateOf("") }
    val custom = state.customFields.firstOrNull { "custom:${it.id}" == selected }
    val title = when (selected) { "phone" -> "电话"; "email" -> "邮箱"; "new" -> "自定义字段"; else -> custom?.fieldName.orEmpty() }
    WindowDialog(show = true, title = if (selected.isEmpty()) "添加联系方式" else "添加$title", onDismissRequest = onDismiss) {
        if (selected.isEmpty()) ContactDraftFieldChoices(state, onSelect = { selected = it }, onDismiss)
        else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (selected == "new") TextField(name, { name = it }, label = "字段名称", modifier = Modifier.fillMaxWidth())
            TextField(value, { value = it }, label = if (selected == "phone") "电话号码" else "填写内容",
                modifier = Modifier.fillMaxWidth())
            ContactDraftDialogButtons(onDismiss, value.isNotBlank() && (selected != "new" || name.isNotBlank())) {
                val key = selected.takeIf { it == "phone" || it == "email" }
                if (viewModel.stageField(key, custom?.id, if (selected == "new") name else title, value)) onDismiss()
            }
        }
    }
}

@Composable
private fun ContactDraftFieldChoices(state: ContactDraftUiState, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()).padding(top = BadgerSpacing.scrollContentTopGap)) {
        ContactDraftChoice("电话", onClick = { onSelect("phone") })
        ContactDraftChoice("邮箱", onClick = { onSelect("email") })
        state.customFields.forEach { field -> ContactDraftChoice(field.fieldName, onClick = { onSelect("custom:${field.id}") }) }
        ContactDraftChoice("自定义字段", onClick = { onSelect("new") })
    }
    TextButton("取消", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContactDraftTagEditor(state: ContactDraftUiState, onConfirm: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(state.draft.tags.toList()) }
    var newName by rememberSaveable { mutableStateOf("") }
    val names = remember(state.availableTags, selected) { (state.availableTags.map { it.name } + selected).distinct() }
    WindowDialog(show = true, title = "标签", onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(newName, { newName = it }, label = "新标签名称", modifier = Modifier.fillMaxWidth())
            TextButton("添加到已选", enabled = newName.isNotBlank(), onClick = {
                selected = (selected + newName.trim()).distinct(); newName = ""
            }, modifier = Modifier.fillMaxWidth())
            if (names.isEmpty()) Text("还没有标签", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            else FlowRow(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()).padding(top = BadgerSpacing.scrollContentTopGap),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                names.forEach { name -> TextButton(if (name in selected) "✓ $name" else name,
                    onClick = { selected = if (name in selected) selected - name else selected + name }) }
            }
            ContactDraftDialogButtons(onDismiss) {
                onConfirm((selected + listOfNotNull(newName.trim().takeIf(String::isNotBlank))).toSet()); onDismiss()
            }
        }
    }
}

@Composable
private fun ContactDraftCollectionEditor(state: ContactDraftUiState, onConfirm: (Set<Long>) -> Unit, onDismiss: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(state.draft.collectionIds.toList()) }
    WindowDialog(show = true, title = "选择名片夹", summary = "可多选；不选则暂不加入名片夹", onDismissRequest = onDismiss) {
        if (state.collections.isEmpty()) Text("还没有名片夹", Modifier.fillMaxWidth().padding(vertical = 16.dp))
        else Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()).padding(top = BadgerSpacing.scrollContentTopGap)) {
            state.collections.forEach { collection ->
                ContactDraftChoice(collection.name, collection.id in selected) {
                    selected = if (collection.id in selected) selected - collection.id else selected + collection.id
                }
            }
        }
        ContactDraftDialogButtons(onDismiss, !state.isLoading) {
            onConfirm(selected.filter { id -> state.collections.any { it.id == id } }.toSet()); onDismiss()
        }
    }
}

@Composable
private fun ContactDraftPlaceEditor(key: String, current: String, onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var value by rememberSaveable(key) { mutableStateOf(current) }
    WindowDialog(show = true, title = if (key == "country") "国家" else "地区", onDismissRequest = onDismiss) {
        TextField(value, { value = it }, label = if (key == "country") "填写国家" else "填写省、市或地区",
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
        ContactDraftDialogButtons(onDismiss) { onConfirm(key, value.trim()); onDismiss() }
    }
}

@Composable
private fun ContactDraftChoice(title: String, selected: Boolean = false, onClick: () -> Unit) {
    Text(if (selected) "✓ $title" else title,
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface)
}

@Composable
internal fun ContactDraftDialogButtons(onDismiss: () -> Unit, enabled: Boolean = true, onConfirm: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton("取消", onClick = onDismiss, modifier = Modifier.weight(1f))
        TextButton("确定", onClick = onConfirm, enabled = enabled, modifier = Modifier.weight(1f))
    }
}
