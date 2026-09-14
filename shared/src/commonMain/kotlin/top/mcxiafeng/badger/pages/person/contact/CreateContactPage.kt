package top.mcxiafeng.badger.pages.person.contact

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.di.KoinComponentBy
import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.network.IdentifyResponse
import top.mcxiafeng.badger.network.PlatformManifestRepository
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
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
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.mcxiafeng.badger.platform.downloadImage
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import top.mcxiafeng.badger.platform.downloadImageAsPng

private const val TAG = "CreateContactPage"

private enum class CreateMode { MANUAL, AUTO_FETCH }

@Composable
fun CreateContactPage(
    targetCollectionId: Long? = null,
    onBack: () -> Unit = {},
    onNavigateToContactDetail: (Long) -> Unit = {},
    viewModel: CreateContactViewModel = koinViewModel()
) {
    val scope = rememberCoroutineScope()

    
    var mode by remember { mutableStateOf(CreateMode.MANUAL) }

    
    var contactName by remember { mutableStateOf("") }

    
    val manifestRepo = remember { KoinComponentBy.get<PlatformManifestRepository>() }
    val addableDefs by manifestRepo.addable.collectAsState()
    LaunchedEffect(Unit) { manifestRepo.ensureLoaded() }

    var isGridPhase by remember { mutableStateOf(true) }
    var selectedFieldKey by remember { mutableStateOf("") }
    var mainInput by remember { mutableStateOf("") }
    var isResolving by remember { mutableStateOf(false) }
    var resolveError by remember { mutableStateOf<String?>(null) }
    var resolved by remember { mutableStateOf<IdentifyResponse?>(null) }
    var previewImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var editableName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }

    val selectedDef = remember(selectedFieldKey, addableDefs) {
        addableDefs.firstOrNull { it.fieldKey == selectedFieldKey }
            ?: FIELD_DEF_MAP[selectedFieldKey]
    }

    
    LaunchedEffect(resolved?.avatarUrl) {
        val url = resolved?.avatarUrl?.takeIf { it.isNotBlank() }
        previewImageBitmap = if (url != null) {
            downloadImageAsPng(url)?.let { bytes -> runCatching { bytes.decodeToImageBitmap() }.getOrNull() }
        } else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "新建联系人",
                navigationIcon = {
                    IconButton(onClick = {
                        if (mode == CreateMode.AUTO_FETCH && !isGridPhase && resolved == null) {
                            
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

            
            ModeTabRow(
                mode = mode,
                onModeChange = {
                    mode = it
                    
                    if (it == CreateMode.MANUAL) {
                        isGridPhase = true
                        selectedFieldKey = ""
                        mainInput = ""
                        resolved = null
                        resolveError = null
                                                previewImageBitmap = null
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
                        scope.launch(BadgerDispatchers.io) {
                            val id = viewModel.createMinimalContact(name, targetCollectionId)
                            BadgerLog.d(TAG, "手动创建成功: id=$id, name=$name")
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
                    previewImageBitmap = previewImageBitmap,
                    editableName = editableName,
                    isCreating = isCreating,
                    onGridSelect = { fieldKey ->
                        selectedFieldKey = fieldKey
                        isGridPhase = false
                        mainInput = ""
                        resolved = null
                        resolveError = null
                                                previewImageBitmap = null
                        editableName = ""
                    },
                    onCustomSelect = {
                        selectedFieldKey = "website"
                        isGridPhase = false
                        mainInput = ""
                        resolved = null
                        resolveError = null
                                                previewImageBitmap = null
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
                        scope.launch(BadgerDispatchers.io) {
                            val resp = KoinComponentBy.get<ContactNetworkResolver>().identify(input)
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
                        scope.launch(BadgerDispatchers.io) {
                            val id = viewModel.createContactFromResolve(
                                name = finalName,
                                bio = resolvedData.signature?.takeIf { it.isNotBlank() },
                                avatarUrl = resolvedData.avatarUrl?.takeIf { it.isNotBlank() },
                                platformKey = selectedFieldKey.takeIf { it.isNotBlank() },
                                platformValue = mainInput.trim().takeIf { it.isNotBlank() },
                                collectionId = targetCollectionId,

                            )
                            BadgerLog.d(TAG, "自动获取创建成功: id=$id, name=$finalName")
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
                                                previewImageBitmap = null
                        editableName = ""
                    }
                )
            }
        }
    }
}

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

