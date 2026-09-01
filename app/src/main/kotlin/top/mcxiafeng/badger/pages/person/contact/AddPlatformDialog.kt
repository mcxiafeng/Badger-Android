package top.mcxiafeng.badger.pages.person.contact

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinInject
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity as UserProfile
import top.mcxiafeng.badger.data.repository.ContactMapper
import top.mcxiafeng.badger.network.PlatformManifestRepository
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ocr.LinkSource
import top.mcxiafeng.badger.ocr.buildPlatformLink
import top.mcxiafeng.badger.ocr.isUrlInput
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/** 弹窗模式：添加 or 编辑 */
enum class AddEditMode { ADD, EDIT }

/**
 * 添加/编辑社交平台对话框
 *
 * Phase 1: 图标网格选择平台
 * Phase 2: 按 LinkSource 分型的智能表单
 */
@Composable
fun AddPlatformWindowDialog(
    show: Boolean,
    mode: AddEditMode = AddEditMode.ADD,
    existingProfile: UserProfile? = null,
    editingEntry: Pair<String, PlatformEntry>? = null,
    onDismiss: () -> Unit,
    onConfirm: (fieldKey: String, entry: PlatformEntry) -> Unit
) {
    val context = LocalContext.current

    val manifestRepo: PlatformManifestRepository = koinInject()
    val addableDefs by manifestRepo.addable.collectAsState()
    LaunchedEffect(show) { if (show) manifestRepo.ensureLoaded() }

    val editFieldKey = editingEntry?.first
    val editData = editingEntry?.second

    val existingPlatformKeys = remember(existingProfile, editFieldKey) {
        val map = ContactMapper.decodePlatformsMap(existingProfile?.platformsJson)
        if (map == null) emptySet()
        else map.keys.filter { it != editFieldKey }.toSet()
    }

    var isGridPhase by remember { mutableStateOf(mode == AddEditMode.ADD) }
    var selectedFieldKey by remember { mutableStateOf(editFieldKey ?: "") }
    var isCustomMode by remember { mutableStateOf(false) }
    var mainInput by remember { mutableStateOf("") }
    var auxiliaryInput by remember { mutableStateOf("") }
    var customPlatformName by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var resolvedJumpLink by remember { mutableStateOf("") }
    var resolvedOriginalLink by remember { mutableStateOf("") }
    var resolvedValue by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(editFieldKey, editData) {
        if (mode == AddEditMode.EDIT && editData != null) {
            selectedFieldKey = editFieldKey ?: ""
            isCustomMode = editFieldKey == null || !FIELD_DEF_MAP.containsKey(editFieldKey)
            if (isCustomMode) customPlatformName = editFieldKey.orEmpty()
            displayName = editData.displayName ?: ""
            resolvedJumpLink = editData.jumpLink
            resolvedOriginalLink = editData.originalLink ?: ""
            resolvedValue = editData.value
            val def = FIELD_DEF_MAP[editFieldKey]
            if (def?.linkSource == LinkSource.LINK_ONLY && editData.jumpLink.isNotBlank() && !editData.value.isNullOrBlank()) {
                mainInput = editData.jumpLink
                auxiliaryInput = editData.value
            } else {
                mainInput = editData.value ?: editData.jumpLink
            }
            isGridPhase = false
        }
    }

    val currentFieldDef = remember(selectedFieldKey, addableDefs) {
        addableDefs.firstOrNull { it.fieldKey == selectedFieldKey } ?: FIELD_DEF_MAP[selectedFieldKey]
    }

    if (show) WindowDialog(
        show = true,
        title = if (mode == AddEditMode.EDIT) "编辑平台" else if (isGridPhase) "添加社交平台" else "添加 ${currentFieldDef?.displayName ?: customPlatformName}",
        summary = if (isGridPhase) "选择一个平台" else null,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
        ) {
            if (mode == AddEditMode.EDIT) {
                EditForm(
                    fieldKey = selectedFieldKey,
                    fieldDef = currentFieldDef,
                    isCustomMode = isCustomMode,
                    customPlatformName = customPlatformName,
                    mainInput = mainInput,
                    auxiliaryInput = auxiliaryInput,
                    displayName = displayName,
                    resolvedJumpLink = resolvedJumpLink,
                    errorMessage = errorMessage,
                    infoMessage = infoMessage,
                    isSaving = isSaving,
                    onMainInputChange = { mainInput = it; errorMessage = null; infoMessage = null },
                    onAuxiliaryInputChange = { auxiliaryInput = it },
                    onDisplayNameChange = { displayName = it },
                    onResolvedJumpLinkChange = { resolvedJumpLink = it },
                    onErrorMessageChange = { errorMessage = it },
                    onDismiss = onDismiss,
                    onSave = {
                        if (isSaving) return@EditForm
                        val fieldKey = if (isCustomMode) customPlatformName.trim().lowercase().ifBlank { selectedFieldKey } else selectedFieldKey
                        isSaving = true
                        onConfirm(fieldKey, PlatformEntry(
                            displayName = displayName.trim().ifBlank { null },
                            jumpLink = resolvedJumpLink.ifBlank { mainInput.trim() },
                            originalLink = resolvedOriginalLink.ifBlank { null },
                            value = mainInput.trim().ifBlank { null },
                            avatarUrl = editData?.avatarUrl
                        ))
                    }
                )
            } else if (isGridPhase) {
                PlatformGridSelector(
                    defs = addableDefs,
                    existingPlatformKeys = existingPlatformKeys,
                    onSelect = { fieldKey ->
                        selectedFieldKey = fieldKey
                        isCustomMode = false
                        isGridPhase = false
                        mainInput = ""
                        auxiliaryInput = ""
                        displayName = ""
                        resolvedJumpLink = ""
                        resolvedOriginalLink = ""
                        resolvedValue = null
                        errorMessage = null
                        infoMessage = null
                    },
                    onCustom = {
                        isCustomMode = true
                        isGridPhase = false
                        selectedFieldKey = ""
                        mainInput = ""
                        customPlatformName = ""
                        errorMessage = null
                        infoMessage = null
                    }
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    IconButton(onClick = {
                        isGridPhase = true
                        mainInput = ""
                        auxiliaryInput = ""
                        displayName = ""
                        resolvedJumpLink = ""
                        resolvedOriginalLink = ""
                        resolvedValue = null
                        errorMessage = null
                        infoMessage = null
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MiuixTheme.colorScheme.onBackground)
                    }
                    Text(text = "添加 ${currentFieldDef?.displayName ?: "自定义"}", style = MiuixTheme.textStyles.title3)
                }

                if (isCustomMode) {
                    CustomPlatformForm(
                        customPlatformName = customPlatformName,
                        onCustomPlatformNameChange = { customPlatformName = it; errorMessage = null },
                        mainInput = mainInput,
                        onMainInputChange = { mainInput = it; errorMessage = null; infoMessage = null },
                        displayName = displayName,
                        onDisplayNameChange = { displayName = it },
                        errorMessage = errorMessage,
                    )
                } else {
                    PlatformForm(
                        fieldDef = currentFieldDef,
                        mainInput = mainInput,
                        auxiliaryInput = auxiliaryInput,
                        displayName = displayName,
                        errorMessage = errorMessage,
                        infoMessage = infoMessage,
                        onMainInputChange = { mainInput = it; errorMessage = null; infoMessage = null },
                        onAuxiliaryInputChange = { auxiliaryInput = it },
                        onDisplayNameChange = { displayName = it },
                        fieldKey = selectedFieldKey,
                        onResolvedJumpLink = { resolvedJumpLink = it },
                        onResolvedOriginalLink = { resolvedOriginalLink = it },
                        onResolvedValue = { resolvedValue = it },
                        onInfoMessage = { infoMessage = it },
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    TextButton(text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f), enabled = !isSaving)
                    Spacer(Modifier.width(20.dp))
                    Button(
                        onClick = {
                            if (isSaving) return@Button
                            val fieldKey = if (isCustomMode) {
                                val name = customPlatformName.trim()
                                if (name.isBlank()) {
                                    errorMessage = "请输入平台名称"
                                    return@Button
                                }
                                name.lowercase()
                            } else selectedFieldKey
                            val input = mainInput.trim()
                            val auxInput = auxiliaryInput.trim()
                            if (input.isBlank() && auxInput.isBlank()) {
                                errorMessage = "请输入账号或链接"
                                return@Button
                            }
                            isSaving = true
                            val entry = if (isCustomMode) {
                                PlatformEntry(
                                    displayName = displayName.trim().ifBlank { null },
                                    jumpLink = if (isUrlInput(input)) input else "",
                                    value = if (isUrlInput(input)) null else input.ifBlank { null },
                                    originalLink = null
                                )
                            } else {
                                val urlInput = isUrlInput(input)
                                val def = FIELD_DEF_MAP[fieldKey]
                                if (urlInput) {
                                    PlatformEntry(
                                        displayName = displayName.trim().ifBlank { null },
                                        jumpLink = "",
                                        originalLink = input,
                                        value = if (auxInput.isNotBlank()) auxInput else input,
                                        avatarUrl = null,
                                    )
                                } else if (def?.linkSource == LinkSource.LINK_ONLY) {
                                    PlatformEntry(
                                        displayName = displayName.trim().ifBlank { null },
                                        jumpLink = "",
                                        originalLink = null,
                                        value = auxInput.ifBlank { input },
                                        avatarUrl = null
                                    )
                                } else {
                                    PlatformEntry(
                                        displayName = displayName.trim().ifBlank { null },
                                        jumpLink = buildPlatformLink(fieldKey, input),
                                        originalLink = null,
                                        value = input.ifBlank { null },
                                        avatarUrl = null
                                    )
                                }
                            }
                            onConfirm(fieldKey, entry)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColorsPrimary()
                    ) { Text("保存") }
                }
            }
        }
    }
}
