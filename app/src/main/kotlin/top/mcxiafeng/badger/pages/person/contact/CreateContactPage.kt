package top.mcxiafeng.badger.pages.person.contact

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.di.KoinComponentBy
import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.network.IdentifyResponse
import top.mcxiafeng.badger.network.PlatformManifestRepository
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.utils.HttpUtil
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.mcxiafeng.badger.pages.person.contact.dialogs.PlatformGridSelector
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft

private const val TAG = "CreateContactPage"

/**
 * [B5] 创建联系人页面模式。
 *
 * - [MANUAL] 手动输入姓名创建（原有逻辑）
 * - [AUTO_FETCH] 选择平台 → 粘贴链接/ID → 解析 → 预览 → 创建
 */
private enum class CreateMode { MANUAL, AUTO_FETCH }

@Composable
fun CreateContactPage(
    targetCollectionId: Long? = null,
    onBack: () -> Unit = {},
    onNavigateToContactDetail: (Long) -> Unit = {},
    viewModel: CreateContactViewModel = koinViewModel()
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 模式切换
    var mode by remember { mutableStateOf(CreateMode.MANUAL) }

    // 手动模式状态
    var contactName by remember { mutableStateOf("") }

    // 自动获取模式状态
    val manifestRepo = remember { KoinComponentBy.get<PlatformManifestRepository>() }
    val addableDefs by manifestRepo.addable.collectAsState()
    LaunchedEffect(Unit) { manifestRepo.ensureLoaded() }

    var isGridPhase by remember { mutableStateOf(true) }
    var selectedFieldKey by remember { mutableStateOf("") }
    var mainInput by remember { mutableStateOf("") }
    var isResolving by remember { mutableStateOf(false) }
    var resolveError by remember { mutableStateOf<String?>(null) }
    var resolved by remember { mutableStateOf<IdentifyResponse?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var editableName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }

    val selectedDef = remember(selectedFieldKey, addableDefs) {
        addableDefs.firstOrNull { it.fieldKey == selectedFieldKey }
            ?: FIELD_DEF_MAP[selectedFieldKey]
    }

    // 解析成功后惰性下载头像用于预览
    LaunchedEffect(resolved?.avatarUrl) {
        val url = resolved?.avatarUrl?.takeIf { it.isNotBlank() }
        val old = previewBitmap
        previewBitmap = if (url != null) {
            withContext(Dispatchers.IO) { HttpUtil.downloadBitmap(url) }
        } else null
        if (old != null && old !== previewBitmap) old.recycle()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "新建联系人",
                navigationIcon = {
                    IconButton(onClick = {
                        if (mode == CreateMode.AUTO_FETCH && !isGridPhase && resolved == null) {
                            // 返回平台网格
                            isGridPhase = true
                            selectedFieldKey = ""
                            mainInput = ""
                            resolveError = null
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            imageVector = Lucide.ArrowLeft,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 模式切换 Tab
            ModeTabRow(
                mode = mode,
                onModeChange = {
                    mode = it
                    // 切换模式时重置各模式状态
                    if (it == CreateMode.MANUAL) {
                        isGridPhase = true
                        selectedFieldKey = ""
                        mainInput = ""
                        resolved = null
                        resolveError = null
                        previewBitmap?.recycle()
                        previewBitmap = null
                        editableName = ""
                    } else {
                        contactName = ""
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            when (mode) {
                CreateMode.MANUAL -> ManualModeContent(
                    contactName = contactName,
                    onNameChange = { contactName = it },
                    isCreating = isCreating,
                    onCreate = {
                        val name = contactName.trim()
                        if (name.isBlank()) return@ManualModeContent
                        isCreating = true
                        scope.launch(Dispatchers.IO) {
                            val id = viewModel.createMinimalContact(name, targetCollectionId)
                            Log.d(TAG, "手动创建成功: id=$id, name=$name")
                            withContext(Dispatchers.Main) {
                                isCreating = false
                                onNavigateToContactDetail(id)
                            }
                        }
                    }
                )

                CreateMode.AUTO_FETCH -> AutoFetchModeContent(
                    isGridPhase = isGridPhase,
                    selectedFieldKey = selectedFieldKey,
                    selectedDef = selectedDef,
                    addableDefs = addableDefs,
                    mainInput = mainInput,
                    isResolving = isResolving,
                    resolveError = resolveError,
                    resolved = resolved,
                    previewBitmap = previewBitmap,
                    editableName = editableName,
                    isCreating = isCreating,
                    onGridSelect = { fieldKey ->
                        selectedFieldKey = fieldKey
                        isGridPhase = false
                        mainInput = ""
                        resolved = null
                        resolveError = null
                        previewBitmap?.recycle()
                        previewBitmap = null
                        editableName = ""
                    },
                    onCustomSelect = {
                        selectedFieldKey = "website"
                        isGridPhase = false
                        mainInput = ""
                        resolved = null
                        resolveError = null
                        previewBitmap?.recycle()
                        previewBitmap = null
                        editableName = ""
                    },
                    onInputChange = { mainInput = it; resolveError = null },
                    onNameChange = { editableName = it },
                    onResolve = {
                        val input = mainInput.trim()
                        if (input.isBlank()) {
                            resolveError = "请输入链接或 ID"
                            return@AutoFetchModeContent
                        }
                        isResolving = true
                        resolveError = null
                        scope.launch(Dispatchers.IO) {
                            val resp = ContactNetworkResolver.identify(input)
                            withContext(Dispatchers.Main) {
                                isResolving = false
                                if (resp == null) {
                                    resolveError = "解析失败，请检查链接或网络"
                                } else {
                                    resolved = resp
                                    editableName = resp.name?.takeIf { it.isNotBlank() && it != "未知" } ?: ""
                                }
                            }
                        }
                    },
                    onCreate = {
                        val resolvedData = resolved ?: return@AutoFetchModeContent
                        val finalName = editableName.trim()
                        if (finalName.isBlank()) return@AutoFetchModeContent
                        isCreating = true
                        scope.launch(Dispatchers.IO) {
                            val id = viewModel.createContactFromResolve(
                                name = finalName,
                                bio = resolvedData.signature?.takeIf { it.isNotBlank() },
                                avatarUrl = resolvedData.avatarUrl?.takeIf { it.isNotBlank() },
                                platformKey = selectedFieldKey.takeIf { it.isNotBlank() },
                                platformValue = mainInput.trim().takeIf { it.isNotBlank() },
                                collectionId = targetCollectionId,
                                context = context,
                            )
                            Log.d(TAG, "自动获取创建成功: id=$id, name=$finalName")
                            withContext(Dispatchers.Main) {
                                isCreating = false
                                onNavigateToContactDetail(id)
                            }
                        }
                    },
                    onBackToGrid = {
                        isGridPhase = true
                        selectedFieldKey = ""
                        mainInput = ""
                        resolved = null
                        resolveError = null
                        previewBitmap?.recycle()
                        previewBitmap = null
                        editableName = ""
                    }
                )
            }
        }
    }
}

/**
 * 模式切换 Tab：手动输入 / 自动获取
 */
@Composable
private fun ModeTabRow(
    mode: CreateMode,
    onModeChange: (CreateMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val manualColor = if (mode == CreateMode.MANUAL) MiuixTheme.colorScheme.primary
        else MiuixTheme.colorScheme.onBackgroundVariant
        val autoColor = if (mode == CreateMode.AUTO_FETCH) MiuixTheme.colorScheme.primary
        else MiuixTheme.colorScheme.onBackgroundVariant

        Text(
            text = "手动输入",
            style = MiuixTheme.textStyles.title3,
            color = manualColor,
            modifier = Modifier.clickable { onModeChange(CreateMode.MANUAL) }
        )
        Spacer(modifier = Modifier.width(24.dp))
        Text(text = "|", style = MiuixTheme.textStyles.title3, color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.width(24.dp))
        Text(
            text = "自动获取",
            style = MiuixTheme.textStyles.title3,
            color = autoColor,
            modifier = Modifier.clickable { onModeChange(CreateMode.AUTO_FETCH) }
        )
    }
}

// ========== 手动模式 ==========

@Composable
private fun ManualModeContent(
    contactName: String,
    onNameChange: (String) -> Unit,
    isCreating: Boolean,
    onCreate: () -> Unit,
) {
    Text(
        text = "输入联系人姓名",
        style = MiuixTheme.textStyles.title3,
        color = MiuixTheme.colorScheme.onBackground
    )
    Spacer(modifier = Modifier.height(16.dp))
    TextField(
        value = contactName,
        onValueChange = onNameChange,
        label = "姓名",
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(24.dp))
    Button(
        onClick = onCreate,
        modifier = Modifier.fillMaxWidth(),
        enabled = contactName.trim().isNotBlank() && !isCreating,
        colors = ButtonDefaults.buttonColorsPrimary()
    ) {
        if (isCreating) {
            CircularProgressIndicator(
                size = 18.dp,
                strokeWidth = 2.dp,
                colors = ProgressIndicatorDefaults.progressIndicatorColors(
                    foregroundColor = MiuixTheme.colorScheme.onPrimary,
                    backgroundColor = MiuixTheme.colorScheme.onPrimary.copy(alpha = 0.3f)
                )
            )
        } else {
            Text(text = "创建")
        }
    }
}

// ========== 自动获取模式 ==========

@Composable
private fun AutoFetchModeContent(
    isGridPhase: Boolean,
    selectedFieldKey: String,
    selectedDef: top.mcxiafeng.badger.ocr.PlatformFieldDef?,
    addableDefs: List<top.mcxiafeng.badger.ocr.PlatformFieldDef>,
    mainInput: String,
    isResolving: Boolean,
    resolveError: String?,
    resolved: IdentifyResponse?,
    previewBitmap: Bitmap?,
    editableName: String,
    isCreating: Boolean,
    onGridSelect: (String) -> Unit,
    onCustomSelect: () -> Unit,
    onInputChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onResolve: () -> Unit,
    onCreate: () -> Unit,
    onBackToGrid: () -> Unit,
) {
    if (isGridPhase) {
        // Phase 1: 平台网格选择
        Text(
            text = "选择平台后粘贴链接或 ID",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        PlatformGridSelector(
            defs = addableDefs,
            existingPlatformKeys = emptySet(),
            onSelect = onGridSelect,
            onCustom = onCustomSelect,
        )
    } else {
        // Phase 2: 输入/解析/预览
        // 返回按钮行
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            IconButton(onClick = onBackToGrid) {
                Icon(
                    imageVector = Lucide.ArrowLeft,
                    contentDescription = "返回平台选择",
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
            Text(
                text = selectedDef?.displayName ?: "自定义",
                style = MiuixTheme.textStyles.title3,
            )
        }

        TextField(
            value = mainInput,
            onValueChange = onInputChange,
            label = "链接或 ID",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (resolveError != null) {
            Text(
                text = resolveError,
                color = MiuixTheme.colorScheme.error,
                style = MiuixTheme.textStyles.footnote2,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        if (resolved == null) {
            // 解析按钮
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = onBackToGrid,
                    modifier = Modifier.weight(1f),
                    enabled = !isResolving
                )
                Button(
                    onClick = onResolve,
                    modifier = Modifier.weight(1f),
                    enabled = !isResolving && mainInput.trim().isNotBlank(),
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    if (isResolving) {
                        CircularProgressIndicator(
                            size = 18.dp,
                            strokeWidth = 2.dp,
                            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                foregroundColor = MiuixTheme.colorScheme.onPrimary,
                                backgroundColor = MiuixTheme.colorScheme.onPrimary.copy(alpha = 0.3f)
                            )
                        )
                    } else {
                        Text(text = "解析")
                    }
                }
            }
        } else {
            // 预览解析结果 + 可编辑姓名
            Spacer(modifier = Modifier.height(12.dp))
            ResolvePreviewRow(
                bio = resolved.signature?.takeIf { it.isNotBlank() },
                avatarBitmap = previewBitmap,
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextField(
                value = editableName,
                onValueChange = onNameChange,
                label = "姓名",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = onBackToGrid,
                    modifier = Modifier.weight(1f),
                    enabled = !isCreating
                )
                Button(
                    onClick = onCreate,
                    modifier = Modifier.weight(1f),
                    enabled = editableName.trim().isNotBlank() && !isCreating,
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(
                            size = 18.dp,
                            strokeWidth = 2.dp,
                            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                foregroundColor = MiuixTheme.colorScheme.onPrimary,
                                backgroundColor = MiuixTheme.colorScheme.onPrimary.copy(alpha = 0.3f)
                            )
                        )
                    } else {
                        Text(text = "创建")
                    }
                }
            }
        }
    }
}

/**
 * 解析结果预览：头像 + 简介（姓名可编辑，不在此处展示）。
 */
@Composable
private fun ResolvePreviewRow(
    bio: String?,
    avatarBitmap: Bitmap?,
) {
    val hasContent = !bio.isNullOrBlank() || avatarBitmap != null
    if (!hasContent) {
        Text(
            text = "未解析到可预览的信息（简介 / 头像）",
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            style = MiuixTheme.textStyles.footnote2,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (avatarBitmap != null) {
            Image(
                bitmap = avatarBitmap.asImageBitmap(),
                contentDescription = "头像预览",
                modifier = Modifier.size(56.dp).clip(CircleShape)
            )
            Spacer(modifier = Modifier.size(12.dp))
        }
        if (!bio.isNullOrBlank()) {
            Text(
                text = bio,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
