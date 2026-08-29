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
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity as UserProfile
import top.mcxiafeng.badger.data.repository.ContactMapper
import top.mcxiafeng.badger.di.KoinComponentBy
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

/**
 * 弹窗模式：添加 or 编辑
 */
enum class AddEditMode { ADD, EDIT }

/**
 * 添加/编辑社交平台对话框
 *
 * Phase 1: 图标网格选择平台
 * Phase 2: 按 LinkSource 分型的智能表单
 *
 * @param show 是否显示
 * @param mode 添加/编辑模式
 * @param existingPlatforms 已添加平台的 fieldKey 集合
 * @param initialFieldKey 编辑模式下的初始 fieldKey
 * @param initialEntry 编辑模式下的初始 PlatformEntry
 * @param onConfirm 确认回调，参数为 (fieldKey, PlatformEntry)
 * @param onDismiss 关闭回调
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

    // [Phase 4 剩余] 平台清单服务端驱动：拉取并缓存合并后的可添加 defs（离线兜底本地）。
    // 打开对话框即触发惰性加载（30s TTL 防抖），成功后 StateFlow 更新自动重组网格。
    val manifestRepo = remember { KoinComponentBy.get<PlatformManifestRepository>() }
    val addableDefs by manifestRepo.addable.collectAsState()
    LaunchedEffect(show) { if (show) manifestRepo.ensureLoaded() }

    // 从 editingEntry 解析出 fieldKey
    val editFieldKey = editingEntry?.first
    val editData = editingEntry?.second

    // 已添加平台的 fieldKey 集合
    val existingPlatformKeys = remember(existingProfile, editFieldKey) {
        val map = ContactMapper.decodePlatformsMap(existingProfile?.platformsJson)
        if (map == null) emptySet()
        else map.keys
            .filter { it != editFieldKey }
            .toSet()
    }

    // Phase 状态：true=图标网格, false=表单
    var isGridPhase by remember { mutableStateOf(mode == AddEditMode.ADD) }
    // 选中的平台 fieldKey
    var selectedFieldKey by remember { mutableStateOf(editFieldKey ?: "") }
    // 是否自定义平台
    var isCustomMode by remember { mutableStateOf(false) }

    // 表单字段（使用 remember 确保每次对话框重新挂载时状态清零，避免不同编辑会话间数据串扰）
    var mainInput by remember { mutableStateOf("") }       // 主输入框（账号或链接）
    var auxiliaryInput by remember { mutableStateOf("") }   // 辅助输入框（抖音号/小红书号）
    var customPlatformName by remember { mutableStateOf("") } // 自定义平台名
    var displayName by remember { mutableStateOf("") }       // 昵称
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var resolvedJumpLink by remember { mutableStateOf("") }   // 解析后的 jumpLink
    var resolvedOriginalLink by remember { mutableStateOf("") } // 解析后的 originalLink
    var resolvedValue by remember { mutableStateOf<String?>(null) } // 解析后的 value

    // 编辑模式初始化：用 LaunchedEffect 在 composition 完成后执行，
    // 避免与 remember 初始值产生竞态，确保每次打开对话框都加载当前平台的最新数据。
    LaunchedEffect(editFieldKey, editData) {
        if (mode == AddEditMode.EDIT && editData != null) {
            selectedFieldKey = editFieldKey ?: ""
            isCustomMode = editFieldKey == null || !FIELD_DEF_MAP.containsKey(editFieldKey)
            if (isCustomMode) {
                customPlatformName = editingEntry.first ?: ""
            }
            displayName = editData.displayName ?: ""
            resolvedJumpLink = editData.jumpLink
            resolvedOriginalLink = editData.originalLink ?: ""
            resolvedValue = editData.value
            // LINK_ONLY 平台：如果 jumpLink 有值（粘贴过链接），mainInput 显示链接，auxiliaryInput 显示 value（如抖音号）
            // 否则 mainInput 显示 value（如微信号）
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

    // 当前平台的字段定义（[Phase 4 剩余]：先查服务端合并 defs，再退回本地 FIELD_DEF_MAP ——
    // 服务端独有/自定义平台也能拿到动态 def，走统一表单逻辑）。
    val currentFieldDef = remember(selectedFieldKey, addableDefs) {
        addableDefs.firstOrNull { it.fieldKey == selectedFieldKey }
            ?: FIELD_DEF_MAP[selectedFieldKey]
    }

    if (show) WindowDialog(
        show = true,
        title = if (mode == AddEditMode.EDIT) "编辑平台" else if (isGridPhase) "添加社交平台" else "添加 ${currentFieldDef?.displayName ?: customPlatformName}",
        summary = if (isGridPhase) "选择一个平台" else null,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            if (mode == AddEditMode.EDIT) {
                // ========== 编辑模式：纯表单 ==========
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
                // ========== Phase 1: 图标网格选择 ==========
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
                // ========== Phase 2: 表单（按 LinkSource 分型） ==========
                // 返回按钮
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
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = "添加 ${currentFieldDef?.displayName ?: "自定义"}",
                        style = MiuixTheme.textStyles.title3
                    )
                }

                if (isCustomMode) {
                    // ========== 自定义平台表单 ==========
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
                    // ========== 预设平台表单（按 LinkSource 分型） ==========
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

                Spacer(modifier = Modifier.height(16.dp))

                // 保存按钮
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(
                        text = "取消",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        enabled = !isSaving
                    )
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
                            } else {
                                selectedFieldKey
                            }

                            val input = mainInput.trim()
                            val auxInput = auxiliaryInput.trim()

                            if (input.isBlank() && auxInput.isBlank()) {
                                errorMessage = "请输入账号或链接"
                                return@Button
                            }

                            isSaving = true

                            val entry = if (isCustomMode) {
                                // 自定义平台：直接存
                                PlatformEntry(
                                    displayName = displayName.trim().ifBlank { null },
                                    jumpLink = if (isUrlInput(input)) input else "",
                                    value = if (isUrlInput(input)) null else input.ifBlank { null },
                                    originalLink = null
                                )
                            } else {
                                // 预设平台：走解析逻辑
                                val isUrlInput = isUrlInput(input)
                                val def = FIELD_DEF_MAP[fieldKey]

                                if (isUrlInput) {
                                    // 粘贴链接 → 直接使用输入（服务端 ContactNetworkResolver 负责真正解析）
                                    PlatformEntry(
                                        displayName = displayName.trim().ifBlank { null },
                                        jumpLink = "",
                                        originalLink = input,
                                        value = if (auxInput.isNotBlank()) auxInput else input,
                                        avatarUrl = null,
                                    )
                                } else if (def?.linkSource == LinkSource.LINK_ONLY) {
                                    // LINK_ONLY 平台，非 http 输入 → 不生成链接，存辅助字段
                                    PlatformEntry(
                                        displayName = displayName.trim().ifBlank { null },
                                        jumpLink = "",
                                        originalLink = null,
                                        value = auxInput.ifBlank { input },
                                        avatarUrl = null
                                    )
                                } else {
                                    // AUTO/NO_LINK：用 buildPlatformLink 生成链接
                                    val generatedLink = buildPlatformLink(fieldKey, input)
                                    PlatformEntry(
                                        displayName = displayName.trim().ifBlank { null },
                                        jumpLink = generatedLink,
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
                    ) {
                        Text(text = "保存")
                    }
                }
            }
        }
    }
}
