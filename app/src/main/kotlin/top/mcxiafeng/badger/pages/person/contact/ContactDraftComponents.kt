package top.mcxiafeng.badger.pages.person.contact

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import top.mcxiafeng.badger.ui.designsystem.continuousClip as clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ui.glass.IconButton
import top.mcxiafeng.badger.ui.glass.TextButton
import top.mcxiafeng.badger.ui.components.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.mcxiafeng.badger.ui.components.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.mcxiafeng.badger.ui.glass.buttonGlass
import top.mcxiafeng.badger.ui.glass.glassButtonForeground

@Composable
internal fun ContactDraftBody(
    state: ContactDraftUiState, viewModel: CreateContactViewModel, padding: PaddingValues,
    onAvatar: () -> Unit, onEdit: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
        .padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ContactDraftAvatar(state.avatar, state.editable, onAvatar, viewModel::removeAvatar)
        ContactDraftIdentity(state, viewModel)
        ContactDraftFields(state, viewModel, onEdit)
        ContactDraftPlatforms(state, viewModel, onEdit)
        ContactDraftSection("标签", state.draft.tags.joinToString("、"), state.editable) {
            ContactDraftAction(if (state.draft.tags.isEmpty()) "添加标签" else "编辑标签", state.editable) { onEdit("tags") }
        }
        ContactDraftSection("名片夹", state.collectionSummary, state.editable) {
            ContactDraftAction("选择名片夹", state.editable) { onEdit("collections") }
        }
        ContactDraftSection("更多信息", "性别、生日、国家和地区", state.editable) {
            BasicInfoCard(state.draft.basicFields) { key, _ -> if (state.editable) onEdit(key) }
            state.draft.fields.filter { it.key in DRAFT_BASIC_KEYS }.forEach { field ->
                TextButton("清除${field.name}", enabled = state.editable, onClick = { viewModel.removeField(field.id) },
                    modifier = Modifier.fillMaxWidth())
            }
        }
        if (state.isLoading) Text("正在加载可选资料…", Modifier.padding(horizontal = 28.dp))
        state.error?.let { Text(it, color = MiuixTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)) }
        if (!state.isLoading && state.draft.collectionIds.any { id -> state.collections.none { it.id == id } }) {
            Text("所选名片夹已失效，请重新选择", color = MiuixTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 28.dp))
        }
    }
}

@Composable
private fun ContactDraftIdentity(state: ContactDraftUiState, viewModel: CreateContactViewModel) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(state.draft.name, viewModel::updateName, label = "姓名 / 昵称（必填）",
                enabled = state.editable, modifier = Modifier.fillMaxWidth())
            Text("${state.draft.name.codePointCount(0, state.draft.name.length)}/$CONTACT_NAME_LIMIT",
                style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            TextField(state.draft.note, viewModel::updateNote, label = "备注（选填）", singleLine = false,
                enabled = state.editable, modifier = Modifier.fillMaxWidth())
            Text("${state.draft.note.codePointCount(0, state.draft.note.length)}/$CONTACT_NOTE_LIMIT",
                style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun ContactDraftAvatar(bitmap: Bitmap?, enabled: Boolean, onClick: () -> Unit, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(112.dp).clip(CircleShape).background(MiuixTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = enabled, onClickLabel = "选择头像", onClick = onClick),
            contentAlignment = Alignment.Center) {
            if (bitmap != null) Image(bitmap.asImageBitmap(), "头像草稿", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Default.CameraAlt, "选择头像", Modifier.size(32.dp), tint = MiuixTheme.colorScheme.primary)
        }
        if (bitmap != null) TextButton("移除头像", onClick = onRemove, enabled = enabled)
    }
}

@Composable
private fun ContactDraftFields(state: ContactDraftUiState, viewModel: CreateContactViewModel, onEdit: (String) -> Unit) {
    ContactDraftSection("联系方式", "${state.draft.contactFields.size} 项", state.editable) {
        state.draft.contactFields.forEach { field ->
            ContactDraftEditableRow(field.name, field.value, state.editable,
                onEdit = { onEdit("field:${field.id}") }, onRemove = { viewModel.removeField(field.id) })
        }
        ContactDraftAction("添加联系方式", state.editable) { onEdit("field") }
    }
}

@Composable
private fun ContactDraftPlatforms(state: ContactDraftUiState, viewModel: CreateContactViewModel, onEdit: (String) -> Unit) {
    ContactDraftSection("平台账号", "${state.draft.platforms.size} 个", state.editable) {
        state.draft.platforms.forEach { (key, entry) ->
            ContactDraftEditableRow(FIELD_DEF_MAP[key]?.displayName ?: key,
                entry.displayName?.takeIf(String::isNotBlank) ?: entry.value ?: entry.jumpLink, state.editable,
                onEdit = { onEdit("platform:$key") }, onRemove = { viewModel.removePlatform(key) })
        }
        ContactDraftAction("添加平台账号", state.editable) { onEdit("platform") }
    }
}

@Composable
private fun ContactDraftEditableRow(title: String, summary: String, enabled: Boolean, onEdit: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable(enabled = enabled, onClick = onEdit).padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MiuixTheme.textStyles.body1)
            Text(summary, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        IconButton(onClick = onRemove, enabled = enabled) { Icon(Icons.Default.Close, "移除$title") }
    }
}

@Composable
private fun ContactDraftSection(title: String, summary: String, enabled: Boolean, content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).animateContentSize()) {
        Row(Modifier.fillMaxWidth().clickable(enabled = enabled) {
            expanded = !expanded
            Log.d("ContactDraftComponentsTester", "草稿分区展开状态改变: expanded=$expanded")
        }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MiuixTheme.textStyles.title4)
                if (summary.isNotBlank()) Text(summary, maxLines = 2, style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                if (expanded) "收起$title" else "展开$title")
        }
        AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) { content() }
        }
    }
}

@Composable
private fun ContactDraftAction(title: String, enabled: Boolean, onClick: () -> Unit) {
    val tint = glassButtonForeground(MiuixTheme.colorScheme.primary)
    Row(Modifier.fillMaxWidth().buttonGlass(12.dp).clickable(enabled = enabled, onClick = onClick)
        .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.Add, null, tint = tint)
        Text(title, color = tint)
    }
}
