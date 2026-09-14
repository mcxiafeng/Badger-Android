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

data class ScanMarkerConfig(
    val tagId: Long? = null,
    val tagName: String = "",
    val tagColor: Long = 0xFF1976D2L,
) {
    
    val enabled: Boolean get() = tagId != null
}

@Composable
internal fun ScanMarkerConfigRow(
    markerConfig: ScanMarkerConfig,
    onMarkerConfigChange: (ScanMarkerConfig) -> Unit,
    tagRepository: TagRepository,
    enabled: Boolean = true,
) {
    if (!enabled) return  
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

    
    val resolveStates = remember { mutableStateMapOf<String, QrResolveState>() }

    
    val ocrResolveStates = remember { mutableStateMapOf<String, QrResolveState>() }

    

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

    

    LaunchedEffect(qrCodeContents) {
        
        
        val networkQr = qrCodeContents.filter { content ->
            content.startsWith("http://") || content.startsWith("https://") ||
                content.contains("qq.com") || content.startsWith("mqq://")
        }
        
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

        
        
        val qrLocalJobs = mutableListOf<Pair<String, String>>()
        qrCodeContents.forEach { content ->
            val state = resolveStates[content] ?: return@forEach
            if (state.isLoading || state.isLoaded) return@forEach  
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

        
        val jobs = mutableListOf<Pair<String, String>>()
        networkQr.forEach { content -> jobs += content to content }
        qrLocalJobs.forEach { (stateKey, adapterContent) -> jobs += stateKey to adapterContent }

        scope.launch(BadgerDispatchers.io) {
            batchResolve(jobs) { results ->
                
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

    
    BackHandler(enabled = isProcessingPhoto) {  }

    
    val dismissRequest = if (isProcessingPhoto) {{}} else onDismiss

    
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
        
        var waitCount = 0
        while (waitCount < RESOLVE_POLL_MAX_COUNT) {
            val hasLoading = resolveStates.values.any { it.isLoading } ||
                           ocrResolveStates.values.any { it.isLoading }
            if (!hasLoading) break
            delay(RESOLVE_POLL_INTERVAL_MS)
            waitCount++
        }

        val fieldValues = mutableMapOf<String, String>()
        val allResults = resolveStates.values.mapNotNull { it.networkResult } +
                ocrResolveStates.values.mapNotNull { it.networkResult }

        
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
            
            qrCodeContents.forEach { content ->
                val localInfo = parseLocalContent(content)
                localInfo?.let { info ->
                    info.phone?.let { fieldValues["phone"] = it }
                    info.email?.let { fieldValues["email"] = it }
                    fieldValues.putAll(info.platforms)
                }
            }
            
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
            
            duplicateFieldKeys = fieldValues.keys.filter { key ->
                val newValue = fieldValues[key] ?: return@filter false
                val existingValue = existingMap[key]
                existingValue != null && existingValue == newValue
            }.toSet()
            
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

private const val RESOLVE_POLL_MAX_COUNT = 30
private const val RESOLVE_POLL_INTERVAL_MS = 100L