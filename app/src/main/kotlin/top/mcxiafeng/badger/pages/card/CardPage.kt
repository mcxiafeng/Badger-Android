package top.mcxiafeng.badger.pages.card

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.ui.components.FirstTimeHint
import top.mcxiafeng.badger.data.CollectionWithCount
import top.mcxiafeng.badger.data.ContactRepository
import top.mcxiafeng.badger.pages.person.contact.ToolbarAction
import top.mcxiafeng.badger.data.exportToJson
import top.mcxiafeng.badger.data.analyzeImportConflicts
import top.mcxiafeng.badger.data.ImportConflict
import top.mcxiafeng.badger.data.CollectionConflictAction
import top.mcxiafeng.badger.utils.Methods
import top.mcxiafeng.badger.pages.card.CardViewModel
import top.mcxiafeng.badger.pages.card.CardUiState
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.io.File

private const val TAG = "Tester"

/**
 * 名片夹页面
 *
 * 展示所有名片夹（每行2个），显示名称、描述和联系人数量。
 * 点击名片夹进入联系人列表，支持创建和删除名片夹。
 *
 */
@Composable
fun CardRoute(
    onScanToCollection: ((Long) -> Unit)? = null,
    onContactClick: ((Long) -> Unit)? = null,
    onNavigateToCollectionDetail: (Long) -> Unit = {}
) {
    val viewModel: CardViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CardScreen(
        uiState = uiState,
        repository = viewModel.repository,
        onNavigateToCollectionDetail = onNavigateToCollectionDetail,
        onCreateCollection = viewModel::createCollection,
        onUpdateCollection = viewModel::updateCollection,
        onDeleteCollection = viewModel::deleteCollection
    )
}

@Composable
fun CardScreen(
    uiState: CardUiState,
    repository: ContactRepository,
    onNavigateToCollectionDetail: (Long) -> Unit = {},
    onCreateCollection: (String, String?, String?, Long?) -> Unit = { _, _, _, _ -> },
    onUpdateCollection: suspend (top.mcxiafeng.badger.data.CardCollection) -> Unit = {},
    onDeleteCollection: (CollectionWithCount) -> Unit = {}
) {
    val context = LocalContext.current
    // repository passed from Route
    val successState = (uiState as? CardUiState.Success)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 创建名片夹 Dialog 状态
    var showCreateDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var actualExportIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var showImportDialog by remember { mutableStateOf(false) }
    // 导入冲突状态
    var importConflicts by remember { mutableStateOf<List<ImportConflict>?>(null) }
    var importCollectionActions by remember { mutableStateOf<Map<String, CollectionConflictAction>>(emptyMap()) }
    var importRenameNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showImportRenameField by remember { mutableStateOf(false) }
    var importRenameInput by remember { mutableStateOf("") }
    var showContactConflictDialog by remember { mutableStateOf(false) }
    val mergeChecked = remember { mutableStateMapOf<String, Boolean>() }
    val newStyleChecked = remember { mutableStateMapOf<String, Boolean>() }
    val forceImportChecked = remember { mutableStateMapOf<String, Boolean>() }
    val importChecked = remember { mutableStateMapOf<String, Boolean>() }
    // 多选名片夹
    var isInSelectionMode by remember { mutableStateOf(false) }
    var selectedCollectionIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showCollectionDeleteDialog by remember { mutableStateOf(false) }
    var showEditCollectionDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = isInSelectionMode) {
        Log.d(TAG, "BackHandler: exit selection mode")
        isInSelectionMode = false
        selectedCollectionIds = emptySet()
    }

    // 文件选择器
    val exportFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val ids = actualExportIds
                    val json = exportToJson(repository, ids)
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    withContext(Dispatchers.Main) {
                        Log.d(TAG, "exportToFile: success, ids=${ids.size}")
                        Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "exportToFile: failed", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    val importFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: throw IllegalArgumentException("无法读取文件")
                    val conflicts = analyzeImportConflicts(repository, json)
                    withContext(Dispatchers.Main) {
                        importConflicts = conflicts
                        importCollectionActions = emptyMap()
                        importRenameNames = emptyMap()
                        showContactConflictDialog = false
                        Log.d(TAG, "importFromFile: ${conflicts.size} collections, ${conflicts.sumOf { it.contactConflicts.size }} contacts")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "importFromFile: failed", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val allCollectionIds = (successState?.collections ?: emptyList()).map { it.collection.id }.toSet()
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    Scaffold(
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        topBar = {
            if (isInSelectionMode) {
                val isAllSelected = selectedCollectionIds == allCollectionIds && allCollectionIds.isNotEmpty()
                TopAppBar(
                    title = "已选择 ${selectedCollectionIds.size} 项",
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = {
                            isInSelectionMode = false
                            selectedCollectionIds = emptySet()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "取消")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            selectedCollectionIds = if (isAllSelected) emptySet() else allCollectionIds
                            Log.d(TAG, "toggleSelectAll: isAllSelected=$isAllSelected")
                        }) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = if (isAllSelected) "取消全选" else "全选",
                                tint = if (isAllSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "更多")
                            }
                            OverlayListPopup(
                                show = showOverflowMenu,
                                alignment = PopupPositionProvider.Align.TopEnd,
                                popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                ListPopupColumn {
                                    DropdownImpl(
                                        text = "导出名片夹",
                                        optionSize = 2,
                                        isSelected = false,
                                        index = 0,
                                        onSelectedIndexChange = {
                                            showOverflowMenu = false
                                            val ids = selectedCollectionIds.ifEmpty {
                                                (successState?.collections ?: emptyList()).map { it.collection.id }
                                            }.toList()
                                            actualExportIds = ids
                                            Log.d(TAG, "exportCollections: ids=${ids.size}")
                                            exportFileLauncher.launch("badger_export_${System.currentTimeMillis()}.json")
                                        }
                                    )
                                    DropdownImpl(
                                        text = "导入名片夹",
                                        optionSize = 2,
                                        isSelected = false,
                                        index = 1,
                                        onSelectedIndexChange = {
                                            showOverflowMenu = false
                                            showImportDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = "名片夹",
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {},
                    actions = {
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "更多")
                            }
                            OverlayListPopup(
                                show = showOverflowMenu,
                                alignment = PopupPositionProvider.Align.TopEnd,
                                popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                ListPopupColumn {
                                    DropdownImpl(
                                        text = "导出名片夹",
                                        optionSize = 2,
                                        isSelected = false,
                                        index = 0,
                                        onSelectedIndexChange = {
                                            showOverflowMenu = false
                                            val ids = selectedCollectionIds.ifEmpty {
                                                (successState?.collections ?: emptyList()).map { it.collection.id }
                                            }.toList()
                                            actualExportIds = ids
                                            Log.d(TAG, "exportCollections: ids=${ids.size}")
                                            exportFileLauncher.launch("badger_export_${System.currentTimeMillis()}.json")
                                        }
                                    )
                                    DropdownImpl(
                                        text = "导入名片夹",
                                        optionSize = 2,
                                        isSelected = false,
                                        index = 1,
                                        onSelectedIndexChange = {
                                            showOverflowMenu = false
                                            showImportDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !isInSelectionMode,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.padding(bottom = floatingBarBottomPadding)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "添加",
                        tint = MiuixTheme.colorScheme.onPrimary
                    )
                }
            }
        },
        floatingToolbar = {
            AnimatedVisibility(
                visible = isInSelectionMode,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                Box(modifier = Modifier.padding(bottom = LocalFloatingBarBottomPadding.current)) {
                    FloatingToolbar(cornerRadius = 16.dp) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            ToolbarAction(
                                icon = Icons.Default.Edit,
                                label = "编辑",
                                onClick = {
                                    if (selectedCollectionIds.size == 1) {
                                        showEditCollectionDialog = true
                                    }
                                }
                            )
                            ToolbarAction(
                                icon = Icons.Default.Delete,
                                label = "删除",
                                tint = MiuixTheme.colorScheme.error,
                                onClick = {
                                    showCollectionDeleteDialog = true
                                }
                            )
                            ToolbarAction(
                                icon = Icons.Default.Share,
                                label = "分享",
                                onClick = {
                                    val ids = selectedCollectionIds.toList()
                                    isInSelectionMode = false
                                    selectedCollectionIds = emptySet()
                                    scope.launch {
                                        try {
                                            val json = exportToJson(repository, ids)
                                            val fileName = "badger_share_${System.currentTimeMillis()}.json"
                                            val sharedDir = File(context.cacheDir, "shared").apply { mkdirs() }
                                            val tempFile = File(sharedDir, fileName).also { it.writeText(json) }
                                            Log.d(TAG, "shareCollections: ids=${ids.size}, tempFile=${tempFile.absolutePath}")
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                tempFile
                                            )
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/json"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "分享名片夹"))
                                            withContext(Dispatchers.IO) {
                                                delay(5000)
                                                tempFile.delete()
                                                Log.d(TAG, "shareCollections: tempFile deleted")
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "shareCollections: failed", e)
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        floatingToolbarPosition = ToolbarPosition.BottomCenter
    ) { paddingValues ->
        Crossfade(
            targetState = when {
                uiState is CardUiState.Loading -> "loading"
                uiState is CardUiState.Error -> "error"
                (successState?.collections ?: emptyList()).isEmpty() -> "empty"
                else -> "content"
            },
            label = "cardPageState"
        ) { state ->
            when (state) {
                "loading" -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("加载中...", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body1)
                    }
                }
                "error" -> {
                    val msg = (uiState as? CardUiState.Error)?.message ?: ""
                    Box(
                        modifier = Modifier.fillMaxSize().padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("加载失败: $msg", color = MiuixTheme.colorScheme.error, style = MiuixTheme.textStyles.body1)
                    }
                }
                "empty" -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        val annotatedText = buildAnnotatedString {
                            withStyle(SpanStyle(color = MiuixTheme.colorScheme.onSurfaceVariantSummary)) {
                                append("还没有名片夹，")
                            }
                            pushStringAnnotation("add", "click")
                            withStyle(SpanStyle(
                                color = MiuixTheme.colorScheme.primary,
                                fontWeight = MiuixTheme.textStyles.subtitle.fontWeight
                            )) {
                                append("点击添加")
                            }
                            pop()
                        }
                        ClickableText(
                            text = annotatedText,
                            style = MiuixTheme.textStyles.body1,
                            onClick = { offset ->
                                annotatedText.getStringAnnotations("add", offset, offset).firstOrNull()?.let {
                                    showCreateDialog = true
                                }
                            }
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            top = paddingValues.calculateTopPadding() + 12.dp,
                            bottom = paddingValues.calculateBottomPadding() + 12.dp + LocalFloatingBarBottomPadding.current
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                    ) {
                        item(key = "long_press_hint") {
                            FirstTimeHint(
                                text = "长按名片夹可多选、编辑或删除",
                                hintKey = "long_press_card",
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        items(
                            (successState?.collections ?: emptyList<CollectionWithCount>()).chunked(2),
                            key = { row -> row.joinToString(",") { it.collection.id.toString() } },
                            contentType = { _ -> "collection_row" }
                        ) { rowItems ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { item ->
                                    val isSelected = isInSelectionMode && item.collection.id in selectedCollectionIds
                                    CollectionCard(
                                        item = item,
                                        selected = isSelected,
                                        isInSelectionMode = isInSelectionMode,
                                        onClick = {
                                            if (isInSelectionMode) {
                                                selectedCollectionIds = if (item.collection.id in selectedCollectionIds) {
                                                    selectedCollectionIds - item.collection.id
                                                } else {
                                                    selectedCollectionIds + item.collection.id
                                                }
                                                if (selectedCollectionIds.isEmpty()) {
                                                    isInSelectionMode = false
                                                    Log.d(TAG, "exitSelectionMode: no selection")
                                                } else {
                                                    Log.d(TAG, "toggleSelection: selectedIds=${selectedCollectionIds.size}")
                                                }
                                            } else {
                                                onNavigateToCollectionDetail(item.collection.id)
                                            }
                                        },
                                        onLongClick = {
                                            if (!isInSelectionMode) {
                                                isInSelectionMode = true
                                                selectedCollectionIds = setOf(item.collection.id)
                                                Log.d(TAG, "enterSelectionMode: selected collection=${item.collection.name}")
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 名片夹删除确认对话框
    if (showCollectionDeleteDialog && selectedCollectionIds.isNotEmpty()) {
        val allCollections = successState?.collections ?: emptyList()
        val selectedItems = allCollections.filter { it.collection.id in selectedCollectionIds }
        val count = selectedCollectionIds.size
        WindowDialog(
            show = true,
            title = "删除名片夹",
            summary = if (count == 1 && selectedItems.isNotEmpty()) "确定删除「${selectedItems.first().collection.name}」吗？其中的联系人不会被删除。"
                       else "确定删除 $count 个名片夹吗？其中的联系人不会被删除。",
            onDismissRequest = {
                showCollectionDeleteDialog = false
                isInSelectionMode = false
                selectedCollectionIds = emptySet()
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    text = "取消",
                    onClick = {
                        showCollectionDeleteDialog = false
                        isInSelectionMode = false
                        selectedCollectionIds = emptySet()
                    },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(20.dp))
                TextButton(
                    text = "删除",
                    onClick = {
                        selectedItems.forEach { item ->
                            top.mcxiafeng.badger.utils.Methods.deleteFileIfExists(item.collection.backgroundImagePath)
                            Log.d(TAG, "deleteCollection: id=${item.collection.id}, bgPath=${item.collection.backgroundImagePath} cleaned")
                            scope.launch(Dispatchers.IO) { onDeleteCollection(item) }
                        }
                        showCollectionDeleteDialog = false
                        isInSelectionMode = false
                        selectedCollectionIds = emptySet()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }

    // 编辑名片夹对话框
    if (showEditCollectionDialog && selectedCollectionIds.size == 1) {
        val allCollections = successState?.collections ?: emptyList()
        val item = allCollections.find { it.collection.id in selectedCollectionIds }
        if (item != null) {
            EditCollectionDialog(
                collection = item.collection,
                onDismiss = {
                    showEditCollectionDialog = false
                    isInSelectionMode = false
                    selectedCollectionIds = emptySet()
                },
                onConfirm = { updatedCollection ->
                    scope.launch {
                        onUpdateCollection(updatedCollection)
                        snackbarHostState.showSnackbar("名片夹已更新", duration = SnackbarDuration.Custom(2000))
                    }
                    showEditCollectionDialog = false
                    isInSelectionMode = false
                    selectedCollectionIds = emptySet()
                }
            )
        }
    }

    // 创建名片夹对话框
    if (showCreateDialog) {
        CreateCollectionDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, desc, bgPath, dominantColor ->
                showCreateDialog = false
                onCreateCollection(name, desc, bgPath, dominantColor)
                scope.launch {
                    snackbarHostState.showSnackbar("名片夹已创建", duration = SnackbarDuration.Custom(2000))
                }
            }
        )
    }

    // 导入 → 直接触发选择文件
    if (showImportDialog) {
        showImportDialog = false
        importFileLauncher.launch("application/json")
    }

    // ===== 导入冲突：名片夹冲突对话框 =====
    val collectionConflicts = importConflicts?.filter { it.existingCollection != null } ?: emptyList()
    val currentCollectionConflictIndex = importCollectionActions.size
    val currentCollectionConflict = collectionConflicts.getOrNull(currentCollectionConflictIndex)
    if (currentCollectionConflict != null) {
        WindowDialog(
            show = true,
            title = "导入「${currentCollectionConflict.collectionExport.name}」",
            onDismissRequest = {
                importConflicts = null
                importCollectionActions = emptyMap()
                importRenameNames = emptyMap()
            }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("该名片夹已存在", modifier = Modifier.padding(bottom = 12.dp))
                if (showImportRenameField) {
                    TextField(
                        value = importRenameInput,
                        onValueChange = { importRenameInput = it },
                        label = "新名称",
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(text = "取消", onClick = { showImportRenameField = false }, modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(20.dp))
                        TextButton(text = "确认", onClick = {
                            val name = currentCollectionConflict.collectionExport.name
                            importCollectionActions = importCollectionActions + (name to CollectionConflictAction.RENAME)
                            importRenameNames = importRenameNames + (name to importRenameInput.ifBlank { "${name}_2" })
                            showImportRenameField = false
                        }, modifier = Modifier.weight(1f))
                    }
                } else {
                    TextButton(text = "合并到已有名片夹", onClick = {
                        importCollectionActions = importCollectionActions + (currentCollectionConflict.collectionExport.name to CollectionConflictAction.MERGE)
                    }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(text = "改名导入为新名片夹", onClick = {
                        importRenameInput = "${currentCollectionConflict.collectionExport.name}_2"
                        showImportRenameField = true
                    }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(text = "不要导入", onClick = {
                        importCollectionActions = importCollectionActions + (currentCollectionConflict.collectionExport.name to CollectionConflictAction.SKIP)
                    }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    } else if (importConflicts != null && currentCollectionConflictIndex >= collectionConflicts.size && !showContactConflictDialog) {
        // 名片夹冲突全部处理完，弹出联系人列表对话框
        val allContacts = importConflicts!!.flatMap { it.contactConflicts }
        mergeChecked.clear()
        newStyleChecked.clear()
        forceImportChecked.clear()
        importChecked.clear()
        allContacts.forEach { cc ->
            if (cc.existingContact != null) {
                mergeChecked[cc.contactExport.name] = true
                newStyleChecked[cc.contactExport.name] = false
                forceImportChecked[cc.contactExport.name] = false
            } else {
                importChecked[cc.contactExport.name] = true
            }
        }
        showContactConflictDialog = true
    }

    // ===== 导入：联系人列表对话框 =====
    if (showContactConflictDialog && importConflicts != null) {
        ImportConflictDialog(
            conflicts = importConflicts!!,
            repository = repository,
            scope = scope,
            mergeChecked = mergeChecked,
            newStyleChecked = newStyleChecked,
            forceImportChecked = forceImportChecked,
            importChecked = importChecked,
            collectionActions = importCollectionActions,
            renamedCollectionNames = importRenameNames,
            onDismiss = {
                showContactConflictDialog = false
                mergeChecked.clear()
                newStyleChecked.clear()
                forceImportChecked.clear()
                importChecked.clear()
                importConflicts = null
                importCollectionActions = emptyMap()
                importRenameNames = emptyMap()
            },
            onSuccess = { msg ->
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        )
    }
}
