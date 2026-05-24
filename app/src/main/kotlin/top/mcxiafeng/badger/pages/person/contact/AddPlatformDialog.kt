package top.mcxiafeng.badger.pages.person.contact

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.UserProfile
import top.mcxiafeng.badger.network.LinkResolver
import top.mcxiafeng.badger.network.PlatformIdExtractor
import top.mcxiafeng.badger.ocr.ADDABLE_PLATFORMS
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ocr.LinkSource
import top.mcxiafeng.badger.ocr.PlatformFieldDef
import top.mcxiafeng.badger.ocr.buildPlatformLink
import top.mcxiafeng.badger.ui.components.PlatformIcon
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private const val TAG = "AddPlatformDialog"

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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddPlatformWindowDialog(
    show: Boolean,
    mode: AddEditMode = AddEditMode.ADD,
    existingProfile: UserProfile? = null,
    editingEntry: Pair<String, PlatformEntry>? = null,
    onDismiss: () -> Unit,
    onConfirm: (fieldKey: String, entry: PlatformEntry) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 从 editingEntry 解析出 fieldKey
    val editFieldKey = editingEntry?.first?.let { PlatformIdExtractor.normalizeToKey(it) }
    val editData = editingEntry?.second

    // 已添加平台的 fieldKey 集合
    val existingPlatformKeys = remember(existingProfile, editFieldKey) {
        existingProfile?.platforms?.keys
            ?.map { PlatformIdExtractor.normalizeToKey(it) }
            ?.filter { it != editFieldKey }
            ?.toSet() ?: emptySet()
    }

    // Phase 状态：true=图标网格, false=表单
    var isGridPhase by remember { mutableStateOf(mode == AddEditMode.ADD) }
    // 选中的平台 fieldKey
    var selectedFieldKey by remember { mutableStateOf(editFieldKey ?: "") }
    // 是否自定义平台
    var isCustomMode by remember { mutableStateOf(false) }

    // 表单字段
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

    // 编辑模式一次性初始化标记（防止清空输入框后旧数据被重新填回）
    var editInitialized by remember { mutableStateOf(false) }

    // 编辑模式初始化
    if (mode == AddEditMode.EDIT && editData != null && !editInitialized) {
        editInitialized = true
        selectedFieldKey = editFieldKey ?: ""
        isCustomMode = editFieldKey == null || !FIELD_DEF_MAP.containsKey(editFieldKey)
        if (isCustomMode) {
            customPlatformName = editingEntry?.first ?: ""
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

    // 重置对话框状态
    if (!show) {
        mainInput = ""
        auxiliaryInput = ""
        customPlatformName = ""
        displayName = ""
        errorMessage = null
        infoMessage = null
        isSaving = false
        resolvedJumpLink = ""
        resolvedOriginalLink = ""
        resolvedValue = null
        editInitialized = false
        if (mode == AddEditMode.ADD) {
            isGridPhase = true
            selectedFieldKey = ""
            isCustomMode = false
        }
    }

    // 当前平台的字段定义
    val currentFieldDef = remember(selectedFieldKey) {
        FIELD_DEF_MAP[selectedFieldKey]
    }

    WindowDialog(
        show = show,
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
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ADDABLE_PLATFORMS.forEach { def ->
                        val isExisting = def.fieldKey in existingPlatformKeys
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable(enabled = !isExisting) {
                                    selectedFieldKey = def.fieldKey
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
                                    Log.d(TAG, "选择平台: ${def.fieldKey}")
                                }
                                .padding(8.dp)
                        ) {
                            PlatformIcon(
                                fieldKey = def.fieldKey,
                                color = if (isExisting) MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.3f) else MiuixTheme.colorScheme.primary,
                                sizeDp = 32f
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = def.displayName,
                                fontSize = 12.sp,
                                color = if (isExisting) MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.3f) else MiuixTheme.colorScheme.onBackground,
                                maxLines = 1
                            )
                        }
                    }
                    // + 自定义
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable {
                                isCustomMode = true
                                isGridPhase = false
                                selectedFieldKey = ""
                                mainInput = ""
                                customPlatformName = ""
                                errorMessage = null
                                infoMessage = null
                                Log.d(TAG, "选择自定义平台")
                            }
                            .padding(8.dp)
                    ) {
                        PlatformIcon(
                            fieldKey = "website",
                            color = MiuixTheme.colorScheme.primary,
                            sizeDp = 32f
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "自定义",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onBackground,
                            maxLines = 1
                        )
                    }
                }
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
                        style = MiuixTheme.textStyles.title3,
                        fontWeight = FontWeight.Medium
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
                        scope = scope,
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

                            scope.launch(Dispatchers.IO) {
                                val entry = if (isCustomMode) {
                                    // 自定义平台：直接存
                                    PlatformEntry(
                                        displayName = displayName.trim().ifBlank { null },
                                        jumpLink = if (input.startsWith("http")) input else "",
                                        value = if (input.startsWith("http")) null else input.ifBlank { null },
                                        originalLink = null
                                    )
                                } else {
                                    // 预设平台：走解析逻辑
                                    val isUrlInput = input.startsWith("http://") || input.startsWith("https://")
                                    val def = FIELD_DEF_MAP[fieldKey]

                                    if (isUrlInput) {
                                        // 粘贴链接 → LinkResolver 解析
                                        val result = LinkResolver.resolve(fieldKey, input)
                                        val value = if (auxInput.isNotBlank()) auxInput else result.value
                                        LinkResolver.toPlatformEntry(
                                            LinkResolver.LinkResolveResult(
                                                jumpLink = result.jumpLink,
                                                originalLink = result.originalLink,
                                                value = value,
                                                displayName = result.displayName ?: displayName.trim().ifBlank { null },
                                                avatarUrl = result.avatarUrl,
                                                errorMessage = null
                                            )
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

                                withContext(Dispatchers.Main) {
                                    onConfirm(fieldKey, entry)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColorsPrimary()
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                size = 18.dp,
                                strokeWidth = 2.dp,
                                colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                    foregroundColor = Color.White,
                                    backgroundColor = Color.White.copy(alpha = 0.3f)
                                )
                            )
                        } else {
                            Text(text = "保存")
                        }
                    }
                }
            }
        }
    }
}

/**
 * 编辑模式表单
 */
@Composable
private fun EditForm(
    fieldKey: String,
    fieldDef: PlatformFieldDef?,
    isCustomMode: Boolean,
    customPlatformName: String,
    mainInput: String,
    auxiliaryInput: String,
    displayName: String,
    resolvedJumpLink: String,
    errorMessage: String?,
    infoMessage: String?,
    isSaving: Boolean,
    onMainInputChange: (String) -> Unit,
    onAuxiliaryInputChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onResolvedJumpLinkChange: (String) -> Unit,
    onErrorMessageChange: (String?) -> Unit = {},
    onDismiss: () -> Unit = {},
    onSave: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // 平台名（只读展示）
    val platformName = if (isCustomMode) customPlatformName else (fieldDef?.displayName ?: fieldKey)
    Text(
        text = "平台",
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = platformName,
        style = MiuixTheme.textStyles.body1,
        color = MiuixTheme.colorScheme.onBackground
    )
    Spacer(modifier = Modifier.height(8.dp))

    // 昵称
    Text(
        text = "昵称",
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
    )
    Spacer(modifier = Modifier.height(4.dp))
    TextField(
        value = displayName,
        onValueChange = onDisplayNameChange,
        label = "平台昵称（选填）",
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))

    // ID / 账号
    val idLabel = fieldDef?.inputHint?.let { hint ->
        if (hint.contains("或")) hint.substringBefore("或").trim() else hint
    } ?: "账号/ID"
    Text(
        text = idLabel,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
    )
    Spacer(modifier = Modifier.height(4.dp))
    TextField(
        value = mainInput,
        onValueChange = {
            onMainInputChange(it)
            // 编辑模式下同步更新 jumpLink
            if (fieldDef != null && !it.startsWith("http")) {
                val link = buildPlatformLink(fieldKey, it.trim())
                onResolvedJumpLinkChange(link)
            }
        },
        label = idLabel,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))

    // 主页链接
    Text(
        text = "主页链接",
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
    )
    Spacer(modifier = Modifier.height(4.dp))
    TextField(
        value = resolvedJumpLink,
        onValueChange = { onResolvedJumpLinkChange(it) },
        label = "主页链接（可修改）",
        modifier = Modifier.fillMaxWidth()
    )

    // LINK_ONLY 平台辅助字段
    if (fieldDef?.linkSource == LinkSource.LINK_ONLY && auxiliaryInput.isNotBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${fieldDef.displayName}号（仅供App内搜索）",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Spacer(modifier = Modifier.height(4.dp))
        TextField(
            value = auxiliaryInput,
            onValueChange = onAuxiliaryInputChange,
            label = "仅供App内手动搜索",
            modifier = Modifier.fillMaxWidth()
        )
    }

    // 微信特殊提示
    if (fieldKey == "wechat" && mainInput.isNotBlank()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "微信号无法自动跳转，对方需要手动搜索添加",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }

    // 错误提示
    errorMessage?.let { msg ->
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = msg, style = MiuixTheme.textStyles.body2, color = Color(0xFFFF6B6B))
    }
    infoMessage?.let { msg ->
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = msg, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.primary)
    }

    Spacer(modifier = Modifier.height(16.dp))
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
                // 编辑模式下不允许保存空内容（否则会导致删除该平台）
                if (mainInput.isBlank() && resolvedJumpLink.isBlank()) {
                    onErrorMessageChange("请输入账号或链接，如需删除请使用删除功能")
                    return@Button
                }
                onSave()
            },
            modifier = Modifier.weight(1f),
            enabled = !isSaving,
            colors = ButtonDefaults.buttonColorsPrimary()
        ) {
            Text(text = "保存")
        }
    }
}

/**
 * 预设平台表单（按 LinkSource 分型）
 */
@Composable
private fun PlatformForm(
    fieldDef: PlatformFieldDef?,
    mainInput: String,
    auxiliaryInput: String,
    displayName: String,
    errorMessage: String?,
    infoMessage: String?,
    onMainInputChange: (String) -> Unit,
    onAuxiliaryInputChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    scope: CoroutineScope,
    fieldKey: String,
    onResolvedJumpLink: (String) -> Unit,
    onResolvedOriginalLink: (String) -> Unit,
    onResolvedValue: (String?) -> Unit,
    onInfoMessage: (String?) -> Unit,
) {
    if (fieldDef == null) return

    val linkSource = fieldDef.linkSource

    // 主输入框提示
    val mainLabel = fieldDef.inputHint

    // 主输入框
    Text(
        text = fieldDef.displayName,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
    )
    Spacer(modifier = Modifier.height(4.dp))
    TextField(
        value = mainInput,
        onValueChange = { input ->
            onMainInputChange(input)
            onInfoMessage(null)

            val isUrl = input.startsWith("http://") || input.startsWith("https://")

            when (linkSource) {
                LinkSource.AUTO -> {
                    if (!isUrl && input.isNotBlank()) {
                        // 填账号 → 自动生成链接
                        val link = buildPlatformLink(fieldKey, input.trim())
                        onResolvedJumpLink(link)
                        onResolvedOriginalLink("")
                        onResolvedValue(input.trim())
                    } else if (isUrl) {
                        // 粘贴链接 → 异步解析
                        scope.launch(Dispatchers.IO) {
                            val result = LinkResolver.resolve(fieldKey, input.trim())
                            withContext(Dispatchers.Main) {
                                onResolvedJumpLink(result.jumpLink)
                                onResolvedOriginalLink(result.originalLink)
                                onResolvedValue(result.value)
                                if (result.displayName != null) onDisplayNameChange(result.displayName)
                                if (result.errorMessage != null) onInfoMessage(result.errorMessage)
                            }
                        }
                    } else {
                        onResolvedJumpLink("")
                        onResolvedOriginalLink("")
                        onResolvedValue(null)
                    }
                }
                LinkSource.LINK_ONLY -> {
                    if (isUrl) {
                        // 粘贴链接 → 异步解析
                        scope.launch(Dispatchers.IO) {
                            val result = LinkResolver.resolve(fieldKey, input.trim())
                            withContext(Dispatchers.Main) {
                                onResolvedJumpLink(result.jumpLink)
                                onResolvedOriginalLink(result.originalLink)
                                onResolvedValue(result.value)
                                if (result.displayName != null) onDisplayNameChange(result.displayName)
                                if (result.errorMessage != null) onInfoMessage(result.errorMessage)
                            }
                        }
                    } else {
                        // 非 URL 输入（抖音号/小红书号） → 不生成链接
                        onResolvedJumpLink("")
                        onResolvedOriginalLink("")
                        onResolvedValue(input.trim())
                    }
                }
                LinkSource.NO_LINK -> {
                    // 微信：存 ID，不生成链接
                    onResolvedJumpLink("")
                    onResolvedOriginalLink("")
                    onResolvedValue(input.trim())
                }
            }
        },
        label = mainLabel,
        modifier = Modifier.fillMaxWidth()
    )

    // LINK_ONLY 提示
    if (linkSource == LinkSource.LINK_ONLY) {
        Spacer(modifier = Modifier.height(4.dp))
        if (!mainInput.startsWith("http") && mainInput.isNotBlank()) {
            Text(
                text = "${fieldDef.displayName}号仅供App内搜索，请粘贴主页链接生成跳转二维码",
                style = MiuixTheme.textStyles.body2,
                color = Color(0xFFFF9800)
            )
        } else if (mainInput.startsWith("http")) {
            Text(
                text = "请在${fieldDef.displayName}App中复制主页链接后粘贴",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }

    // 微信特殊提示
    if (fieldKey == "wechat" && mainInput.isNotBlank()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "微信号/手机号无法生成跳转链接，他人需复制后手动搜索添加",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }

    // AUTO 模式：显示自动生成的链接
    if (linkSource == LinkSource.AUTO && mainInput.isNotBlank() && !mainInput.startsWith("http")) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "主页链接（自动生成，可修改）",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Spacer(modifier = Modifier.height(4.dp))
        val autoLink = buildPlatformLink(fieldKey, mainInput.trim())
        Text(
            text = autoLink,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceSecondary
        )
    }

    // LINK_ONLY 平台辅助字段（抖音号/小红书号）
    if (linkSource == LinkSource.LINK_ONLY) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${fieldDef.displayName}号（仅供App内手动搜索，可选）",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Spacer(modifier = Modifier.height(4.dp))
        TextField(
            value = auxiliaryInput,
            onValueChange = onAuxiliaryInputChange,
            label = "对方可在${fieldDef.displayName}App搜索此号找到你",
            modifier = Modifier.fillMaxWidth()
        )
    }

    // 解析提示
    infoMessage?.let { msg ->
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = msg, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.primary)
    }

    // 错误提示
    errorMessage?.let { msg ->
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = msg, style = MiuixTheme.textStyles.body2, color = Color(0xFFFF6B6B))
    }
}

/**
 * 自定义平台表单
 */
@Composable
private fun CustomPlatformForm(
    customPlatformName: String,
    onCustomPlatformNameChange: (String) -> Unit,
    mainInput: String,
    onMainInputChange: (String) -> Unit,
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    errorMessage: String?,
) {
    Text(
        text = "平台名称",
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
    )
    Spacer(modifier = Modifier.height(4.dp))
    TextField(
        value = customPlatformName,
        onValueChange = onCustomPlatformNameChange,
        label = "如：Discord、Instagram",
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "账号或链接",
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
    )
    Spacer(modifier = Modifier.height(4.dp))
    TextField(
        value = mainInput,
        onValueChange = onMainInputChange,
        label = "平台账号或主页链接",
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "昵称（选填）",
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
    )
    Spacer(modifier = Modifier.height(4.dp))
    TextField(
        value = displayName,
        onValueChange = onDisplayNameChange,
        label = "平台昵称",
        modifier = Modifier.fillMaxWidth()
    )

    errorMessage?.let { msg ->
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = msg, style = MiuixTheme.textStyles.body2, color = Color(0xFFFF6B6B))
    }
}
