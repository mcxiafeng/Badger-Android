package top.mcxiafeng.badger.pages.scanner

import android.util.Log
import androidx.activity.compose.BackHandler
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
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.MergeChoice
import top.mcxiafeng.badger.network.ContactType
import top.mcxiafeng.badger.network.adapter.PlatformAdapterRegistry
import top.mcxiafeng.badger.ocr.ALIAS_TO_KEY_MAP
import top.mcxiafeng.badger.ocr.ExtractedContactInfo
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ocr.PLATFORM_FIELDS
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.mcxiafeng.badger.ui.components.PlatformIcon
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.mcxiafeng.badger.utils.miuixShape
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 拍照模式：所有信息合并为一个联系人，字段级勾选
 */
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
    onDismiss: () -> Unit,
    onConfirm: (List<Pair<String, ExtractedContactInfo>>, Contact?, Map<String, MergeChoice>) -> Unit,
    onAddStyle: (Contact, ExtractedContactInfo) -> Unit
) {
    // 信息获取优先级（QQ > B站 > 微信 > 抖音 > 微博 > GitHub > Telegram > 小红书 > X > Facebook > QQ群 > 网站）
    val infoPriority = remember {
        listOf(
            ContactType.QQ, ContactType.Bilibili, ContactType.WeChat, ContactType.TikTok,
            ContactType.Weibo, ContactType.GitHub, ContactType.Telegram, ContactType.Xiaohongshu,
            ContactType.X, ContactType.Facebook, ContactType.TelegramGroup, ContactType.QQGroup, ContactType.Website
        )
    }

    // 使用 rememberUpdatedState 确保 derivedStateOf 内部能读到最新值
    // 否则 lambda 闭包会捕获旧的 ocrExtractedInfo 参数（null），导致 OCR 字段永远不被处理
    val currentOcrInfo by rememberUpdatedState(ocrExtractedInfo)

    // 合并后的名字：所有来源（二维码+OCR网络）统一按优先级选择
    val mergedName by remember {
        derivedStateOf {
            val allResults = resolveStates.values.mapNotNull { it.networkResult } +
                    ocrResolveStates.values.mapNotNull { it.networkResult }
            infoPriority.firstNotNullOfOrNull { type ->
                allResults.firstOrNull { it.type == type && it.nickname.isNotBlank() }?.nickname
            } ?: allResults.firstOrNull { it.nickname.isNotBlank() }?.nickname
            ?: currentOcrInfo?.name
            ?: resolveStates.values.mapNotNull { it.extractedInfo?.name }.firstOrNull { it.isNotBlank() }
            ?: "未知联系人"
        }
    }

    // 合并后的头像 URL：所有来源统一按优先级选择
    val mergedAvatarUrl by remember {
        derivedStateOf {
            val allResults = resolveStates.values.mapNotNull { it.networkResult } +
                    ocrResolveStates.values.mapNotNull { it.networkResult }
            infoPriority.firstNotNullOfOrNull { type ->
                allResults.firstOrNull { it.type == type && it.avatarUrl.isNotBlank() }?.avatarUrl
            } ?: allResults.firstOrNull { it.avatarUrl.isNotBlank() }?.avatarUrl
            ?: currentOcrInfo?.avatarUrl
        }
    }

    // 是否有任何二维码还在加载
    val isAnyLoading by remember {
        derivedStateOf { resolveStates.values.any { it.isLoading } }
    }

    // 字段优先级（数值越小越靠前）
    val fieldOrder = remember {
        mapOf(
            "qq" to 0, "bilibili" to 1, "wechat" to 2, "phone" to 3, "email" to 4,
            "douyin" to 5, "weibo" to 6, "github" to 7, "telegram" to 8,
            "xiaohongshu" to 9, "x" to 10, "facebook" to 11, "telegramGroup" to 12, "qqGroup" to 13, "website" to 14
        )
    }

    // 合并字段列表：收集 → 按优先级排序 → 同key去重（website允许多个）
    val mergedFields by remember {
        derivedStateOf {
            val fields = mutableListOf<SelectableField>()

            // 从二维码/OCR 网络解析结果提取（统一逻辑）
            val allNetworkResults = resolveStates.values.mapNotNull { it.networkResult } +
                    ocrResolveStates.values.mapNotNull { it.networkResult }
            Log.d("Tester", "PhotoModeDialog: allNetworkResults=${allNetworkResults.map { "${it.type}→${it.contactMap}" }}, resolveStates=${resolveStates.keys}")
            for (result in allNetworkResults) {
                if (result.type == ContactType.QQGroup) {
                    result.contactMap["qqGroup"]?.let { fields.add(SelectableField("qqGroup", "QQ\u7fa4", it)) }
                    continue
                }
                if (result.type == ContactType.TelegramGroup) {
                    result.contactMap["telegramGroup"]?.let { fields.add(SelectableField("telegramGroup", "Telegram\u7fa4", it)) }
                    continue
                }
                if (result.type == ContactType.None) continue
                val def = PLATFORM_FIELDS.find { it.contactType == result.type } ?: continue
                val value = result.contactMap[def.fieldKey] ?: continue
                fields.add(SelectableField(def.fieldKey, def.displayName, value))
            }

            // \u4ece\u4e8c\u7ef4\u7801\u672c\u5730\u89e3\u6790\u7ed3\u679c\u63d0\u53d6\uff08parseLocalContent \u89e3\u6790\u7684 vCard/QQ\u53f7/\u624b\u673a\u53f7\u7b49\uff09
            for (state in resolveStates.values) {
                val info = state.extractedInfo ?: continue
                info.phone?.let { phoneStr ->
                    phoneStr.split(",", "\uff0c", ";", " ").filter { it.isNotBlank() }.forEachIndexed { idx, phone ->
                        val key = if (idx == 0) "phone" else "phone_$idx"
                        fields.add(SelectableField(key, "\u7535\u8bdd", phone.trim()))
                    }
                }
                info.email?.let { fields.add(SelectableField("email", "\u90ae\u7bb1", it)) }
                for ((key, value) in info.platforms) {
                    val def = FIELD_DEF_MAP[key]
                    fields.add(SelectableField(key, def?.displayName ?: key, value))
                }
            }

            // 从 OCR 结果提取联系方式
            currentOcrInfo?.let { info ->
                // 系统字段：phone 可能是逗号分隔的多个号码，拆分处理
                info.phone?.let { phoneStr ->
                    phoneStr.split(",", "，", ";", " ").filter { it.isNotBlank() }.forEachIndexed { idx, phone ->
                        val key = if (idx == 0) "phone" else "phone_$idx"
                        fields.add(SelectableField(key, "电话", phone.trim()))
                    }
                }
                info.email?.let { fields.add(SelectableField("email", "邮箱", it)) }
                // 平台字段（通过注册表获取 displayName）
                for ((key, value) in info.platforms) {
                    val def = FIELD_DEF_MAP[key]
                    fields.add(SelectableField(key, def?.displayName ?: key, value))
                }
                // otherInfo 中可映射到标准平台的字段
                info.otherInfo.forEach { otherItem ->
                    val colonIndex = otherItem.indexOfAny(charArrayOf(':', '：'))
                    if (colonIndex > 0) {
                        val key = otherItem.substring(0, colonIndex).lowercase().trim()
                        val value = otherItem.substring(colonIndex + 1).trim()
                        val fieldKey = ALIAS_TO_KEY_MAP[key]
                        if (fieldKey != null && value.isNotBlank()) {
                            val def = FIELD_DEF_MAP[fieldKey]
                            fields.add(SelectableField(fieldKey, def?.displayName ?: fieldKey, value))
                        }
                    }
                }
            }

            // 按优先级排序 → 同key去重 + 同value跨key去重（二维码结果优先于OCR误识别）
            val sorted = fields.sortedBy { fieldOrder[it.key] ?: 99 }
            Log.d("Tester", "PhotoModeDialog: sorted fields = ${sorted.map { "${it.key}=${it.value}" }}")
            sorted
                .fold(mutableListOf<SelectableField>() to (mutableSetOf<String>() to mutableSetOf<String>())) { (result, pair), field ->
                    val (seen, seenValues) = pair
                    // 所有字段都用 key:value 组合去重，允许同一平台多个不同值（如多个QQ号）
                    val dedupeKey = "${field.key}:${field.value}"
                    val valueKey = field.value
                    if (dedupeKey !in seen && valueKey !in seenValues) {
                        seen.add(dedupeKey)
                        seenValues.add(valueKey)
                        // 同 key 多值时加后缀区分（如 qq_1、qq_2）
                        if (result.any { it.key == field.key }) {
                            val idx = result.count { it.key == field.key || it.key.startsWith("${field.key}_") }
                            result.add(field.copy(key = "${field.key}_$idx"))
                        } else {
                            result.add(field)
                        }
                    }
                    result to (seen to seenValues)
                }.first
        }
    }

    // 字段勾选状态
    val checkedFields = remember { mutableStateSetOf<String>() }
    // 冲突字段的解决选择：fieldKey -> MergeChoice
    val conflictResolutions = remember { mutableStateMapOf<String, MergeChoice>() }
    // 当前显示冲突解决子对话框的字段 key
    var showConflictDialogFor by remember { mutableStateOf<String?>(null) }
    var showContactPicker by remember { mutableStateOf(false) }
    // 初始化：非重复/非冲突字段自动勾选，重复/冲突字段不勾选
    LaunchedEffect(mergedFields, duplicateFieldKeys, conflictFieldMap) {
        checkedFields.clear()
        conflictResolutions.clear()
        mergedFields.forEach { field ->
            if (field.key !in duplicateFieldKeys && field.key !in conflictFieldMap) {
                checkedFields.add(field.key)
            }
        }
    }

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
        // 拍照完成但无任何有效信息
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
            // 主行：头像 + 名字 + 平台标签
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

                // 全选行
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
                                // 只勾选非重复、非冲突字段
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

                    // 正在加载中的二维码数量提示
                    val loadingCount = resolveStates.values.count { it.isLoading }
                    if (loadingCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "($loadingCount 个码加载中)",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onBackgroundVariant
                        )
                    }

                    // 重复字段提示
                    if (duplicateFieldKeysSet.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${duplicateFieldKeysSet.size}个重复",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onBackgroundVariant
                        )
                    }
                    // 冲突字段提示
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

                // 字段列表
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
                        // 平台色块颜色
                        val fallbackTagColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        val tagColor = remember(field.key) {
                            val type = if (field.key == "qqGroup") ContactType.QQGroup
                                else if (field.key == "telegramGroup") ContactType.TelegramGroup
                                else if (field.key.startsWith("website")) ContactType.Website
                                else FIELD_DEF_MAP[field.key]?.contactType
                            type?.let {
                                PlatformAdapterRegistry.getTagInfo(it)?.second?.let { Color(it) }
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
                            // 平台图标
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

            // 底部按钮
            val hasChecked = checkedFields.isNotEmpty()
            val hasExisting = existingContact != null
            if (isImportToProfile) {
                // 导入到我的名片：只显示导入按钮
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
                        Log.d("PhotoModeDialog", "导入到我的名片: checkedFields=$checkedFields")
                        onConfirm(listOf(("__merged__") to info), null, emptyMap())
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    enabled = hasChecked
                )
            } else if (hasExisting && !hasChecked) {
                // 无可合并字段：取消 + 追加样式（主按钮），并提示用户原因
                Text(
                    text = "所有字段已存在，无需合并",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
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
                        text = "追加样式",
                        onClick = {
                            Log.d("PhotoModeDialog", "追加样式: existingContact=${existingContact!!.id}")
                            onAddStyle(existingContact!!, ExtractedContactInfo(rawText = qrCodeContents.firstOrNull() ?: ""))
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            } else if (hasExisting) {
                // 有可合并信息：合并信息（主按钮） + 追加样式
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
                            Log.d("PhotoModeDialog", "合并信息: checkedFields=$checkedFields, conflictResolutions=$conflictResolutions")
                            onConfirm(listOf(("__merged__") to info), existingContact, conflictResolutions.toMap())
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        enabled = hasChecked && hasMergeableFields
                    )
                    TextButton(
                        text = "追加样式",
                        onClick = {
                            Log.d("PhotoModeDialog", "追加样式: existingContact=${existingContact!!.id}")
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
                            onConfirm(listOf(("__merged__") to info), null, emptyMap())
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        enabled = hasChecked
                    )
                }
            }
        }
    }

    // 冲突字段解决子对话框
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
                            Log.d("PhotoModeDialog", "冲突解决: field=$fieldKey, choice=REPLACE")
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
                            Log.d("PhotoModeDialog", "冲突解决: field=$fieldKey, choice=KEEP")
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
                        Log.d("PhotoModeDialog", "冲突解决: field=$fieldKey, choice=APPEND")
                        conflictResolutions[fieldKey] = MergeChoice.APPEND
                        checkedFields.add(fieldKey)
                        showConflictDialogFor = null
                    }.padding(vertical = 4.dp)
                )
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
                Log.d("PhotoModeDialog", "附加到已有: contact=${contact.name}, info=$info")
                onAddStyle(contact, info)
            }
        )
    }
}