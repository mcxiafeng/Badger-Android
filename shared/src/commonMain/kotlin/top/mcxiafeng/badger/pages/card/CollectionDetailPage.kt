package top.mcxiafeng.badger.pages.card

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import top.mcxiafeng.badger.platform.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity as CardCollection
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.importer.ImportConflict
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.pages.person.contact.detail.ToolbarAction
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
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X
import top.mcxiafeng.badger.platform.rememberDocumentPickLauncher
import top.mcxiafeng.badger.platform.rememberDocumentSaveLauncher
import top.mcxiafeng.badger.platform.showToast
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.shared.util.deleteFileQuietly
import top.mcxiafeng.badger.shared.util.nowMs
import top.mcxiafeng.badger.shared.util.BadgerDispatchers

private const val TAG = "CollectionDetailPage"

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
    val scope = rememberCoroutineScope()

    var collection by remember(collectionId) { mutableStateOf<CardCollection?>(null) }
    var contacts by remember(collectionId) { mutableStateOf<List<Contact>>(emptyList()) }
    var memberCounts by remember(collectionId) { mutableStateOf<Map<Long, Int>>(emptyMap()) }

    LaunchedEffect(collectionId) {
        val coll = viewModel.getCollectionById(collectionId)
        if (coll != null) {
            collection = coll
        }
        viewModel.getContactsByCollectionFlow(collectionId).collect { list ->
            contacts = list
            // 联系人列表变化时同步刷新 memberCounts，避免过时缓存
            memberCounts = viewModel.getMemberCountsByCollection(collectionId)
        }
    }

    var showMoreMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showContactPicker by remember { mutableStateOf(false) }
    var showExportCollectionDialog by remember { mutableStateOf(false) }
    var showImportContactsDialog by remember { mutableStateOf(false) }
    var showAddChoiceDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    // 导入联系人冲突状态（[F6/F7] 勾选状态按 ContactConflict.rowId 为键，禁 name 键）
    var importContactConflicts by remember { mutableStateOf<List<ImportConflict>?>(null) }
    var showContactConflictDialog by remember { mutableStateOf(false) }
    val mergeChecked = remember { mutableStateMapOf<Int, Boolean>() }
    val newStyleChecked = remember { mutableStateMapOf<Int, Boolean>() }
    val forceImportChecked = remember { mutableStateMapOf<Int, Boolean>() }
    val importChecked = remember { mutableStateMapOf<Int, Boolean>() }

    // 多选模式
    var isInSelectionMode by remember { mutableStateOf(false) }
    var selectedContactIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showBatchRemoveDialog by remember { mutableStateOf(false) }

    fun exitSelectionMode() {
        isInSelectionMode = false
        selectedContactIds = emptySet()
    }

    // 文件导入选择器
    val importContactFileLauncher = rememberDocumentPickLauncher("application/json") { bytes ->
        if (bytes != null) {
            scope.launch {
                try {
                    val json = bytes.decodeToString()
                    val conflicts = viewModel.analyzeImportConflicts(json)
                    withContext(Dispatchers.Main) {
                        importContactConflicts = conflicts
                        mergeChecked.clear()
                        newStyleChecked.clear()
                        forceImportChecked.clear()
                        importChecked.clear()
                        showContactConflictDialog = false
                        BadgerLog.d(TAG, "importContacts: ${conflicts.size} collections, ${conflicts.sumOf { it.contactConflicts.size }} contacts")
                    }
                } catch (e: Exception) {
                    BadgerLog.e(TAG, "importContacts: failed", e)
                    withContext(Dispatchers.Main) {
                        showToast("导入失败: ${e.message}")
                    }
                }
            }
        }
    }
    // 文件导出选择器
    val exportCollectionFileLauncher = rememberDocumentSaveLauncher(
        mime = "application/json",
        suggestedName = "badger_collection.json",
    ) { ok ->
        scope.launch {
            try {
                if (ok) {
                    withContext(Dispatchers.Main) {
                        BadgerLog.d(TAG, "exportCollectionToFile: success")
                        showToast("导出成功")
                    }
                }
            } catch (e: Exception) {
                BadgerLog.e(TAG, "exportCollectionToFile: failed", e)
                withContext(Dispatchers.Main) {
                    showToast("导出失败: ${e.message}")
                }
            }
        }
    }

    BackHandler(enabled = isInSelectionMode) {
        BadgerLog.d(TAG, "BackHandler: exit selection mode")
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
                            BadgerLog.d(TAG, "exitSelectionMode: via top bar close")
                            exitSelectionMode()
                        }) {
                            Icon(
                                imageVector = Lucide.X,
                                contentDescription = "取消"
                            )
                        }
                    },
                    actions = {
                        val allIds = contacts.map { it.id }.toSet()
                        val isAllSelected = allIds.isNotEmpty() && allIds.all { it in selectedContactIds }
                        IconButton(onClick = {
                            selectedContactIds = if (isAllSelected) emptySet() else allIds
                            BadgerLog.d(TAG, "toggleSelectAll: isAllSelected=$isAllSelected, size=${selectedContactIds.size}")
                        }) {
                            Icon(
                                imageVector = Lucide.CircleCheck,
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
                                imageVector = Lucide.ArrowLeft,
                                contentDescription = "返回"
                            )
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(imageVector = Lucide.EllipsisVertical, contentDescription = "更多")
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
                        imageVector = Lucide.Plus,
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
                                icon = Lucide.Trash2,
                                label = "移除",
                                tint = MiuixTheme.colorScheme.error,
                                onClick = {
                                    BadgerLog.d(TAG, "openBatchRemoveDialog: size=${selectedContactIds.size}")
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
        if (contacts.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(
                    PaddingValues(
                        top = paddingValues.calculateTopPadding() + 8.dp,
                        bottom = paddingValues.calculateBottomPadding() + 8.dp + LocalFloatingBarBottomPadding.current
                    )
                )
            ) {
                CollectionDetailHeroHeader(collection)
                CollectionDetailEmptyState()
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding() + 8.dp,
                    bottom = paddingValues.calculateBottomPadding() + 8.dp + LocalFloatingBarBottomPadding.current
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "hero_header") {
                    CollectionDetailHeroHeader(collection)
                }
                collectionDetailContactList(
                    contacts = contacts,
                    isInSelectionMode = isInSelectionMode,
                    selectedContactIds = selectedContactIds,
                    memberCounts = memberCounts,
                    collection = collection,
                    onContactClick = { contact, isSelected ->
                        if (isInSelectionMode) {
                            selectedContactIds = if (isSelected) {
                                selectedContactIds - contact.id
                            } else {
                                selectedContactIds + contact.id
                            }
                            BadgerLog.d(TAG, "toggleSelection: contact=${contact.name}, selected=${!isSelected}, total=${selectedContactIds.size}")
                        } else {
                            onNavigateToContactDetail(contact.id)
                        }
                    },
                    onContactLongClick = { contact ->
                        if (!isInSelectionMode) {
                            isInSelectionMode = true
                            selectedContactIds = setOf(contact.id)
                            BadgerLog.d(TAG, "enterSelectionMode: contact=${contact.name}")
                        }
                    },
                )
            }
        }
    }

    // 导出 → 直接触发保存文件
    LaunchedEffect(showExportCollectionDialog) {
        if (showExportCollectionDialog) {
            showExportCollectionDialog = false
            exportCollectionFileLauncher.launch("badger_export_${nowMs()}.json")
        }
    }

    // 导入 → 直接触发选择文件
    LaunchedEffect(showImportContactsDialog) {
        if (showImportContactsDialog) {
            showImportContactsDialog = false
            importContactFileLauncher.launch()
        }
    }

    CollectionDetailDialogs(
        viewModel = viewModel,
        collectionId = collectionId,
        collection = collection,
        showBatchRemoveDialog = showBatchRemoveDialog,
        onDismissBatchRemove = { showBatchRemoveDialog = false },
        selectedContactIds = selectedContactIds,
        exitSelectionMode = { exitSelectionMode() },
        showAddChoiceDialog = showAddChoiceDialog,
        onDismissAddChoice = { showAddChoiceDialog = false },
        onOpenContactPicker = {
            showAddChoiceDialog = false
            showContactPicker = true
        },
        onNavigateToScanner = {
            showAddChoiceDialog = false
            onNavigateToScanner(collectionId)
        },
        onNavigateToCreateContact = {
            showAddChoiceDialog = false
            onNavigateToCreateContact(collectionId)
        },
        showEditDialog = showEditDialog,
        onDismissEdit = { showEditDialog = false },
        onEditConfirm = { updatedCollection ->
            scope.launch {
                viewModel.updateCollection(updatedCollection)
                collection = viewModel.getCollectionById(collectionId)
            }
            showEditDialog = false
        },
        showDeleteDialog = showDeleteDialog,
        onDismissDelete = { showDeleteDialog = false },
        onDeleteConfirm = {
            val bgPath = collection!!.backgroundImagePath
            scope.launch(BadgerDispatchers.io) {
                viewModel.deleteCollectionDirect(collection!!)
                deleteFileQuietly(bgPath)
                BadgerLog.d(TAG, "deleteCollection: id=${collection!!.id}, bgPath=$bgPath cleaned")
                withContext(Dispatchers.Main) { onBack() }
            }
        },
        showContactPicker = showContactPicker,
        onDismissContactPicker = { showContactPicker = false },
        onContactSelected = { contact ->
            showContactPicker = false
            scope.launch(BadgerDispatchers.io) {
                viewModel.addContactToCollection(contact.id, collectionId, "manual")
                withContext(Dispatchers.Main) {
                    showToast("已添加 ${contact.name}")
                }
            }
        },
        searchContacts = { query ->
            viewModel.searchAvailableContacts(
                query = query,
                existingContactIds = contacts.map { it.id }.toSet()
            )
        },
        importContactConflicts = importContactConflicts,
        showContactConflictDialog = showContactConflictDialog,
        setShowContactConflictDialog = { showContactConflictDialog = it },
        mergeChecked = mergeChecked,
        newStyleChecked = newStyleChecked,
        forceImportChecked = forceImportChecked,
        importChecked = importChecked,
        onDismissConflict = {
            showContactConflictDialog = false
            mergeChecked.clear()
            newStyleChecked.clear()
            forceImportChecked.clear()
            importChecked.clear()
            importContactConflicts = null
        },
    )
}
