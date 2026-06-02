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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.CardCollection
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.rememberContactRepository
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.mcxiafeng.badger.ui.components.contentColorFor
import top.mcxiafeng.badger.ui.components.isLightColor
import top.mcxiafeng.badger.ui.components.textContentColorForBitmap
import top.mcxiafeng.badger.ui.components.subTextColorFor
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.pages.person.contact.ToolbarAction
import top.mcxiafeng.badger.data.exportToJson
import top.mcxiafeng.badger.data.analyzeImportConflicts
import top.mcxiafeng.badger.data.executeImport
import top.mcxiafeng.badger.data.ImportConflict
import top.mcxiafeng.badger.data.ContactConflictAction
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Checkbox
import androidx.compose.ui.state.ToggleableState
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
    viewModel: CardViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val repository = rememberContactRepository()
    val scope = rememberCoroutineScope()

    var collection by remember(collectionId) { mutableStateOf<CardCollection?>(null) }
    var contacts by remember(collectionId) { mutableStateOf<List<Contact>>(emptyList()) }
    var styleCounts by remember(collectionId) { mutableStateOf<Map<Long, Int>>(emptyMap()) }

    LaunchedEffect(collectionId) {
        val coll = repository.getCollectionById(collectionId)
        if (coll != null) {
            collection = coll
        }
        repository.getContactsByCollection(collectionId).collect { list ->
            contacts = list
            // 联系人列表变化时同步刷新 styleCounts，避免过时缓存
            styleCounts = withContext(Dispatchers.IO) {
                repository.getStyleCountsByCollection(collectionId)
            }
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

    // 文件导入选择器
    val importContactFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                        ?: throw IllegalArgumentException("无法读取文件")
                    val conflicts = analyzeImportConflicts(repository, json)
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
                    val json = exportToJson(repository, listOf(collectionId))
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
    // 长按联系人
    var showContactContextMenu by remember { mutableStateOf(false) }
    var selectedContact by remember { mutableStateOf<Contact?>(null) }

    BackHandler(enabled = showContactContextMenu) {
        Log.d(TAG, "BackHandler: exit contact context menu")
        showContactContextMenu = false
        selectedContact = null
    }

    Scaffold(
        topBar = {
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
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !showContactContextMenu,
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
            val currentContact = selectedContact
            if (currentContact != null) {
            AnimatedVisibility(
                visible = showContactContextMenu,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                val contact = currentContact
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
                                    Log.d(TAG, "removeContact: ${contact.name}")
                                    scope.launch(Dispatchers.IO) {
                                        repository.removeContactFromCollection(contact.id, collectionId)
                                        withContext(Dispatchers.Main) {
                                            showContactContextMenu = false
                                            selectedContact = null
                                            Toast.makeText(context, "已移除 ${contact.name}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
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
                    val isSelected = showContactContextMenu && selectedContact?.id == contact.id
                    BasicComponent(
                        title = contact.name,
                        summary = contact.note,
                        modifier = Modifier
                            .then(
                                if (isSelected) Modifier.background(MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                else Modifier
                            )
                            .combinedClickable(
                                onClick = {
                                    if (showContactContextMenu) {
                                        if (selectedContact?.id == contact.id) {
                                            Log.d(TAG, "deselectContact: ${contact.name}")
                                            showContactContextMenu = false
                                            selectedContact = null
                                        } else {
                                            selectedContact = contact
                                            Log.d(TAG, "switchSelection: selected contact=${contact.name}")
                                        }
                                        return@combinedClickable
                                    }
                                    onNavigateToContactDetail(contact.id)
                                },
                                onLongClick = {
                                    selectedContact = contact
                                    showContactContextMenu = true
                                    Log.d(TAG, "longClick: selected contact=${contact.name}")
                                }
                            ),
                        startAction = {
                            ContactAvatar(name = contact.name, avatarUrl = contact.avatarUrl, size = 40)
                        },
                        endActions = {
                            val count = styleCounts[contact.id] ?: 1
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
                    collection = repository.getCollectionById(collectionId)
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
                            repository.deleteCollection(collection!!)
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
            repository = repository,
            existingContactIds = contacts.map { it.id }.toSet(),
            onDismiss = { showContactPicker = false },
            onContactSelected = { contact ->
                showContactPicker = false
                scope.launch(Dispatchers.IO) {
                    repository.addContactToCollection(contact.id, collectionId, "manual")
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
        val allContacts = importContactConflicts!!.flatMap { it.contactConflicts }
        if (allContacts.isEmpty()) {
            WindowDialog(
                show = true,
                title = "导入联系人",
                onDismissRequest = {
                    showContactConflictDialog = false
                    importContactConflicts = null
                }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("没找到可导入的联系人", style = MiuixTheme.textStyles.body2)
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        text = "确定",
                        onClick = {
                            showContactConflictDialog = false
                            importContactConflicts = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
        val duplicateCount = allContacts.count { it.existingContact != null }
        WindowDialog(
            show = true,
            title = if (duplicateCount > 0) "导入联系人（${duplicateCount}重复）" else "导入联系人（${allContacts.size}）",
            onDismissRequest = {
                showContactConflictDialog = false
                mergeChecked.clear()
                newStyleChecked.clear()
                forceImportChecked.clear()
                importChecked.clear()
                importContactConflicts = null
            }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 表头
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(36.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("名称", style = MiuixTheme.textStyles.body2, modifier = Modifier.weight(1f))
                    if (duplicateCount > 0) {
                        Text("合并信息", style = MiuixTheme.textStyles.body2, modifier = Modifier.width(56.dp), textAlign = TextAlign.Center)
                        Text("新样式", style = MiuixTheme.textStyles.body2, modifier = Modifier.width(56.dp), textAlign = TextAlign.Center)
                        Text("新联系人", style = MiuixTheme.textStyles.body2, modifier = Modifier.width(56.dp), textAlign = TextAlign.Center)
                    } else {
                        Text("导入", style = MiuixTheme.textStyles.body2, modifier = Modifier.width(56.dp), textAlign = TextAlign.Center)
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(allContacts, key = { it.contactExport.name }) { cc ->
                        val name = cc.contactExport.name
                        val isDuplicate = cc.existingContact != null
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            top.mcxiafeng.badger.ui.components.ContactAvatar(
                                name = name,
                                avatarUrl = cc.contactExport.avatarUrl,
                                size = 36
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                name,
                                style = TextStyle(fontSize = 14.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (isDuplicate) {
                                Checkbox(
                                    state = if (mergeChecked[name] ?: true) ToggleableState.On else ToggleableState.Off,
                                    onClick = {
                                        val current = mergeChecked[name] ?: true
                                        mergeChecked[name] = !current
                                        if (!current) {
                                            forceImportChecked[name] = false
                                            newStyleChecked[name] = false
                                        }
                                    },
                                    modifier = Modifier.width(56.dp)
                                )
                                if (!(forceImportChecked[name] ?: false)) {
                                    Checkbox(
                                        state = if (newStyleChecked[name] ?: false) ToggleableState.On else ToggleableState.Off,
                                        onClick = {
                                            val current = newStyleChecked[name] ?: false
                                            if (!current && !(mergeChecked[name] ?: true)) {
                                                mergeChecked[name] = true
                                            }
                                            newStyleChecked[name] = !current
                                        },
                                        modifier = Modifier.width(56.dp)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.width(56.dp))
                                }
                                Checkbox(
                                    state = if (forceImportChecked[name] ?: false) ToggleableState.On else ToggleableState.Off,
                                    onClick = {
                                        val current = forceImportChecked[name] ?: false
                                        forceImportChecked[name] = !current
                                        if (!current) {
                                            mergeChecked[name] = false
                                            newStyleChecked[name] = true
                                        }
                                    },
                                    modifier = Modifier.width(56.dp)
                                )
                            } else {
                                Checkbox(
                                    state = if (importChecked[name] ?: true) ToggleableState.On else ToggleableState.Off,
                                    onClick = {
                                        val current = importChecked[name] ?: true
                                        importChecked[name] = !current
                                    },
                                    modifier = Modifier.width(56.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(text = "取消", onClick = {
                        showContactConflictDialog = false
                        mergeChecked.clear()
                        newStyleChecked.clear()
                        forceImportChecked.clear()
                        importChecked.clear()
                        importContactConflicts = null
                    }, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(20.dp))
                    TextButton(text = "确认", onClick = {
                        val conflicts = importContactConflicts ?: return@TextButton
                        val contactActions = mutableMapOf<String, ContactConflictAction>()
                        val contactAddStyleMap = mutableMapOf<String, Boolean>()
                        for (cc in allContacts) {
                            val name = cc.contactExport.name
                            if (cc.existingContact != null) {
                                val m = mergeChecked[name] ?: true
                                val n = newStyleChecked[name] ?: false
                                val f = forceImportChecked[name] ?: false
                                when {
                                    m -> {
                                        contactActions[name] = ContactConflictAction.MERGE
                                        contactAddStyleMap[name] = n
                                    }
                                    f -> {
                                        contactActions[name] = ContactConflictAction.FORCE_IMPORT
                                        contactAddStyleMap[name] = n
                                    }
                                    else -> {
                                        contactActions[name] = ContactConflictAction.SKIP
                                    }
                                }
                            } else {
                                if (importChecked[name] != false) {
                                    contactActions[name] = ContactConflictAction.FORCE_IMPORT
                                } else {
                                    contactActions[name] = ContactConflictAction.SKIP
                                }
                            }
                        }
                        scope.launch {
                            try {
                                val result = executeImport(repository, conflicts, emptyMap(), contactActions, emptyMap(), contactAddStyleMap)
                                withContext(Dispatchers.Main) {
                                    Log.d(TAG, "importContacts: done, new=${result.importedContacts}, merged=${result.mergedContacts}")
                                    Toast.makeText(context, "导入完成：${result.importedContacts}位新联系人，${result.mergedContacts}位已合并", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "importContacts: failed", e)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        showContactConflictDialog = false
                        mergeChecked.clear()
                        newStyleChecked.clear()
                        forceImportChecked.clear()
                        importChecked.clear()
                        importContactConflicts = null
                    }, modifier = Modifier.weight(1f))
                }
            }
        }
        }
    }
}
