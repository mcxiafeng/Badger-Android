package top.mcxiafeng.badger.pages.scanner

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
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.data.model.MergeChoice
import top.mcxiafeng.badger.di.KoinComponentBy
import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.network.ContactType
import top.mcxiafeng.badger.network.IdentifyResponse
import top.mcxiafeng.badger.network.NetworkResolveResult
import top.mcxiafeng.badger.network.kindToContactType
import top.mcxiafeng.badger.ocr.ExtractedContactInfo
import top.mcxiafeng.badger.ocr.PLATFORM_FIELDS
import top.mcxiafeng.badger.ocr.buildPlatformLink
import top.mcxiafeng.badger.utils.SafeLog
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.platform.BackHandler
import top.mcxiafeng.badger.shared.util.BadgerDispatchers

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
/**
 * [修复防御]: 批量网络解析公共逻辑，消除两个 LaunchedEffect 中的重复代码。
 * 调用方只关心 (stateKey, response) 的回填逻辑。
 */
private suspend fun batchResolve(
    jobs: List<Pair<String, String>>,
    onResults: suspend (List<Pair<String, IdentifyResponse?>>) -> Unit
) {
    if (jobs.isEmpty()) return
    val responses = try {
        KoinComponentBy.get<ContactNetworkResolver>().identifyBatch(jobs.map { it.second })
    } catch (e: Throwable) {
        BadgerLog.w("ScannerDialogs", "batchResolve failed: ${e.message}")
        List(jobs.size) { null }
    }
    onResults(jobs.mapIndexed { i, (stateKey, _) -> stateKey to responses.getOrNull(i) })
}

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

    // OCR 结果中平台 ID 的网络解析状态（key 为 "ocr:{platformKey}" 或 "qr_local:{content}:{fieldKey}"）
    val ocrResolveStates = remember { mutableStateMapOf<String, QrResolveState>() }

    /**
     * 把 [IdentifyResponse] 投影成下游 [NetworkResolveResult] —— 与 [ContactNetworkResolver.identify]
     * 的逻辑对齐（含 `type` 兜底）但跳过它内部又调一次 `identify()` 的多余开销。
     */
    fun toNetworkResolveResult(resp: IdentifyResponse): NetworkResolveResult {
        val detected = kindToContactType(resp.kind) ?: ContactType.None
        return NetworkResolveResult(
            nickname = resp.name,
            description = resp.signature,
            avatarUrl = resp.avatarUrl,
            contactMap = resp.contactMap,
            type = detected,
        )
    }

    /**
     * 把「需要走服务端 resolver 的 URL」按 origin（原始 QR / OCR 补出来 / QR 本地解析出平台 ID）
     * 收集起来，一次性 POST /v1/resolver 批量提交。
     *
     * 旧实现对每个 URL 单独发请求 —— 多码场景（用户一次扫到 N 个码）下服务端
     * `RouteScanner` 日志能看到 N 行 `POST /v1/resolver`，客户端 / 服务端双倍开销。
     * 现在按 origin 各自一次 batch 调用，把网络 RTT 砍到 1 次。
     */
    LaunchedEffect(qrCodeContents) {
        // 1) 收集所有需要服务端解析的原始 QR 内容（http / qq.com / mqq://）。
        //    本地能解析的（纯文本 / vCard / 邮箱 / 手机号）依然走本地分支，只是不发网络请求。
        val networkQr = qrCodeContents.filter { content ->
            content.startsWith("http://") || content.startsWith("https://") ||
                content.contains("qq.com") || content.startsWith("mqq://")
        }
        // 先把每个 content 的本地解析结果填上（即便后面服务端失败也至少保留本地）。
        qrCodeContents.forEach { content ->
            if (content !in resolveStates) {
                val localInfo = parseLocalContent(content)
                resolveStates[content] = QrResolveState(
                    qrContent = content,
                    extractedInfo = localInfo,
                    isLoading = content in networkQr,
                )
            }
        }

        // 2) QR 本地解析出的平台 ID —— 二次网络解析以拿昵称/头像。
        //    这里组装 (stateKey, content) 二元组,后面 batch 一次性发。
        val qrLocalJobs = mutableListOf<Pair<String, String>>()
        qrCodeContents.forEach { content ->
            val state = resolveStates[content] ?: return@forEach
            if (state.isLoading || state.isLoaded) return@forEach  // 已经在 batch 路径里
            val localInfo = state.extractedInfo ?: return@forEach
            if (localInfo.platforms.isEmpty()) return@forEach
            for (def in PLATFORM_FIELDS) {
                val value = localInfo.platforms[def.fieldKey] ?: continue
                if (value.isBlank()) continue
                val stateKey = "qr_local:${content}:${def.fieldKey}"
                if (stateKey in ocrResolveStates) continue
                ocrResolveStates[stateKey] = QrResolveState(qrContent = stateKey, isLoading = true)
                qrLocalJobs += stateKey to buildPlatformLink(def.fieldKey, value)
            }
        }

        if (networkQr.isEmpty() && qrLocalJobs.isEmpty()) return@LaunchedEffect

        // [修复防御]: 使用公共 batchResolve 消除重复的网络调用+错误处理逻辑
        val jobs = mutableListOf<Pair<String, String>>()
        networkQr.forEach { content -> jobs += content to content }
        qrLocalJobs.forEach { (stateKey, adapterContent) -> jobs += stateKey to adapterContent }

        scope.launch(BadgerDispatchers.io) {
            batchResolve(jobs) { results ->
                // 把响应按 jobs 顺序回填：networkQr 的填 resolveStates，其余的填 ocrResolveStates。
                withContext(Dispatchers.Main) {
                    results.forEach { (stateKey, resp) ->
                        if (stateKey.startsWith("qr_local:")) {
                            val mapped = resp?.let { toNetworkResolveResult(it) }
                            ocrResolveStates[stateKey] = ocrResolveStates[stateKey]?.copy(
                                networkResult = mapped,
                                isLoading = false,
                                loadFailed = resp == null,
                            ) ?: QrResolveState(
                                qrContent = stateKey,
                                networkResult = mapped,
                                isLoading = false,
                                loadFailed = resp == null,
                            )
                        } else {
                            val st = resolveStates[stateKey]
                            if (st == null) return@forEach
                            val resolvedInfo = if (resp != null) {
                                st.extractedInfo ?: ExtractedContactInfo(
                                    rawText = stateKey,
                                    name = resp.name,
                                    avatarUrl = resp.avatarUrl,
                                    platforms = resp.contactMap,
                                    otherInfo = if (resp.name != null) emptyList() else listOf(stateKey),
                                )
                            } else {
                                st.extractedInfo ?: ExtractedContactInfo(
                                    rawText = stateKey,
                                    platforms = if (stateKey.startsWith("http")) mapOf("website" to stateKey) else emptyMap(),
                                    otherInfo = listOf(stateKey),
                                )
                            }
                            resolveStates[stateKey] = st.copy(
                                networkResult = resp?.let { toNetworkResolveResult(it) },
                                extractedInfo = resolvedInfo,
                                isLoading = false,
                                loadFailed = resp == null,
                            )
                        }
                    }
                }
            }
        }
    }

    // OCR 结果中平台 ID 的二次网络解析（获取昵称/头像）—— 也并入 batch。
    LaunchedEffect(ocrExtractedInfo) {
        if (ocrExtractedInfo == null || !isPhotoMode) return@LaunchedEffect
        val jobs = mutableListOf<Pair<String, String>>()
        for (def in PLATFORM_FIELDS) {
            val value = ocrExtractedInfo.platforms[def.fieldKey]
            if (value.isNullOrBlank()) continue
            val stateKey = "ocr:${def.fieldKey}"
            if (stateKey in ocrResolveStates) continue
            ocrResolveStates[stateKey] = QrResolveState(qrContent = stateKey, isLoading = true)
            jobs += stateKey to buildPlatformLink(def.fieldKey, value)
        }
        if (jobs.isEmpty()) return@LaunchedEffect
        // [修复防御]: 使用公共 batchResolve 消除重复的网络调用+错误处理逻辑
        scope.launch(BadgerDispatchers.io) {
            batchResolve(jobs) { results ->
                withContext(Dispatchers.Main) {
                    results.forEach { (stateKey, resp) ->
                        val mapped = resp?.let { toNetworkResolveResult(it) }
                        ocrResolveStates[stateKey] = ocrResolveStates[stateKey]?.copy(
                            networkResult = mapped,
                            isLoading = false,
                            loadFailed = resp == null,
                        ) ?: QrResolveState(
                            qrContent = stateKey,
                            networkResult = mapped,
                            isLoading = false,
                            loadFailed = resp == null,
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
        val dupResult = withContext(BadgerDispatchers.io) {
            repository.checkDuplicate(
                newContactName = ocrExtractedInfo?.name ?: "未知联系人",
                fieldValues = fieldValues,
                customFieldValues = emptyMap()
            )
        }
        val existingContact = dupResult.existingContact
        if (existingContact != null) {
            val existingMap = withContext(BadgerDispatchers.io) {
                fieldRepository.getFieldValueMapByContact(existingContact.id)
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
                    } else {
            duplicateFieldKeys = emptySet()
            conflictFieldMap = emptyMap()
            duplicateExistingContact = null
            totalFieldCount = 0
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