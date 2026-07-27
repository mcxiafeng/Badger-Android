package top.mcxiafeng.badger.pages.card

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import org.koin.androidx.compose.koinViewModel
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity as CardCollection
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.ImportConflict
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.mcxiafeng.badger.ui.components.DialogButtonRow
import top.mcxiafeng.badger.ui.components.contentColorFor
import top.mcxiafeng.badger.ui.components.isLightColor
import top.mcxiafeng.badger.ui.components.textContentColorForBitmap
import top.mcxiafeng.badger.ui.components.subTextColorFor
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.pages.person.contact.ToolbarAction
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
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

private const val TAG = "Tester"

/**
 * 名片夹详情页（联系人列表）
 */
@Composable
fun CollectionDetailPage(
    collectionId: Long,
    onBack: () -> Unit,
    onNavigateToScanner: (Long) -> Unit,
    onNavigateToContactDetail: (Long) -> Unit,
    onNavigateToCreateContact: (Long) -> Unit = {},
    viewModel: CardViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var collection by remember(collectionId) { mutableStateOf<CardCollection?>(null) }
    var contacts by remember(collectionId) { mutableStateOf<List<Contact>>(emptyList()) }
    var scanRecordCounts by remember(collectionId) { mutableStateOf<Map<Long, Int>>(emptyMap()) }

    LaunchedEffect(collectionId) {
        val coll = viewModel.getCollectionById(collectionId)
        if (coll != null) {
            collection = coll
        }
        viewModel.getContactsByCollectionFlow(collectionId).collect { list ->
            contacts = list
            // 联系人列表变化时同步刷新 scanRecordCounts，避免过时缓存
            scanRecordCounts = viewModel.getScanRecordCountsByCollection(collectionId)
        }
    }

    var showMoreMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showContactPicker by remember { mutableStateOf(false) }
    var showExportCollectionDialog by remember { mutableStateOf(false) }
    var showImportContactsDialog by remember { mutableStateOf(false) }
    var showAddChoiceDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    // 导入联系人冲突状态
    var importContactConflicts by remember { mutableStateOf<List<ImportConflict>?>(null) }
    var showContactConflictDialog by remember { mutableStateOf(false) }
    val mergeChecked = remember { mutableStateMapOf<String, Boolean>() }
    val newStyleChecked = remember { mutableStateMapOf<String, Boolean>() }
    val forceImportChecked = remember { mutableStateMapOf<String, Boolean>() }
    val importChecked = remember { mutableStateMapOf<String, Boolean>() }

    // 多选模式
    var isInSelectionMode by remember { mutableStateOf(false) }
    var selectedContactIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showBatchRemoveDialog by remember { mutableStateOf(false) }

    fun exitSelectionMode() {
        isInSelectionMode = false
        selectedContactIds = emptySet()
    }

    // 文件导入选择器
    val importContactFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                        ?: throw IllegalArgumentException("无法读取文件")
                    val conflicts = viewModel.analyzeImportConflicts(json)
                    withContext(Dispatchers.Main) {
                        importContactConflicts = conflicts
                        mergeChecked.clear()
                        newStyleChecked.clear()
                        forceImportChecked.clear()
                        importChecked.clear()
                        showContactConflictDialog = false
                        Log.d(TAG, "importContacts: ${conflicts.size} collections, ${conflicts.sumOf { it.contactConflicts.size }} contacts")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "importContacts: failed", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    // 文件导出选择器
    val exportCollectionFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = viewModel.exportCollectionToJson(listOf(collectionId))
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    withContext(Dispatchers.Main) {
                        Log.d(TAG, "exportCollectionToFile: success")
                        Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "exportCollectionToFile: failed", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    BackHandler(enabled = isInSelectionMode) {
        Log.d(TAG, "BackHandler: exit selection mode")
        exitSelectionMode()
    }

    Scaffold(
        topBar = {
            if (isInSelectionMode) {
                TopAppBar(
                    title = "已选择 ${selectedContactIds.size} 项",
                    scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState()),
                    navigationIcon = {
                        IconButton(onClick = {
                            Log.d(TAG, "exitSelectionMode: via top bar close")
                            exitSelectionMode()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "取消"
                            )
                        }
                    },
                    actions = {
                        val allIds = contacts.map { it.id }.toSet()
                        val isAllSelected = allIds.isNotEmpty() && allIds.all { it in selectedContactIds }
                        IconButton(onClick = {
                            selectedContactIds = if (isAllSelected) emptySet() else allIds
                            Log.d(TAG, "toggleSelectAll: isAllSelected=$isAllSelected, size=${selectedContactIds.size}")
                        }) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = if (isAllSelected) "取消全选" else "全选",
                                tint = if (isAllSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = collection?.name ?: "",
                    scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState()),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(imageVector = Icons.Default.MoreVert, contentDescription = "更多")
                            }
                            OverlayListPopup(
                                show = showMoreMenu,
                                alignment = PopupPositionProvider.Align.TopEnd,
                                popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                                onDismissRequest = { showMoreMenu = false },
                            ) {
                                ListPopupColumn {
                                    DropdownImpl(
                                        text = "编辑名片夹",
                                        optionSize = 4,
                                        isSelected = false,
                                        index = 0,
                                        onSelectedIndexChange = {
                                            showMoreMenu = false
                                            showEditDialog = true
                                        }
                                    )
                                    DropdownImpl(
                                        text = "导出此名片夹",
                                        optionSize = 4,
                                        isSelected = false,
                                        index = 1,
                                        onSelectedIndexChange = {
                                            showMoreMenu = false
                                            showExportCollectionDialog = true
                                        }
                                    )
                                    DropdownImpl(
                                        text = "导入联系人",
                                        optionSize = 4,
                                        isSelected = false,
                                        index = 2,
                                        onSelectedIndexChange = {
                                            showMoreMenu = false
                                            showImportContactsDialog = true
                                        }
                                    )
                                    DropdownImpl(
                                        text = "删除名片夹",
                                        optionSize = 4,
                                        isSelected = false,
                                        index = 3,
                                        onSelectedIndexChange = {
                                            showMoreMenu = false
                                            showDeleteDialog = true
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
                    onClick = { showAddChoiceDialog = true },
                    modifier = Modifier.padding(bottom = floatingBarBottomPadding)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "添加联系人",
                        tint = MiuixTheme.colorScheme.onPrimary
                    )
                }
            }
        },
        floatingToolbar = {
            AnimatedVisibility(
                visible = isInSelectionMode && selectedContactIds.isNotEmpty(),
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
                                icon = Icons.Default.Delete,
                                label = "移除",
                                tint = MiuixTheme.colorScheme.error,
                                onClick = {
                                    Log.d(TAG, "openBatchRemoveDialog: size=${selectedContactIds.size}")
                                    showBatchRemoveDialog = true
                                }
                            )
                        }
                    }
                }
            }
        },
        floatingToolbarPosition = ToolbarPosition.BottomCenter,
    ) { paddingValues ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + 8.dp,
                bottom = paddingValues.calculateBottomPadding() + 8.dp + LocalFloatingBarBottomPadding.current
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            // Hero header
            item(key = "hero_header") {
                val hasBg = !collection?.backgroundImagePath.isNullOrBlank()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .animateContentSize(animationSpec = tween(300))
                ) {
                    val headerHeight = if (hasBg) 200.dp else 80.dp
                    Box(modifier = Modifier.fillMaxWidth().height(headerHeight)) {
                        var bgBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                        LaunchedEffect(collection?.backgroundImagePath) {
                            bgBitmap = top.mcxiafeng.badger.utils.Methods.loadBackgroundBitmap(collection?.backgroundImagePath)
                        }
                        val isDark = isSystemInDarkTheme()
                        Crossfade(targetState = bgBitmap, animationSpec = tween(300), label = "heroBgCrossfade") { bmp ->
                            if (bmp != null) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    // 全图半透明遮罩，保证文字对比度
                                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)))
                                    // 底部渐变
                                    Box(modifier = Modifier.fillMaxSize().background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                                            startY = 0f
                                        )
                                    ))
                                    if (isDark) {
                                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
                                    }
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surfaceContainer))
                            }
                        }
                        val heroTextColor = textContentColorForBitmap(
                            bgBitmap, collection?.dominantColor, MiuixTheme.colorScheme.onBackground
                        )
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = collection?.name ?: "",
                                color = heroTextColor,
                                style = MiuixTheme.textStyles.title3,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!collection?.description.isNullOrBlank()) {
                                Text(
                                    text = collection?.description.orEmpty(),
                                    color = subTextColorFor(heroTextColor, MiuixTheme.colorScheme.onSurfaceVariantSummary),
                                    style = MiuixTheme.textStyles.body2,
                                    maxLines = if (hasBg) 2 else 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            if (contacts.isEmpty()) {
                item(key = "empty_state") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无联系人", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body1)
                    }
                }
            } else {
                item {
                    Text(
                        text = "共 ${contacts.size} 位联系人",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }
                items(
                    contacts,
                    key = { it.id },
                    contentType = { _ -> "contact" }
                ) { contact ->
                    val isSelected = isInSelectionMode && contact.id in selectedContactIds
                    Box(
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                if (isInSelectionMode) {
                                    selectedContactIds = if (isSelected) {
                                        selectedContactIds - contact.id
                                    } else {
                                        selectedContactIds + contact.id
                                    }
                                    Log.d(TAG, "toggleSelection: contact=${contact.name}, selected=${!isSelected}, total=${selectedContactIds.size}")
                                } else {
                                    onNavigateToContactDetail(contact.id)
                                }
                            },
                            onLongClick = {
                                if (!isInSelectionMode) {
                                    isInSelectionMode = true
                                    selectedContactIds = setOf(contact.id)
                                    Log.d(TAG, "enterSelectionMode: contact=${contact.name}")
                                }
                            }
                        )
                    ) {
                        BasicComponent(
                            title = contact.name,
                            summary = contact.note,
                            modifier = Modifier
                                .then(
                                    if (isSelected) Modifier.background(MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                    else Modifier
                                ),
                            startAction = {
                                ContactAvatar(name = contact.name, avatarUrl = contact.avatarUrl, size = 40)
                            },
                            endActions = {
                                if (isInSelectionMode) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = if (isSelected) "已选" else "未选",
                                        tint = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                val count = scanRecordCounts[contact.id] ?: 1
                                if (count > 1) {
                                    val badgeColor = collection?.dominantColor?.let { Color(it) } ?: MiuixTheme.colorScheme.primary
                                    val badgeTextColor = collection?.dominantColor?.let { contentColorFor(it) } ?: Color.White
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(badgeColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = count.toString(),
                                            color = badgeTextColor,
                                            style = MiuixTheme.textStyles.footnote2
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                            }
                        )
                    }
                }
            }
        }
        }

    // 批量移除确认对话框
    if (showBatchRemoveDialog && selectedContactIds.isNotEmpty()) {
        WindowDialog(
            show = true,
            title = "移除联系人",
            summary = "确定要从「${collection?.name.orEmpty()}」移除选中的 ${selectedContactIds.size} 个联系人吗？联系人本身不会被删除。",
            onDismissRequest = { showBatchRemoveDialog = false }
        ) {
            DialogButtonRow(
                positiveText = "移除",
                isDestructive = true,
                onNegative = { showBatchRemoveDialog = false },
                onPositive = {
                    showBatchRemoveDialog = false
                    val ids = selectedContactIds.toList()
                    scope.launch(Dispatchers.IO) {
                        viewModel.removeContactsFromCollection(ids, collectionId)
                        withContext(Dispatchers.Main) {
                            Log.d(TAG, "batchRemove: count=${ids.size}")
                            Toast.makeText(context, "已移除 ${ids.size} 个联系人", Toast.LENGTH_SHORT).show()
                            exitSelectionMode()
                        }
                    }
                }
            )
        }
    }

    // 添加联系人选择对话框
    if (showAddChoiceDialog) {
        WindowDialog(
            show = true,
            title = "添加联系人",
            onDismissRequest = { showAddChoiceDialog = false }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = "从已有联系人添加",
                    summary = "从通讯录中选择",
                    onClick = {
                        showAddChoiceDialog = false
                        showContactPicker = true
                    }
                )
                BasicComponent(
                    title = "扫码添加",
                    summary = "扫描名片二维码",
                    onClick = {
                        showAddChoiceDialog = false
                        onNavigateToScanner(collectionId)
                    }
                )
                BasicComponent(
                    title = "手动添加",
                    summary = "手动输入联系人信息",
                    onClick = {
                        showAddChoiceDialog = false
                        onNavigateToCreateContact(collectionId)
                    }
                )
            }
        }
    }

    // 编辑名片夹对话框
    if (showEditDialog && collection != null) {
        EditCollectionDialog(
            collection = collection!!,
            onDismiss = { showEditDialog = false },
            onConfirm = { updatedCollection ->
                scope.launch {
                    viewModel.updateCollection(updatedCollection)
                    collection = viewModel.getCollectionById(collectionId)
                }
                showEditDialog = false
            }
        )
    }

    // 删除名片夹确认对话框
    if (showDeleteDialog && collection != null) {
        WindowDialog(
            show = true,
            title = "删除名片夹",
            summary = "确定删除「${collection!!.name}」吗？其中的联系人不会被删除。",
            onDismissRequest = { showDeleteDialog = false }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(text = "取消", onClick = { showDeleteDialog = false }, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(20.dp))
                TextButton(
                    text = "删除",
                    onClick = {
                        showDeleteDialog = false
                        val bgPath = collection!!.backgroundImagePath
                        scope.launch(Dispatchers.IO) {
                            viewModel.deleteCollectionDirect(collection!!)
                            top.mcxiafeng.badger.utils.Methods.deleteFileIfExists(bgPath)
                            Log.d(TAG, "deleteCollection: id=${collection!!.id}, bgPath=$bgPath cleaned")
                            withContext(Dispatchers.Main) { onBack() }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }

    // 添加联系人选择器
    if (showContactPicker) {
        ContactSelectDialog(
            repository = viewModel.getContactRepository(),
            existingContactIds = contacts.map { it.id }.toSet(),
            onDismiss = { showContactPicker = false },
            onContactSelected = { contact ->
                showContactPicker = false
                scope.launch(Dispatchers.IO) {
                    viewModel.addContactToCollection(contact.id, collectionId, "manual")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "已添加 ${contact.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // 导出 → 直接触发保存文件
    if (showExportCollectionDialog) {
        showExportCollectionDialog = false
        exportCollectionFileLauncher.launch("badger_export_${System.currentTimeMillis()}.json")
    }

    // 导入 → 直接触发选择文件
    if (showImportContactsDialog) {
        showImportContactsDialog = false
        importContactFileLauncher.launch("application/json")
    }

    // 导入联系人：弹出联系人列表对话框
    if (importContactConflicts != null && !showContactConflictDialog) {
        val allContacts = importContactConflicts!!.flatMap { it.contactConflicts }
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

    if (showContactConflictDialog && importContactConflicts != null) {
        ImportConflictDialog(
            conflicts = importContactConflicts!!,
            contactRepository = viewModel.getContactRepository(),
            fieldRepository = viewModel.getFieldRepository(),
            collectionRepository = viewModel.getCollectionRepository(),
            tagRepository = viewModel.getTagRepository(),
            scope = scope,
            mergeChecked = mergeChecked,
            newStyleChecked = newStyleChecked,
            forceImportChecked = forceImportChecked,
            importChecked = importChecked,
            onDismiss = {
                showContactConflictDialog = false
                mergeChecked.clear()
                newStyleChecked.clear()
                forceImportChecked.clear()
                importChecked.clear()
                importContactConflicts = null
            },
            onSuccess = { msg ->
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        )
    }
}
