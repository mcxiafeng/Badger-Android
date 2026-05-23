package top.mcxiafeng.badger.pages.scanner

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactRepository
import top.mcxiafeng.badger.network.NetworkResolveResult
import top.mcxiafeng.badger.ocr.AiOcrConfig
import top.mcxiafeng.badger.ocr.ALIAS_TO_KEY_MAP
import top.mcxiafeng.badger.ocr.ExtractedContactInfo
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.miuixShape
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 联系人选择器对话框
 */
@Composable
internal fun ContactPickerDialog(
    repository: ContactRepository,
    onDismiss: () -> Unit,
    onContactSelected: (Contact) -> Unit
) {
    Log.d("Tester", "ContactPickerDialog: 显示对话框")
    var searchQuery by remember { mutableStateOf("") }
    val contacts by repository.searchContacts(searchQuery)
        .collectAsState(initial = emptyList())
    Log.d("Tester", "ContactPickerDialog: 搜索查询='$searchQuery', 联系人数量=${contacts.size}")

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
                text = if (searchQuery.isEmpty()) "暂无联系人" else "未找到匹配的联系人",
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(contacts, key = { it.id }) { contact ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(miuixShape(8.dp))
                            .clickable { onContactSelected(contact) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ContactAvatar(name = contact.name, avatarUrl = contact.avatarUrl, size = 36)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = contact.name,
                            style = MiuixTheme.textStyles.body1
                        )
                    }
                }
            }
        }
    }
}

/**
 * 字段附加确认对话框
 */
@Composable
internal fun AttachFieldDialog(
    existingContact: Contact,
    extractedInfo: ExtractedContactInfo,
    networkResult: NetworkResolveResult?,
    onDismiss: () -> Unit,
    onConfirm: (selectedFields: List<String>, customFields: Map<Int, String>) -> Unit
) {
    Log.d("Tester", "AttachFieldDialog: existingContact=${existingContact.name}, extractedInfo=$extractedInfo")
    data class SelectableField(val key: String, val label: String, val value: String)

    val availableFields = remember(extractedInfo) {
        buildList {
            extractedInfo.phone?.let { add(SelectableField("phone", "手机", it)) }
            extractedInfo.email?.let { add(SelectableField("email", "邮箱", it)) }
            for ((key, value) in extractedInfo.platforms) {
                val def = FIELD_DEF_MAP[key]
                add(SelectableField(key, def?.displayName ?: key, value))
            }
        }
    }
    Log.d("Tester", "AttachFieldDialog: availableFields=${availableFields.map { it.key }}")

    val availableCustomInfo = remember(extractedInfo) {
        // 只保留可映射到标准平台字段的 otherInfo，忽略求扩列/广告等无意义内容
        extractedInfo.otherInfo.mapIndexedNotNull { index, text ->
            if (text.isNotBlank()) {
                val colonIndex = text.indexOfAny(charArrayOf(':', '\uff1a'))
                if (colonIndex > 0) {
                    val key = text.substring(0, colonIndex).lowercase().trim()
                    val isPlatform = key in ALIAS_TO_KEY_MAP
                    if (isPlatform) index to text else null
                } else null
            } else null
        }
    }

    val fieldChecked = remember { mutableStateMapOf<String, Boolean>().apply {
        availableFields.forEach { put(it.key, true) }
    }}
    val customChecked = remember { mutableStateMapOf<Int, Boolean>().apply {
        availableCustomInfo.forEach { (index, _) -> put(index, true) }
    }}

    val hasAvatar = !networkResult?.avatarUrl.isNullOrBlank() && existingContact.avatarUrl.isNullOrBlank()
    var avatarChecked by remember { mutableStateOf(hasAvatar) }

    val hasAnythingToAttach = fieldChecked.values.any { it }
            || customChecked.values.any { it }
            || (hasAvatar && avatarChecked)

    WindowDialog(
        show = true,
        title = "附加到 ${existingContact.name}",
        onDismissRequest = onDismiss
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            ContactAvatar(name = existingContact.name, avatarUrl = existingContact.avatarUrl, size = 40)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = existingContact.name, style = MiuixTheme.textStyles.subtitle)
                Text(
                    text = "选择要附加的信息",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onBackgroundVariant
                )
            }
        }

        if (hasAvatar) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    state = if (avatarChecked) ToggleableState.On else ToggleableState.Off,
                    onClick = { avatarChecked = !avatarChecked }
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "头像", style = MiuixTheme.textStyles.body1)
            }
        }

        availableFields.forEach { field ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    state = if (fieldChecked[field.key] == true) ToggleableState.On else ToggleableState.Off,
                    onClick = { fieldChecked[field.key] = !(fieldChecked[field.key] ?: true) }
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(text = field.label, style = MiuixTheme.textStyles.body1)
                    Text(
                        text = field.value,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onBackgroundVariant
                    )
                }
            }
        }

        availableCustomInfo.forEach { (index, text) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    state = if (customChecked[index] == true) ToggleableState.On else ToggleableState.Off,
                    onClick = { customChecked[index] = !(customChecked[index] ?: true) }
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = text, style = MiuixTheme.textStyles.body2)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                text = "确认附加",
                onClick = {
                    val selected = availableFields.filter { fieldChecked[it.key] == true }.map { it.key }
                    val customSelected = availableCustomInfo.filter { customChecked[it.first] == true }
                        .associate { it.first to it.second }
                    Log.d("Tester", "AttachFieldDialog: 确认附加 selected=$selected, customSelected=$customSelected")
                    onConfirm(selected, customSelected)
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}

/**
 * AI 文字识别隐私提示对话框
 */
@Composable
internal fun AiOcrPrivacyDialog(
    onAgree: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.widthIn(max = 320.dp),
            cornerRadius = 16.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "AI 文字识别",
                    style = MiuixTheme.textStyles.subtitle
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "启用此功能后，拍摄的图片将上传至第三方 AI 服务进行文字识别。\n\n您的图片数据将由 AI 服务提供商处理，请确保您信任该服务。",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onBackgroundVariant
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = "取消",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "同意并启用",
                        onClick = {
                            AiOcrConfig.setPrivacyAgreed(context, true)
                            onAgree()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    }
}
