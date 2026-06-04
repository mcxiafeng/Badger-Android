package top.mcxiafeng.badger.pages.scanner

import android.util.Log
import androidx.activity.compose.BackHandler
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
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.MergeChoice
import top.mcxiafeng.badger.ocr.ExtractedContactInfo
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 扫码模式：单结果简洁展示
 *
 * 扫码模式每次只识别一个二维码，无需列表/勾选/全选，
 * 直接展示识别结果的头像、名称、平台信息。
 */
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
    onDismiss: () -> Unit,
    onConfirm: (List<Pair<String, ExtractedContactInfo>>, Contact?, Map<String, MergeChoice>) -> Unit,
    onAddStyle: (Contact, ExtractedContactInfo) -> Unit
) {
    // 冲突字段的解决选择
    val conflictResolutions = remember { mutableStateMapOf<String, MergeChoice>() }
    // 是否显示冲突解决对话框
    var showConflictDialog by remember { mutableStateOf(false) }
    // 是否显示联系人选择器
    var showContactPicker by remember { mutableStateOf(false) }

    // 防止处理中误触返回键关闭
    BackHandler(enabled = isProcessingPhoto) { /* 拦截返回键 */ }

    if (show) WindowDialog(show = true, title = "扫描结果", onDismissRequest = onDismiss) {
        // 拍照处理中：显示加载动画
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
            if (content == null) {
                Text(
                    text = "未识别到有效信息",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                // 竖向卡片布局：头像居中 → 名称+平台标签 → ID
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    // 头像
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

                    // 名称
                    Text(
                        text = state?.displayName
                            ?: content.take(30).let { if (content.length > 30) "$it..." else it },
                        style = MiuixTheme.textStyles.subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // 平台标签 + 重复/冲突标识
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

                    // 平台 ID / 加载状态
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

            // 底部按钮
            val hasExisting = existingContact != null
            if (isImportToProfile) {
                // 导入到我的名片：只显示导入按钮
                TextButton(
                    text = "导入",
                    onClick = {
                        val results = qrCodeContents.map { c ->
                            val s = resolveStates[c]
                            val info = s?.extractedInfo?.copy(avatarUrl = s.avatarUrl)
                                ?: ExtractedContactInfo(
                                    avatarUrl = s?.avatarUrl,
                                    rawText = c,
                                    platforms = if (c.startsWith("http")) mapOf("website" to c) else emptyMap(),
                                    otherInfo = listOf(c)
                                )
                            c to info
                        }
                        onConfirm(results, null, emptyMap())
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    enabled = content != null
                )
            } else if (hasExisting) {
                // 有重复联系人：合并信息（主按钮） + 追加样式
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
                                val results = qrCodeContents.map { c ->
                                    val s = resolveStates[c]
                                    val info = s?.extractedInfo?.copy(avatarUrl = s.avatarUrl)
                                        ?: ExtractedContactInfo(
                                            avatarUrl = s?.avatarUrl,
                                            rawText = c,
                                            platforms = if (c.startsWith("http")) mapOf("website" to c) else emptyMap(),
                                            otherInfo = listOf(c)
                                        )
                                    val filteredPlatforms = info.platforms.filterNot { duplicateFieldKeys.contains(it.key) }
                                    c to info.copy(platforms = filteredPlatforms)
                                }
                                onConfirm(results, existingContact, conflictResolutions.toMap())
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        enabled = content != null && hasMergeableFields
                    )
                    TextButton(
                        text = "追加样式",
                        onClick = {
                            Log.d("ScanModeDialog", "追加样式: existingContact=${existingContact!!.id}")
                            onAddStyle(existingContact!!, ExtractedContactInfo(rawText = qrCodeContents.firstOrNull() ?: ""))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                // 无重复联系人：附加到已有 + 添加新记录
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
                            val results = qrCodeContents.map { c ->
                                val s = resolveStates[c]
                                val info = s?.extractedInfo?.copy(avatarUrl = s.avatarUrl)
                                    ?: ExtractedContactInfo(
                                        avatarUrl = s?.avatarUrl,
                                        rawText = c,
                                        platforms = if (c.startsWith("http")) mapOf("website" to c) else emptyMap(),
                                        otherInfo = listOf(c)
                                    )
                                c to info
                            }
                            onConfirm(results, null, emptyMap())
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        enabled = content != null
                    )
                }
            }
        }
    }

    // 冲突解决子对话框（扫码模式：列出所有冲突）
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
                                    Log.d("ScanModeDialog", "冲突解决: field=$fieldKey, choice=REPLACE")
                                    conflictResolutions[fieldKey] = MergeChoice.REPLACE
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = if (resolution == MergeChoice.REPLACE) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors()
                            )
                            TextButton(
                                text = "保留旧信息",
                                onClick = {
                                    Log.d("ScanModeDialog", "冲突解决: field=$fieldKey, choice=KEEP")
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
                                    Log.d("ScanModeDialog", "冲突解决: field=$fieldKey, choice=APPEND")
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
                            val results = qrCodeContents.map { c ->
                                val s = resolveStates[c]
                                val info = s?.extractedInfo?.copy(avatarUrl = s.avatarUrl)
                                    ?: ExtractedContactInfo(
                                        avatarUrl = s?.avatarUrl,
                                        rawText = c,
                                        platforms = if (c.startsWith("http")) mapOf("website" to c) else emptyMap(),
                                        otherInfo = listOf(c)
                                    )
                                val filteredPlatforms = info.platforms.filterNot { duplicateFieldKeys.contains(it.key) }
                                c to info.copy(platforms = filteredPlatforms)
                            }
                            onConfirm(results, existingContact, conflictResolutions.toMap())
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

    // 联系人选择器
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
                Log.d("ScanModeDialog", "附加到已有: contact=${contact.name}, info=$info")
                onAddStyle(contact, info)
            }
        )
    }
}
internal fun parseLocalContent(content: String): ExtractedContactInfo? {
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
        // 纯数字 5-15 位，可能是 QQ 号
        platforms["qq"] = content
        matched = true
    } else if (content.contains("qq.com") || content.contains("tencent.com")) {
        // QQ 相关链接，尝试提取 QQ 号
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
