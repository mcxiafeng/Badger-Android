package top.mcxiafeng.badger.pages.person.contact

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactFieldDisplay
import top.mcxiafeng.badger.data.ContactRepository
import top.mcxiafeng.badger.ui.components.AvatarPlaceholder
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.io.File

@Composable
internal fun ContactDetailAttachFieldDialog(
    sourceContact: Contact,
    sourceFields: List<ContactFieldDisplay>,
    existingContact: Contact,
    repository: ContactRepository,
    onDismiss: () -> Unit,
    onConfirm: (selectedFieldKeys: List<String>, selectedCustomFieldIds: List<Long>) -> Unit
) {
    // 系统字段：默认全选
    val systemFields = remember(sourceFields) {
        sourceFields.filter { it.fieldKey != null }
    }
    val customFields = remember(sourceFields) {
        sourceFields.filter { it.fieldKey == null }
    }

    val fieldChecked = remember { mutableStateMapOf<String, Boolean>().apply {
        systemFields.forEach { it.fieldKey?.let { k -> put(k, true) } }
    }}
    val customChecked = remember { mutableStateMapOf<Long, Boolean>().apply {
        customFields.forEach { it.customFieldId?.let { id -> put(id, true) } }
    }}

    // 头像：仅当源联系人有头像而目标联系人为空时可选
    val hasAvatar = !sourceContact.avatarPath.isNullOrBlank() && existingContact.avatarPath.isNullOrBlank() && existingContact.avatarUrl.isNullOrBlank()
    var avatarChecked by remember { mutableStateOf(hasAvatar) }

    WindowDialog(
        show = true,
        title = "附加到 ${existingContact.name}",
        onDismissRequest = onDismiss
    ) {
        // 目标联系人信息
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            AvatarPlaceholder(
                name = existingContact.name,
                size = 40
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = existingContact.name, style = MiuixTheme.textStyles.subtitle)
                Text(
                    text = "选择要从 ${sourceContact.name} 附加的信息",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onBackgroundVariant
                )
            }
        }

        // 头像选项
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

        // 系统字段
        systemFields.forEach { field ->
            val key = field.fieldKey ?: return@forEach
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    state = if (fieldChecked[key] == true) ToggleableState.On else ToggleableState.Off,
                    onClick = { fieldChecked[key] = !(fieldChecked[key] ?: true) }
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(text = field.fieldName, style = MiuixTheme.textStyles.body1)
                    Text(
                        text = field.value,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onBackgroundVariant
                    )
                }
            }
        }

        // 自定义字段
        customFields.forEach { field ->
            val id = field.customFieldId ?: return@forEach
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    state = if (customChecked[id] == true) ToggleableState.On else ToggleableState.Off,
                    onClick = { customChecked[id] = !(customChecked[id] ?: true) }
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(text = field.fieldName, style = MiuixTheme.textStyles.body1)
                    Text(
                        text = field.value,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onBackgroundVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 按钮
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
                    val selectedKeys = systemFields
                        .filter { it.fieldKey != null && fieldChecked[it.fieldKey] == true }
                        .map { it.fieldKey!! }
                    val selectedCustomIds = customFields
                        .filter { it.customFieldId != null && customChecked[it.customFieldId] == true }
                        .map { it.customFieldId!! }
                    onConfirm(selectedKeys, selectedCustomIds)
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}

/**
 * 将当前联系人的选中字段附加到目标联系人
 *
 * 策略：只补充目标联系人缺失的字段值（不覆盖已有值）。
 * 头像处理：如果目标联系人没有头像，则复制源联系人的本地头像文件。
 *
 * @param repository 数据仓库
 * @param sourceContact 源联系人（当前联系人）
 * @param sourceFields 源联系人的所有字段值
 * @param existingContact 目标联系人
 * @param selectedFieldKeys 用户勾选的系统字段 key 列表
 * @param selectedCustomFieldIds 用户勾选的自定义字段 ID 列表
 */
internal suspend fun attachCurrentContactToExisting(
    repository: ContactRepository,
    sourceContact: Contact,
    sourceFields: List<ContactFieldDisplay>,
    existingContact: Contact,
    selectedFieldKeys: List<String>,
    selectedCustomFieldIds: List<Long>
) {
    // 1. 附加系统字段：同值跳过，不同值新增（允许同字段多值）
    if (selectedFieldKeys.isNotEmpty()) {
        val fieldValues = mutableListOf<Pair<Long, String>>()
        val allExistingValues = repository.getFieldValuesByContactOnce(existingContact.id)
        val enabledFields = repository.getAllEnabledFields().first()
        for (field in enabledFields) {
            if (field.fieldKey !in selectedFieldKeys) continue
            val sourceValues = sourceFields.filter { it.fieldKey == field.fieldKey }
            for (sourceValue in sourceValues) {
                if (sourceValue.value.isNotBlank()) {
                    // 跳过目标联系人已有完全相同值的记录
                    val sameValueExists = allExistingValues.any { it.fieldId == field.id && it.value == sourceValue.value }
                    if (!sameValueExists) {
                        fieldValues.add(field.id to sourceValue.value)
                    }
                }
            }
        }
        if (fieldValues.isNotEmpty()) {
            repository.saveContactFieldValues(existingContact.id, fieldValues)
        }
    }

    // 2. 附加自定义字段：同值跳过，不同值新增
    if (selectedCustomFieldIds.isNotEmpty()) {
        val allExistingValues = repository.getFieldValuesByContactOnce(existingContact.id)
        val customFieldMap = mutableMapOf<Long, String>()
        for (field in sourceFields) {
            val id = field.customFieldId ?: continue
            if (id !in selectedCustomFieldIds) continue
            val sameValueExists = allExistingValues.any { it.customFieldId == id && it.value == field.value }
            if (!sameValueExists) {
                customFieldMap[id] = field.value
            }
        }
        if (customFieldMap.isNotEmpty()) {
            repository.saveContactCustomFieldValues(existingContact.id, customFieldMap)
        }
    }

    // 3. 附加头像（仅当目标联系人为空且有本地头像）
    val sourceAvatarPath = sourceContact.avatarPath
    // 从 DB 重新读取最新联系人，避免用过时的参数覆盖并发修改
    val freshExisting = repository.getContactById(existingContact.id) ?: existingContact
    var avatarAttached = false
    if (!sourceAvatarPath.isNullOrBlank()
        && freshExisting.avatarPath.isNullOrBlank()
        && freshExisting.avatarUrl.isNullOrBlank()
    ) {
        val sourceFile = File(sourceAvatarPath)
        if (sourceFile.exists()) {
            val destFile = File(sourceFile.parentFile, "contact_${freshExisting.id}_avatar.webp")
            sourceFile.copyTo(destFile, overwrite = true)
            repository.updateContact(
                freshExisting.copy(avatarPath = destFile.absolutePath, updateTime = System.currentTimeMillis())
            )
            avatarAttached = true
        }
    }

    // 4. 仅当 step3 未执行时更新 updateTime（step3 已包含 updateTime）
    if (!avatarAttached) {
        repository.updateContact(freshExisting.copy(updateTime = System.currentTimeMillis()))
    }
}