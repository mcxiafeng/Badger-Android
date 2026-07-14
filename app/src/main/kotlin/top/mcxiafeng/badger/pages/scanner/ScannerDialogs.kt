package top.mcxiafeng.badger.pages.scanner

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.data.MergeChoice
import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.network.ContactType
import top.mcxiafeng.badger.ocr.ExtractedContactInfo
import top.mcxiafeng.badger.ocr.PLATFORM_FIELDS
import top.mcxiafeng.badger.ocr.buildPlatformLink
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「本次扫描标记 Tag」配置。
 *
 * 由 ResultDialog 顶部 UI 维护 → 透传给 ScannerPage 的 onConfirm / onAttachToExisting,
 * 后者用 [tagId] 给本次保存的所有联系人打同一个标记 Tag。
 *
 * 是否启用 = [tagId] != null（选了 Tag 即启用,选「无」即关闭）。不再保留中间态。
 *
 * @property tagId 选中的 Tag.id;`null` 表示「无」(显式清空或不标记)
 * @property tagName / tagColor 仅用于 chip 展示,实际写入靠 tagId
 */
data class ScanMarkerConfig(
    val tagId: Long? = null,
    val tagName: String = "",
    val tagColor: Long = 0xFF1976D2L,
) {
    /** 是否启用了标记:tagId != null 即视为开启 */
    val enabled: Boolean get() = tagId != null
}

/**
 * 结果对话框顶部「本次扫描标记」配置行 —— 由 ScannerDialogs 内统一调用。
 *
 * UI: `标记扫描:` + 两个 chip(`标签` / `无`),互斥单选。
 * - 点 `标签` → 弹 [ScanMarkerPickerDialog] 选已有 / 新建 Tag
 * - 点 `无`   → 清空当前选择
 *
 * 只在 [enabled]=true 且 [isImportToProfile]=false 时调用。
 */
@Composable
internal fun ScanMarkerConfigRow(
    markerConfig: ScanMarkerConfig,
    onMarkerConfigChange: (ScanMarkerConfig) -> Unit,
    tagRepository: TagRepository,
    enabled: Boolean = true,
) {
    if (!enabled) return  // 导入到我的名片时跳过
    val cs = MiuixTheme.colorScheme
    var showPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "标记扫描",
            style = MiuixTheme.textStyles.body2,
            color = cs.onSurface,
        )
        // chip 工厂:统一背景/圆角/内边距,只换 content 和 selected
        @Composable
        fun ChoiceChip(
            text: String,
            selected: Boolean,
            onClick: () -> Unit,
            leading: (@Composable () -> Unit)? = null,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selected) cs.primary.copy(alpha = 0.14f)
                        else cs.surfaceContainer
                    )
                    .clickable(onClick = onClick)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (leading != null) {
                        leading()
                        Spacer(modifier = Modifier.size(6.dp))
                    }
                    Text(
                        text = text,
                        style = MiuixTheme.textStyles.body2,
                        color = if (selected) cs.primary else cs.onSurface,
                    )
                }
            }
        }

        ChoiceChip(
            text = if (markerConfig.tagId == null) "标签" else markerConfig.tagName,
            selected = markerConfig.tagId != null,
            onClick = { showPicker = true },
            leading = if (markerConfig.tagId != null) {
                {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(markerConfig.tagColor))
                    )
                }
            } else null,
        )
        ChoiceChip(
            text = "无",
            selected = markerConfig.tagId == null,
            onClick = { onMarkerConfigChange(ScanMarkerConfig()) },
        )
    }

    if (showPicker) {
        ScanMarkerPickerDialog(
            show = true,
            tagRepository = tagRepository,
            currentTagId = markerConfig.tagId,
            onDismiss = { showPicker = false },
            onPicked = { pickedId, pickedName, pickedColor ->
                onMarkerConfigChange(
                    ScanMarkerConfig(
                        tagId = pickedId,
                        tagName = pickedName,
                        tagColor = pickedColor,
                    )
                )
            },
        )
    }
}

/**
 * 扫描结果对话框
 *
 * 拍照模式（isPhotoMode=true）：所有二维码和 OCR 结果属于同一个人，
 * 合并展示为字段级列表，用户可勾选要添加的字段。
 *
 * 扫码模式（isPhotoMode=false）：每个二维码可能属于不同人，
 * 展示为联系人级列表，用户可勾选要添加的联系人。
 *
 * 顶部「本次扫描标记 Tag」配置面板(`isImportToProfile=false` 时显示):
 * 让用户在 ResultDialog 顶部就能选择本次扫描要打的标记 Tag,
 * 通过 [onConfirm] / [onAttachToExisting] 的 markerConfig 参数透传给 ScannerPage。
 *
 * @param repository 数据仓库
 * @param show 是否显示
 * @param qrCodeContents 所有二维码原始内容
 * @param ocrExtractedInfo OCR 提取的联系人信息（拍照模式）
 * @param isPhotoMode 是否拍照模式（true=合并为一人，false=独立联系人列表）
 * @param isProcessingPhoto 是否正在处理拍照（图片识别阶段）
 * @param aiOcrError AI OCR 错误信息
 * @param onDismiss 关闭回调
 * @param onConfirm 确认保存回调(markerConfig = 本次扫描标记 Tag 配置)
 * @param onAttachToExisting 附加到已有联系人回调(同上)
 */
@Composable
internal fun ResultDialog(
    repository: ContactRepository,
    fieldRepository: FieldRepository,
    show: Boolean,
    qrCodeContents: List<String>,
    ocrExtractedInfo: ExtractedContactInfo? = null,
    isPhotoMode: Boolean = false,
    isProcessingPhoto: Boolean = false,
    aiOcrError: String? = null,
    photoNoResult: Boolean = false,
    isImportToProfile: Boolean = false,
    tagRepository: TagRepository? = null,
    onDismiss: () -> Unit,
    onConfirm: (List<Pair<String, ExtractedContactInfo>>, Contact?, Map<String, MergeChoice>, ScanMarkerConfig) -> Unit,
    onAttachToExisting: (Contact, ExtractedContactInfo, ScanMarkerConfig) -> Unit
) {
    val scope = rememberCoroutineScope()

    // 每个二维码的独立解析状态
    val resolveStates = remember { mutableStateMapOf<String, QrResolveState>() }

    // OCR 结果中平台 ID 的网络解析状态（key 为 "ocr:{platformKey}"）
    val ocrResolveStates = remember { mutableStateMapOf<String, QrResolveState>() }

    // 为每个二维码初始化解析状态并触发异步网络解析
    LaunchedEffect(qrCodeContents) {
        qrCodeContents.forEach { content ->
            if (content !in resolveStates) {
                val localInfo = parseLocalContent(content)
                resolveStates[content] = QrResolveState(
                    qrContent = content,
                    extractedInfo = localInfo,
                    isLoading = false
                )
            }
            val state = resolveStates[content]!!
            if (!state.isLoading && !state.isLoaded) {
                val needsNetwork = content.startsWith("http://") || content.startsWith("https://") ||
                        content.contains("qq.com") || content.startsWith("mqq://")
                if (needsNetwork) {
                    resolveStates[content] = state.copy(isLoading = true)
                    scope.launch(Dispatchers.IO) {
                        try {
                            val result = ContactNetworkResolver.getResultInfo(content, mutableMapOf())
                            val info = if (result != null && result.type != ContactType.None) {
                                val (_, extractedInfo) = ContactNetworkResolver.toContactAndInfo(result, content)
                                extractedInfo
                            } else {
                                // 网络解析返回 None 或失败：将原始内容作为 website 平台条目保存，确保详情页可见
                                state.extractedInfo ?: ExtractedContactInfo(
                                    rawText = content,
                                    platforms = if (content.startsWith("http")) mapOf("website" to content) else emptyMap(),
                                    otherInfo = listOf(content)
                                )
                            }
                            withContext(Dispatchers.Main) {
                                resolveStates[content] = resolveStates[content]?.copy(
                                    networkResult = result,
                                    extractedInfo = info ?: state.extractedInfo,
                                    isLoading = false
                                ) ?: QrResolveState(
                                    qrContent = content,
                                    networkResult = result,
                                    extractedInfo = info,
                                    isLoading = false
                                )
                            }
                        } catch (e: Exception) {
                            Log.d("ResultDialog", "网络解析失败: ${e.message}")
                            // 网络解析失败：保留已有的本地解析结果，若无则将原始内容作为 website 平台条目
                            val fallbackInfo = resolveStates[content]?.extractedInfo ?: ExtractedContactInfo(
                                rawText = content,
                                platforms = if (content.startsWith("http")) mapOf("website" to content) else emptyMap(),
                                otherInfo = listOf(content)
                            )
                            withContext(Dispatchers.Main) {
                                resolveStates[content] = resolveStates[content]?.copy(
                                    extractedInfo = fallbackInfo,
                                    isLoading = false,
                                    loadFailed = true
                                ) ?: QrResolveState(
                                    qrContent = content,
                                    extractedInfo = fallbackInfo,
                                    loadFailed = true
                                )
                            }
                        }
                    }
                } else {
                    // 非网络内容：保留本地解析结果，若无则将原始内容作为 website 条目
                    val localInfo = state.extractedInfo ?: ExtractedContactInfo(
                        rawText = content,
                        platforms = if (content.startsWith("http")) mapOf("website" to content) else emptyMap(),
                        otherInfo = listOf(content)
                    )
                    resolveStates[content] = state.copy(extractedInfo = localInfo, loadFailed = true)

                    // 本地解析出的平台 ID（如纯 QQ 号）也需二次网络解析以获取昵称/头像
                    if (localInfo.platforms.isNotEmpty()) {
                        for (def in PLATFORM_FIELDS) {
                            val value = localInfo.platforms[def.fieldKey] ?: continue
                            if (value.isBlank()) continue
                            val stateKey = "qr_local:${content}:${def.fieldKey}"
                            if (stateKey in ocrResolveStates) continue
                            ocrResolveStates[stateKey] = QrResolveState(qrContent = stateKey, isLoading = true)
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val adapterContent = buildPlatformLink(def.fieldKey, value)
                                    val result = ContactNetworkResolver.getResultInfo(adapterContent, mutableMapOf(), def.contactType)
                                    withContext(Dispatchers.Main) {
                                        ocrResolveStates[stateKey] = ocrResolveStates[stateKey]?.copy(
                                            networkResult = result,
                                            isLoading = false
                                        ) ?: QrResolveState(
                                            qrContent = stateKey,
                                            networkResult = result,
                                            isLoading = false
                                        )
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        ocrResolveStates[stateKey] = ocrResolveStates[stateKey]?.copy(
                                            isLoading = false,
                                            loadFailed = true
                                        ) ?: QrResolveState(
                                            qrContent = stateKey,
                                            loadFailed = true
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // OCR 结果中平台 ID 的二次网络解析（获取昵称/头像）
    LaunchedEffect(ocrExtractedInfo) {
        if (ocrExtractedInfo == null || !isPhotoMode) return@LaunchedEffect
        // 对有适配器的平台字段发起网络解析
        for (def in PLATFORM_FIELDS) {
            val value = ocrExtractedInfo.platforms[def.fieldKey]
            if (value.isNullOrBlank()) continue
            val type = def.contactType
            val key = def.fieldKey
            val stateKey = "ocr:$key"
            if (stateKey in ocrResolveStates) continue
            ocrResolveStates[stateKey] = QrResolveState(qrContent = stateKey, isLoading = true)
            scope.launch(Dispatchers.IO) {
                try {
                    val adapterContent = buildPlatformLink(key, value)
                    val result = ContactNetworkResolver.getResultInfo(adapterContent, mutableMapOf(), type)
                    withContext(Dispatchers.Main) {
                        ocrResolveStates[stateKey] = ocrResolveStates[stateKey]?.copy(
                            networkResult = result,
                            isLoading = false
                        ) ?: QrResolveState(
                            qrContent = stateKey,
                            networkResult = result,
                            isLoading = false
                        )
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        ocrResolveStates[stateKey] = ocrResolveStates[stateKey]?.copy(
                            isLoading = false,
                            loadFailed = true
                        ) ?: QrResolveState(
                            qrContent = stateKey,
                            loadFailed = true
                        )
                    }
                }
            }
        }
    }

    // 防止处理中误触返回键关闭
    BackHandler(enabled = isProcessingPhoto) { /* 拦截返回键 */ }

    // 根据 isProcessingPhoto 决定 onDismissRequest：处理中禁止关闭
    val dismissRequest = if (isProcessingPhoto) {{}} else onDismiss

    // 重复字段检测：导入到我的名片时跳过（UserProfile 与 Contact 表无关）
    var duplicateFieldKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var conflictFieldMap by remember { mutableStateOf<Map<String, ConflictFieldInfo>>(emptyMap()) }
    var duplicateExistingContact by remember { mutableStateOf<Contact?>(null) }
    var totalFieldCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(qrCodeContents, ocrExtractedInfo, isProcessingPhoto) {
        if (isImportToProfile) {
            duplicateFieldKeys = emptySet()
            conflictFieldMap = emptyMap()
            duplicateExistingContact = null
            totalFieldCount = 0
            return@LaunchedEffect
        }
        // 等待网络解析完成（最多等待 3 秒，每 100ms 检查一次）
        var waitCount = 0
        while (waitCount < 30) {
            val hasLoading = resolveStates.values.any { it.isLoading } ||
                           ocrResolveStates.values.any { it.isLoading }
            if (!hasLoading) break
            delay(100)
            waitCount++
        }

        val fieldValues = mutableMapOf<String, String>()
        val allResults = resolveStates.values.mapNotNull { it.networkResult } +
                ocrResolveStates.values.mapNotNull { it.networkResult }

        // 拍照模式：收集所有网络解析结果和OCR结果
        if (isPhotoMode) {
            for (result in allResults) {
                if (result.type == ContactType.QQGroup) {
                    result.contactMap["qqGroup"]?.let { fieldValues["qqGroup"] = it }
                } else if (result.type == ContactType.TelegramGroup) {
                    result.contactMap["telegramGroup"]?.let { fieldValues["telegramGroup"] = it }
                } else if (result.type != ContactType.None) {
                    val def = PLATFORM_FIELDS.find { it.contactType == result.type }
                    val key = def?.fieldKey ?: continue
                    result.contactMap[key]?.let { fieldValues[key] = it }
                }
            }
            ocrExtractedInfo?.let { info ->
                info.phone?.let { fieldValues["phone"] = it }
                info.email?.let { fieldValues["email"] = it }
                fieldValues.putAll(info.platforms)
            }
        } else {
            // 扫码模式：从二维码内容中解析字段
            qrCodeContents.forEach { content ->
                val localInfo = parseLocalContent(content)
                localInfo?.let { info ->
                    info.phone?.let { fieldValues["phone"] = it }
                    info.email?.let { fieldValues["email"] = it }
                    fieldValues.putAll(info.platforms)
                }
            }
            // 同时也收集网络解析结果
            for (result in allResults) {
                if (result.type == ContactType.QQGroup) {
                    result.contactMap["qqGroup"]?.let { fieldValues["qqGroup"] = it }
                } else if (result.type == ContactType.TelegramGroup) {
                    result.contactMap["telegramGroup"]?.let { fieldValues["telegramGroup"] = it }
                } else if (result.type != ContactType.None) {
                    val def = PLATFORM_FIELDS.find { it.contactType == result.type }
                    val key = def?.fieldKey ?: continue
                    result.contactMap[key]?.let { fieldValues[key] = it }
                }
            }
        }

        if (fieldValues.isEmpty()) {
            duplicateFieldKeys = emptySet()
            return@LaunchedEffect
        }
        val dupResult = withContext(Dispatchers.IO) {
            repository.checkDuplicate(
                newContactName = ocrExtractedInfo?.name ?: "未知联系人",
                fieldValues = fieldValues,
                customFieldValues = emptyMap()
            )
        }
        if (dupResult.existingContact != null) {
            val existingMap = withContext(Dispatchers.IO) {
                fieldRepository.getFieldValueMapByContact(dupResult.existingContact.id)
            }
            // 同 key 同值 → 重复（黄色标签，禁止选择）
            duplicateFieldKeys = fieldValues.keys.filter { key ->
                val newValue = fieldValues[key] ?: return@filter false
                val existingValue = existingMap[key]
                existingValue != null && existingValue == newValue
            }.toSet()
            // 同 key 不同值 → 冲突（红色标签，可选择，弹子对话框）
            conflictFieldMap = fieldValues.mapNotNull { (key, newValue) ->
                val existingValue = existingMap[key] ?: return@mapNotNull null
                if (key in duplicateFieldKeys) return@mapNotNull null
                if (existingValue == newValue) return@mapNotNull null
                key to ConflictFieldInfo(existingValue, newValue)
            }.toMap()
            duplicateExistingContact = dupResult.existingContact
            totalFieldCount = fieldValues.size
            Log.d("Tester", "ScannerDialogs: 重复检测命中 contactId=${dupResult.existingContact.id}, duplicateFieldKeys=$duplicateFieldKeys, conflictFieldKeys=${conflictFieldMap.keys}, isPhotoMode=$isPhotoMode")
        } else {
            duplicateFieldKeys = emptySet()
            conflictFieldMap = emptyMap()
            duplicateExistingContact = null
            totalFieldCount = 0
            Log.d("Tester", "ScannerDialogs: 重复检测无命中, isPhotoMode=$isPhotoMode")
        }
    }

    val hasMergeableFields = duplicateExistingContact != null &&
        (conflictFieldMap.isNotEmpty() || totalFieldCount > duplicateFieldKeys.size)

    // 「本次扫描标记 Tag」配置状态
    // [修复防御]: 默认 enabled=false,避免每次扫描都被打上标记(用户需主动开启 + 选 Tag)
    var markerConfig by remember { mutableStateOf(ScanMarkerConfig()) }

    if (isPhotoMode) {
        PhotoModeDialog(
            show = show,
            qrCodeContents = qrCodeContents,
            ocrExtractedInfo = ocrExtractedInfo,
            resolveStates = resolveStates,
            ocrResolveStates = ocrResolveStates,
            isProcessingPhoto = isProcessingPhoto,
            photoNoResult = photoNoResult,
            duplicateFieldKeys = duplicateFieldKeys,
            conflictFieldMap = conflictFieldMap,
            existingContact = duplicateExistingContact,
            hasMergeableFields = hasMergeableFields,
            isImportToProfile = isImportToProfile,
            repository = repository,
            tagRepository = tagRepository,
            markerConfig = markerConfig,
            onMarkerConfigChange = { markerConfig = it },
            onDismiss = dismissRequest,
            onConfirm = onConfirm,
            onAttachToExisting = onAttachToExisting
        )
    } else {
        ScanModeDialog(
            show = show,
            qrCodeContents = qrCodeContents,
            resolveStates = resolveStates,
            isProcessingPhoto = isProcessingPhoto,
            duplicateFieldKeys = duplicateFieldKeys,
            conflictFieldMap = conflictFieldMap,
            existingContact = duplicateExistingContact,
            hasMergeableFields = hasMergeableFields,
            isImportToProfile = isImportToProfile,
            repository = repository,
            tagRepository = tagRepository,
            markerConfig = markerConfig,
            onMarkerConfigChange = { markerConfig = it },
            onDismiss = dismissRequest,
            onConfirm = onConfirm,
            onAttachToExisting = onAttachToExisting
        )
    }
}