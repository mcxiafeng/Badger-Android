package top.mcxiafeng.badger.pages.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.data.model.MergeChoice
import top.mcxiafeng.badger.network.ContactType
import top.mcxiafeng.badger.network.PlatformAdapterRegistry
import top.mcxiafeng.badger.ocr.ExtractedContactInfo
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.mcxiafeng.badger.ui.components.PlatformIcon
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.mcxiafeng.badger.utils.miuixShape
import top.yukonga.miuix.kmp.window.WindowDialog
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.platform.BackHandler

@Composable
internal fun PhotoModeDialog(
    show: Boolean,
    qrCodeContents: List<String>,
    ocrExtractedInfo: ExtractedContactInfo?,
    resolveStates: MutableMap<String, QrResolveState>,
    ocrResolveStates: MutableMap<String, QrResolveState> = mutableStateMapOf(),
    isProcessingPhoto: Boolean = false,
    photoNoResult: Boolean = false,
    duplicateFieldKeys: Set<String> = emptySet(),
    conflictFieldMap: Map<String, ConflictFieldInfo> = emptyMap(),
    existingContact: Contact? = null,
    hasMergeableFields: Boolean = true,
    isImportToProfile: Boolean = false,
    repository: ContactRepository,
    tagRepository: TagRepository? = null,
    markerConfig: ScanMarkerConfig = ScanMarkerConfig(),
    onMarkerConfigChange: (ScanMarkerConfig) -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: (List<Pair<String, ExtractedContactInfo>>, Contact?, Map<String, MergeChoice>, ScanMarkerConfig) -> Unit,
    onAttachToExisting: (Contact, ExtractedContactInfo, ScanMarkerConfig) -> Unit
) {
    
    val infoPriority = remember {
        listOf(
            ContactType.QQ, ContactType.Bilibili, ContactType.WeChat, ContactType.Douyin,
            ContactType.Weibo, ContactType.GitHub, ContactType.Telegram, ContactType.Xiaohongshu,
            ContactType.X, ContactType.Facebook, ContactType.TelegramGroup, ContactType.QQGroup, ContactType.Website
        )
    }

    
    
    val currentOcrInfo by rememberUpdatedState(ocrExtractedInfo)

    
    val mergedName by remember {
        derivedStateOf {
            computeMergedName(resolveStates, ocrResolveStates, currentOcrInfo, infoPriority)
        }
    }

    
    val mergedAvatarUrl by remember {
        derivedStateOf {
            val allResults = resolveStates.values.mapNotNull { it.networkResult } +
                    ocrResolveStates.values.mapNotNull { it.networkResult }
            infoPriority.firstNotNullOfOrNull { type ->
                allResults.firstOrNull { it.type == type && !it.avatarUrl.isNullOrBlank() }?.avatarUrl
            } ?: allResults.firstOrNull { !it.avatarUrl.isNullOrBlank() }?.avatarUrl
            ?: currentOcrInfo?.avatarUrl
        }
    }

    
    val isAnyLoading by remember {
        derivedStateOf { resolveStates.values.any { it.isLoading } }
    }

    
    val fieldOrder = remember {
        mapOf(
            "qq" to 0, "bilibili" to 1, "wechat" to 2, "phone" to 3, "email" to 4,
            "douyin" to 5, "weibo" to 6, "github" to 7, "telegram" to 8,
            "xiaohongshu" to 9, "x" to 10, "facebook" to 11, "telegramGroup" to 12, "qqGroup" to 13, "website" to 14
        )
    }

    
    
    val mergedFields by remember {
        derivedStateOf {
            computeMergedFields(resolveStates, ocrResolveStates, currentOcrInfo, fieldOrder)
        }
    }
    
    val checkedFields = remember { mutableStateSetOf<String>() }
    
    val conflictResolutions = remember { mutableStateMapOf<String, MergeChoice>() }
    
    var showConflictDialogFor by remember { mutableStateOf<String?>(null) }
    var showContactPicker by remember { mutableStateOf(false) }
    
    LaunchedEffect(mergedFields, duplicateFieldKeys, conflictFieldMap) {
        checkedFields.clear()
        conflictResolutions.clear()
        mergedFields.forEach { field ->
            if (field.key !in duplicateFieldKeys && field.key !in conflictFieldMap) {
                checkedFields.add(field.key)
            }
        }
    }

    
    BackHandler(enabled = isProcessingPhoto) {  }

    
    if (show) WindowDialog(show = true, title = "扫描结果", onDismissRequest = { if (!isProcessingPhoto) onDismiss() }) {
        
        if (isProcessingPhoto) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(size = 28.dp, strokeWidth = 3.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "正在识别图片...",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onBackgroundVariant
                )
            }
            return@WindowDialog
        }
        
        if (photoNoResult) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "未识别到有效信息",
                    style = MiuixTheme.textStyles.subtitle,
                    color = MiuixTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "请确保图片中包含二维码或联系人信息",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onBackgroundVariant
                )
                Spacer(modifier = Modifier.height(20.dp))
                TextButton(
                    text = "知道了",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
            return@WindowDialog
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            
            if (tagRepository != null) {
                ScanMarkerConfigRow(
                    markerConfig = markerConfig,
                    onMarkerConfigChange = onMarkerConfigChange,
                    tagRepository = tagRepository,
                    enabled = !isImportToProfile,
                )
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isAnyLoading) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(size = 22.dp, strokeWidth = 2.dp)
                    }
                } else {
                    ContactAvatar(
                        name = mergedName,
                        avatarUrl = mergedAvatarUrl,
                        size = 48
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = mergedName,
                            style = MiuixTheme.textStyles.subtitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (existingContact != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            DuplicateTag()
                        }
                        if (conflictFieldMap.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            ConflictTag()
                        }
                    }
                    if (isAnyLoading) {
                        Text(
                            text = "获取信息中...",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onBackgroundVariant
                        )
                    }
                }
            }

            if (mergedFields.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))

                
                val allFieldKeys = mergedFields.map { it.key }
                val duplicateFieldKeysSet = duplicateFieldKeys
                val nonDuplicateFieldKeys = allFieldKeys.filterNot { it in duplicateFieldKeysSet }
                val selectableFieldKeys = nonDuplicateFieldKeys.filterNot { it in conflictFieldMap }
                val allFieldsChecked = selectableFieldKeys.isNotEmpty() && selectableFieldKeys.all { it in checkedFields }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(miuixShape(8.dp))
                        .clickable {
                            if (allFieldsChecked) {
                                checkedFields.clear()
                                conflictResolutions.clear()
                            } else {
                                
                                checkedFields.addAll(selectableFieldKeys)
                            }
                        }
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                ) {
                    Text(
                        text = if (allFieldsChecked) "取消全选" else "全选",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.primary
                    )

                    
                    val loadingCount = resolveStates.values.count { it.isLoading }
                    if (loadingCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "($loadingCount 个码加载中)",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onBackgroundVariant
                        )
                    }

                    
                    if (duplicateFieldKeysSet.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${duplicateFieldKeysSet.size}个重复",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onBackgroundVariant
                        )
                    }
                    
                    if (conflictFieldMap.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${conflictFieldMap.size}个冲突",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    itemsIndexed(mergedFields, key = { _, field -> field.key }) { _, field ->
                        val isDuplicateField = field.key in duplicateFieldKeys
                        val isConflictField = field.key in conflictFieldMap
                        val isChecked = field.key in checkedFields
                        
                        val fallbackTagColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        val tagColor = remember(field.key) {
                            val type = if (field.key == "qqGroup") ContactType.QQGroup
                                else if (field.key == "telegramGroup") ContactType.TelegramGroup
                                else if (field.key.startsWith("website")) ContactType.Website
                                else FIELD_DEF_MAP[field.key]?.contactType
                            type?.let {
                                PlatformAdapterRegistry.getTagInfo(it)?.color?.let { Color(it) }
                            } ?: fallbackTagColor
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(miuixShape(8.dp))
                                .background(if (isChecked) MiuixTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
                                .clickable(enabled = !isDuplicateField) {
                                    if (isDuplicateField) return@clickable
                                    if (isConflictField) {
                                        if (isChecked) {
                                            checkedFields.remove(field.key)
                                            conflictResolutions.remove(field.key)
                                        } else {
                                            showConflictDialogFor = field.key
                                        }
                                    } else {
                                        if (isChecked) checkedFields.remove(field.key) else checkedFields.add(field.key)
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            
                            PlatformIcon(field.key, tagColor)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = field.value,
                                style = MiuixTheme.textStyles.body1,
                                color = if (isChecked) MiuixTheme.colorScheme.onBackground else MiuixTheme.colorScheme.onBackgroundVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (isDuplicateField) {
                                Spacer(modifier = Modifier.width(6.dp))
                                DuplicateTag()
                            }
                            if (isConflictField) {
                                Spacer(modifier = Modifier.width(6.dp))
                                ConflictTag()
                                val resolution = conflictResolutions[field.key]
                                if (resolution != null) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = when (resolution) {
                                            MergeChoice.KEEP -> "保留旧"
                                            MergeChoice.REPLACE -> "保留新"
                                            MergeChoice.APPEND -> "全部保留"
                                        },
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            
            val hasChecked = checkedFields.isNotEmpty()
            val hasExisting = existingContact != null
            if (isImportToProfile) {
                
                TextButton(
                    text = "导入",
                    onClick = {
                        val selectedPlatforms = mutableMapOf<String, String>()
                        for (f in mergedFields) {
                            if (f.key !in checkedFields) continue
                            when {
                                f.key == "phone" || f.key.startsWith("phone_") ->
                                    selectedPlatforms[f.key] = f.value
                                f.key == "email" || f.key.startsWith("email_") ->
                                    selectedPlatforms[f.key] = f.value
                                else -> selectedPlatforms[f.key] = f.value
                            }
                        }
                        val info = ExtractedContactInfo(
                            name = mergedName,
                            platforms = selectedPlatforms,
                            avatarUrl = mergedAvatarUrl,
                            rawText = qrCodeContents.firstOrNull() ?: ""
                        )
                        BadgerLog.d("PhotoModeDialog", "导入到我的名片: checkedFields=$checkedFields")
                        onConfirm(listOf(("__merged__") to info), null, emptyMap(), markerConfig)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    enabled = hasChecked
                )
            } else if (hasExisting && !hasChecked) {
                
                Text(
                    text = "所有字段已存在，无可合并内容",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
                TextButton(
                    text = "完成",
                    onClick = {
                        
                        
                        
                        val info = ExtractedContactInfo(
                            name = mergedName,
                            avatarUrl = mergedAvatarUrl,
                            rawText = qrCodeContents.firstOrNull() ?: ""
                        )
                        BadgerLog.d(
                            "PhotoModeDialog",
                            "重复联系人无可合并字段，点完成：contact=${existingContact.name}, marker=${markerConfig.tagName}"
                        )
                        onAttachToExisting(existingContact, info, markerConfig)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            } else if (hasExisting) {
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = "合并信息",
                        onClick = {
                            val selectedPlatforms = mutableMapOf<String, String>()
                            for (f in mergedFields) {
                                if (f.key !in checkedFields) continue
                                selectedPlatforms[f.key] = f.value
                            }
                            val info = ExtractedContactInfo(
                                name = mergedName,
                                platforms = selectedPlatforms,
                                avatarUrl = mergedAvatarUrl,
                                rawText = qrCodeContents.firstOrNull() ?: ""
                            )
                            BadgerLog.d("PhotoModeDialog", "合并信息: checkedFields=$checkedFields, conflictResolutions=$conflictResolutions")
                            onConfirm(listOf(("__merged__") to info), existingContact, conflictResolutions.toMap(), markerConfig)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        enabled = hasChecked && hasMergeableFields
                    )
                }
            } else {
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = "附加到已有",
                        onClick = { showContactPicker = true },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "添加新记录",
                        onClick = {
                            val selectedPlatforms = mutableMapOf<String, String>()
                            for (f in mergedFields) {
                                if (f.key !in checkedFields) continue
                                selectedPlatforms[f.key] = f.value
                            }
                            val info = ExtractedContactInfo(
                                name = mergedName,
                                platforms = selectedPlatforms,
                                avatarUrl = mergedAvatarUrl,
                                rawText = qrCodeContents.firstOrNull() ?: ""
                            )
                            onConfirm(listOf(("__merged__") to info), null, emptyMap(), markerConfig)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        enabled = hasChecked
                    )
                }
            }
        }
    }

    
    showConflictDialogFor?.let { fieldKey ->
        val conflictInfo = conflictFieldMap[fieldKey] ?: return@let
        val fieldName = mergedFields.find { it.key == fieldKey }?.label ?: fieldKey
        WindowDialog(
            show = true,
            title = "冲突解决",
            onDismissRequest = { showConflictDialogFor = null }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "$fieldName 字段存在冲突",
                    style = MiuixTheme.textStyles.subtitle
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "已有：${conflictInfo.existingValue}",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "新扫描：${conflictInfo.newValue}",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        text = "保留新信息",
                        onClick = {
                            BadgerLog.d("PhotoModeDialog", "冲突解决: field=$fieldKey, choice=REPLACE")
                            conflictResolutions[fieldKey] = MergeChoice.REPLACE
                            checkedFields.add(fieldKey)
                            showConflictDialogFor = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                    TextButton(
                        text = "保留旧信息",
                        onClick = {
                            BadgerLog.d("PhotoModeDialog", "冲突解决: field=$fieldKey, choice=KEEP")
                            conflictResolutions[fieldKey] = MergeChoice.KEEP
                            checkedFields.add(fieldKey)
                            showConflictDialogFor = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    text = "全部保留",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        BadgerLog.d("PhotoModeDialog", "冲突解决: field=$fieldKey, choice=APPEND")
                        conflictResolutions[fieldKey] = MergeChoice.APPEND
                        checkedFields.add(fieldKey)
                        showConflictDialogFor = null
                    }.padding(vertical = 4.dp)
                )
            }
        }
    }

    
    if (showContactPicker) {
        ContactPickerDialog(
            repository = repository,
            onDismiss = { showContactPicker = false },
            onContactSelected = { contact ->
                showContactPicker = false
                val selectedPlatforms = mutableMapOf<String, String>()
                for (f in mergedFields) {
                    selectedPlatforms[f.key] = f.value
                }
                val info = ExtractedContactInfo(
                    name = mergedName,
                    platforms = selectedPlatforms,
                    avatarUrl = mergedAvatarUrl,
                    rawText = qrCodeContents.firstOrNull() ?: ""
                )
                BadgerLog.d("PhotoModeDialog", "附加到已有: contact=${contact.name}, info=$info")
                onAttachToExisting(contact, info, markerConfig)
            }
        )
    }
}