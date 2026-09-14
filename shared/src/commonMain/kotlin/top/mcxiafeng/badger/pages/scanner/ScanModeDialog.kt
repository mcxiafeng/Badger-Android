package top.mcxiafeng.badger.pages.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
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
import top.mcxiafeng.badger.ocr.ExtractedContactInfo
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.platform.BackHandler

@Composable
internal fun ScanModeDialog(
    show: Boolean,
    qrCodeContents: List<String>,
    resolveStates: MutableMap<String, QrResolveState>,
    isProcessingPhoto: Boolean = false,
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
    
    val conflictResolutions = remember { mutableStateMapOf<String, MergeChoice>() }
    
    var showConflictDialog by remember { mutableStateOf(false) }
    
    var showContactPicker by remember { mutableStateOf(false) }

    
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

        val content = qrCodeContents.firstOrNull()
        val state = content?.let { resolveStates[it] }

        Column(modifier = Modifier.fillMaxWidth()) {
            
            if (tagRepository != null) {
                ScanMarkerConfigRow(
                    markerConfig = markerConfig,
                    onMarkerConfigChange = onMarkerConfigChange,
                    tagRepository = tagRepository,
                    enabled = !isImportToProfile,
                )
            }
            if (content == null) {
                Text(
                    text = "未识别到有效信息",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    
                    if (state?.isLoading == true) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(size = 24.dp, strokeWidth = 2.dp)
                        }
                    } else {
                        ContactAvatar(
                            name = state?.displayName ?: content.take(1),
                            avatarUrl = state?.avatarUrl,
                            size = 64
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = state?.displayName
                                ?: content.take(30).let { if (content.length > 30) "$it..." else it },
                            style = MiuixTheme.textStyles.subtitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (existingContact != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            DuplicateTag()
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        state?.platformInfo?.let { (label, color) ->
                            PlatformTag(label, color)
                        }
                        if (duplicateFieldKeys.isNotEmpty()) {
                            DuplicateTag()
                        }
                        if (conflictFieldMap.isNotEmpty()) {
                            ConflictTag()
                        }
                    }

                    
                    if (state?.isLoading == true) {
                        Text(
                            text = "获取信息中...",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onBackgroundVariant
                        )
                    } else {
                        state?.platformIdText?.let { idText ->
                            Text(
                                text = idText,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            
            val hasExisting = existingContact != null
            if (isImportToProfile) {
                
                TextButton(
                    text = "导入",
                    onClick = {
                        onConfirm(buildScanResults(qrCodeContents, resolveStates), null, emptyMap(), markerConfig)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    enabled = content != null
                )
            } else if (hasExisting) {
                
                if (!hasMergeableFields) {
                    
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
                                name = resolveStates[content!!]?.extractedInfo?.name ?: "",
                                avatarUrl = resolveStates[content!!]?.avatarUrl,
                                rawText = content!!
                            )
                            BadgerLog.d(
                                "ScanModeDialog",
                                "重复联系人无可合并字段,点完成: contact=${existingContact.name}, marker=${markerConfig.tagName}"
                            )
                            onAttachToExisting(existingContact, info, markerConfig)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                } else {
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            text = "合并信息",
                            onClick = {
                                if (conflictFieldMap.isNotEmpty()) {
                                    showConflictDialog = true
                                } else {
                                    onConfirm(
                                        buildScanResults(qrCodeContents, resolveStates, duplicateFieldKeys),
                                        existingContact,
                                        conflictResolutions.toMap(),
                                        markerConfig
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            enabled = content != null && hasMergeableFields
                        )
                    }
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
                            onConfirm(buildScanResults(qrCodeContents, resolveStates), null, emptyMap(), markerConfig)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        enabled = content != null
                    )
                }
            }
        }
    }

    
    if (showConflictDialog && conflictFieldMap.isNotEmpty()) {
        WindowDialog(
            show = true,
            title = "冲突解决",
            onDismissRequest = { showConflictDialog = false }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                conflictFieldMap.forEach { (fieldKey, conflictInfo) ->
                    val resolution = conflictResolutions[fieldKey]
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Text(
                            text = fieldKey,
                            style = MiuixTheme.textStyles.subtitle
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "已有：${conflictInfo.existingValue}",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "新扫描：${conflictInfo.newValue}",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                text = "保留新信息",
                                onClick = {
                                    BadgerLog.d("ScanModeDialog", "冲突解决: field=$fieldKey, choice=REPLACE")
                                    conflictResolutions[fieldKey] = MergeChoice.REPLACE
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = if (resolution == MergeChoice.REPLACE) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors()
                            )
                            TextButton(
                                text = "保留旧信息",
                                onClick = {
                                    BadgerLog.d("ScanModeDialog", "冲突解决: field=$fieldKey, choice=KEEP")
                                    conflictResolutions[fieldKey] = MergeChoice.KEEP
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = if (resolution == MergeChoice.KEEP) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors()
                            )
                        }
                        if (resolution != MergeChoice.APPEND) {
                            Text(
                                text = "全部保留",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    BadgerLog.d("ScanModeDialog", "冲突解决: field=$fieldKey, choice=APPEND")
                                    conflictResolutions[fieldKey] = MergeChoice.APPEND
                                }.padding(vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = "取消",
                        onClick = { showConflictDialog = false },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "确认",
                        onClick = {
                            onConfirm(
                                buildScanResults(qrCodeContents, resolveStates, duplicateFieldKeys),
                                existingContact,
                                conflictResolutions.toMap(),
                                markerConfig
                            )
                            showConflictDialog = false
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        enabled = conflictResolutions.size == conflictFieldMap.size
                    )
                }
            }
        }
    }

    
    if (showContactPicker) {
        ContactPickerDialog(
            repository = repository,
            onDismiss = { showContactPicker = false },
            onContactSelected = { contact ->
                showContactPicker = false
                val content = qrCodeContents.firstOrNull()
                val state = content?.let { resolveStates[it] }
                val info = state?.extractedInfo?.copy(avatarUrl = state.avatarUrl)
                    ?: ExtractedContactInfo(
                        avatarUrl = state?.avatarUrl,
                        rawText = content ?: "",
                        platforms = if (content?.startsWith("http") == true) mapOf("website" to content) else emptyMap(),
                        otherInfo = listOf(content ?: "")
                    )
                BadgerLog.d("ScanModeDialog", "附加到已有: contact=${contact.name}, info=$info")
                onAttachToExisting(contact, info, markerConfig)
            }
        )
    }
}

private fun buildScanResults(
    qrCodeContents: List<String>,
    resolveStates: Map<String, QrResolveState>,
    excludeDuplicateKeys: Set<String> = emptySet(),
): List<Pair<String, ExtractedContactInfo>> {
    return qrCodeContents.map { c ->
        val s = resolveStates[c]
        val info = s?.extractedInfo?.copy(avatarUrl = s.avatarUrl)
            ?: ExtractedContactInfo(
                avatarUrl = s?.avatarUrl,
                rawText = c,
                platforms = if (c.startsWith("http")) mapOf("website" to c) else emptyMap(),
                otherInfo = listOf(c)
            )
        if (excludeDuplicateKeys.isEmpty()) {
            c to info
        } else {
            val filtered = info.platforms.filterNot { excludeDuplicateKeys.contains(it.key) }
            c to info.copy(platforms = filtered)
        }
    }
}

fun parseLocalContent(content: String): ExtractedContactInfo? {
    var name: String? = null
    var phone: String? = null
    var email: String? = null
    var matched = false
    val platforms = mutableMapOf<String, String>()

    if (content.contains("BEGIN:VCARD")) {
        content.lines().forEach { line ->
            when {
                line.startsWith("FN:") -> name = line.removePrefix("FN:")
                line.startsWith("TEL:") -> phone = line.removePrefix("TEL:")
                line.startsWith("EMAIL:") -> email = line.removePrefix("EMAIL:")
            }
        }
        matched = true
    } else if (Regex("^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$").matches(content)) {
        email = content
        matched = true
    } else if (Regex("^1[3-9]\\d{9}$").matches(content)) {
        phone = content
        matched = true
    } else if (Regex("^\\d{5,15}$").matches(content)) {
        
        platforms["qq"] = content
        matched = true
    } else if (content.contains("qq.com") || content.contains("tencent.com")) {
        
        val qqMatch1 = Regex("\\d{5,15}").find(content)
        val qqMatch3 = Regex("qq\\.com/(?:user|home)\\?qq=(\\d+)").find(content)

        val qqValue = qqMatch1?.value ?: qqMatch3?.groupValues?.getOrNull(1)
        qqValue?.let {
            platforms["qq"] = it
        }
        matched = true
    }

    return if (matched) {
        ExtractedContactInfo(name = name, phone = phone, email = email, platforms = platforms, rawText = content, otherInfo = emptyList())
    } else null
}
