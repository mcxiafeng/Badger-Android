package top.mcxiafeng.badger.pages.card

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
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
import top.mcxiafeng.badger.utils.exportToJson
import top.mcxiafeng.badger.utils.importFromClipboard
import top.mcxiafeng.badger.utils.importFromJson
import top.mcxiafeng.badger.utils.Methods
import top.mcxiafeng.badger.viewmodels.CardViewModel
import top.mcxiafeng.badger.viewmodels.CardUiState
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
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.io.File

private const val TAG = "CardPage"

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
        onDeleteCollection = viewModel::deleteCollection
    )
}

@Composable
fun CardScreen(
    uiState: CardUiState,
    repository: ContactRepository,
    onNavigateToCollectionDetail: (Long) -> Unit = {},
    onCreateCollection: (String, String?) -> Unit = { _, _ -> },
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
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    // 长按名片夹
    var showCollectionToolbar by remember { mutableStateOf(false) }
    var selectedCollectionItem by remember { mutableStateOf<CollectionWithCount?>(null) }
    var showCollectionDeleteDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = showCollectionToolbar) {
        showCollectionToolbar = false
        selectedCollectionItem = null
    }

    // 文件选择器
    val exportFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val ids = (successState?.collections ?: emptyList()).map { it.collection.id }
                    val json = exportToJson(repository, ids)
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    withContext(Dispatchers.Main) {
                        android.util.Log.d(TAG, "exportToFile: success, ids=${ids.size}")
                        Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
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
                    val result = importFromJson(repository, json)
                    withContext(Dispatchers.Main) {
                        android.util.Log.d(TAG, "importFromFile: collections=${result.importedCollections}, contacts=${result.importedContacts}")
                        Toast.makeText(context, "导入完成：${result.importedCollections}个名片夹，${result.importedContacts}个联系人", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = "名片夹",
                scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState()),
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
                                        showExportDialog = true
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
        },
        floatingActionButton = {
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
        },
        floatingToolbar = {
            if (showCollectionToolbar && selectedCollectionItem != null) {
                val item = selectedCollectionItem!!
                Box(modifier = Modifier.padding(bottom = LocalFloatingBarBottomPadding.current)) {
                    FloatingToolbar(cornerRadius = 16.dp) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            ToolbarAction(
                                icon = Icons.Default.Delete,
                                label = "删除",
                                tint = Color.Red,
                                onClick = {
                                    showCollectionToolbar = false
                                    showCollectionDeleteDialog = true
                                }
                            )
                            ToolbarAction(
                                icon = Icons.Default.Share,
                                label = "分享",
                                onClick = {
                                    showCollectionToolbar = false
                                    scope.launch {
                                        try {
                                            val json = exportToJson(repository, listOf(item.collection.id))
                                            val fileName = "badger_${item.collection.name}_${System.currentTimeMillis()}.json"
                                            val sharedDir = File(context.cacheDir, "shared").apply { mkdirs() }
                                            val tempFile = File(sharedDir, fileName).also { it.writeText(json) }
                                            Log.d(TAG, "shareCollection: tempFile=${tempFile.absolutePath}")
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
                                            // 延迟删除临时文件，等分享完成
                                            withContext(Dispatchers.IO) {
                                                delay(5000)
                                                tempFile.delete()
                                                Log.d(TAG, "shareCollection: tempFile deleted")
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "shareCollection: failed", e)
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
        if (uiState is CardUiState.Loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("加载中...", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        } else if (uiState is CardUiState.Error) {            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("加载失败: ${uiState.message}", color = Color.Red)
            }
        } else if ((successState?.collections ?: emptyList()).isEmpty()) {
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
                        fontWeight = FontWeight.Medium
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
        } else {
            val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
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
                        text = "长按名片夹可删除或分享",
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
                            val isSelected = showCollectionToolbar && selectedCollectionItem?.collection?.id == item.collection.id
                            CollectionCard(
                                item = item,
                                selected = isSelected,
                                onClick = {
                                    if (showCollectionToolbar) {
                                        // 选中态下点击取消选中
                                        showCollectionToolbar = false
                                        selectedCollectionItem = null
                                    } else {
                                        onNavigateToCollectionDetail(item.collection.id)
                                    }
                                },
                                onLongClick = {
                                    selectedCollectionItem = item
                                    showCollectionToolbar = true
                                    android.util.Log.d(TAG, "longClick: selected collection=${item.collection.name}")
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

    // 名片夹删除确认对话框
    if (showCollectionDeleteDialog && selectedCollectionItem != null) {
        val item = selectedCollectionItem!!
        WindowDialog(
            show = true,
            title = "删除名片夹",
            summary = "确定删除「${item.collection.name}」吗？其中的联系人不会被删除。",
            onDismissRequest = { selectedCollectionItem = null }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(text = "取消", onClick = { selectedCollectionItem = null }, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(20.dp))
                TextButton(
                    text = "删除",
                    onClick = {
                        scope.launch(Dispatchers.IO) { onDeleteCollection(item) }
                        selectedCollectionItem = null
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }

    // 创建名片夹对话框
    if (showCreateDialog) {
        CreateCollectionDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, desc ->
                onCreateCollection(name, desc)
                scope.launch {
                    snackbarHostState.showSnackbar("名片夹已创建", duration = SnackbarDuration.Custom(2000))
                }
            }
        )
    }

    // 导出对话框
    if (showExportDialog) {
        WindowDialog(
            show = true,
            title = "导出名片夹",
            onDismissRequest = { showExportDialog = false }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = "复制到剪贴板",
                    onClick = {
                        scope.launch {
                            try {
                                val ids = successState?.collections?.map { it.collection.id } ?: emptyList()
                                val json = exportToJson(repository, ids)
                                Methods.copyToClipboard(context, "badger_export", json)
                                Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
                BasicComponent(
                    title = "保存到文件",
                    onClick = {
                        exportFileLauncher.launch("badger_export_${System.currentTimeMillis()}.json")
                    }
                )
            }
        }
    }

    // 导入对话框
    if (showImportDialog) {
        WindowDialog(
            show = true,
            title = "导入名片夹",
            onDismissRequest = { showImportDialog = false }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = "从剪贴板",
                    onClick = {
                        scope.launch { importFromClipboard(context, repository) }
                    }
                )
                BasicComponent(
                    title = "从文件",
                    onClick = {
                        importFileLauncher.launch("application/json")
                    }
                )
            }
        }
    }
}
