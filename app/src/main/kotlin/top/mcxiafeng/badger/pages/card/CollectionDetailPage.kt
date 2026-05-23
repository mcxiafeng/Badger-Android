package top.mcxiafeng.badger.pages.card

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.CardCollection
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.rememberContactRepository
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.mcxiafeng.badger.pages.person.contact.ToolbarAction
import top.mcxiafeng.badger.utils.exportToJson
import top.mcxiafeng.badger.utils.importContactsToCollection
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
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private const val TAG = "CollectionDetailPage"

/**
 * 名片夹详情页（联系人列表）
 */
@Composable
fun CollectionDetailPage(
    collectionId: Long,
    onBack: () -> Unit,
    onNavigateToScanner: (Long) -> Unit,
    onNavigateToContactDetail: (Long) -> Unit
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
        }
    }

    LaunchedEffect(collectionId) {
        styleCounts = withContext(Dispatchers.IO) {
            repository.getStyleCountsByCollection(collectionId)
        }
    }

    var showMoreMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showContactPicker by remember { mutableStateOf(false) }
    var showExportCollectionDialog by remember { mutableStateOf(false) }
    var showImportContactsDialog by remember { mutableStateOf(false) }
    var showAddChoiceDialog by remember { mutableStateOf(false) }
    // 文件导入选择器
    val importContactFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                        ?: throw IllegalArgumentException("无法读取文件")
                    val count = importContactsToCollection(repository, collectionId, json)
                    withContext(Dispatchers.Main) {
                        android.util.Log.d(TAG, "importContactsToCollection: count=$count")
                        Toast.makeText(context, "已导入 $count 位联系人", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    // 长按联系人
    var showContactContextMenu by remember { mutableStateOf(false) }
    var selectedContact by remember { mutableStateOf<Contact?>(null) }

    BackHandler(enabled = showContactContextMenu) {
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
                                    text = "导出此名片夹",
                                    optionSize = 3,
                                    isSelected = false,
                                    index = 0,
                                    onSelectedIndexChange = {
                                        showMoreMenu = false
                                        showExportCollectionDialog = true
                                    }
                                )
                                DropdownImpl(
                                    text = "导入联系人",
                                    optionSize = 3,
                                    isSelected = false,
                                    index = 1,
                                    onSelectedIndexChange = {
                                        showMoreMenu = false
                                        showImportContactsDialog = true
                                    }
                                )
                                DropdownImpl(
                                    text = "删除名片夹",
                                    optionSize = 3,
                                    isSelected = false,
                                    index = 2,
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
            FloatingActionButton(onClick = { showAddChoiceDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "添加联系人",
                    tint = MiuixTheme.colorScheme.onPrimary
                )
            }
        },
        floatingToolbar = {
            if (showContactContextMenu && selectedContact != null) {
                val contact = selectedContact!!
                FloatingToolbar(cornerRadius = 16.dp) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        ToolbarAction(
                            icon = Icons.Default.Delete,
                            label = "移除",
                            tint = Color.Red,
                            onClick = {
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
        },
        floatingToolbarPosition = ToolbarPosition.BottomCenter,
    ) { paddingValues ->
        if (contacts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无联系人", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 16.sp)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding() + 8.dp,
                    bottom = paddingValues.calculateBottomPadding() + 8.dp
                ),
                modifier = Modifier.fillMaxSize()
            ) {
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
                                        showContactContextMenu = false
                                        selectedContact = null
                                    }
                                    onNavigateToContactDetail(contact.id)
                                },
                                onLongClick = {
                                    selectedContact = contact
                                    showContactContextMenu = true
                                }
                            ),
                        startAction = {
                            ContactAvatar(name = contact.name, avatarUrl = contact.avatarUrl, size = 40)
                        },
                        endActions = {
                            val count = styleCounts[contact.id] ?: 1
                            if (count > 1) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(MiuixTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = count.toString(),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
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
                        showContactPicker = true
                    }
                )
                BasicComponent(
                    title = "扫码添加",
                    summary = "扫描名片二维码",
                    onClick = {
                        onNavigateToScanner(collectionId)
                    }
                )
            }
        }
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
                        scope.launch(Dispatchers.IO) {
                            repository.deleteCollection(collection!!)
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
                scope.launch(Dispatchers.IO) {
                    repository.addContactToCollection(contact.id, collectionId, "manual")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "已添加 ${contact.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // 导出此名片夹对话框
    if (showExportCollectionDialog && collection != null) {
        val coll = collection!!
        WindowDialog(
            show = true,
            title = "导出「${coll.name}」",
            onDismissRequest = { showExportCollectionDialog = false }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = "复制到剪贴板",
                    onClick = {
                        scope.launch {
                            try {
                                val json = exportToJson(repository, listOf(collectionId))
                                top.mcxiafeng.badger.utils.Methods.copyToClipboard(context, "badger_export", json)
                                Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
                BasicComponent(
                    title = "分享",
                    onClick = {
                        scope.launch {
                            try {
                                val json = exportToJson(repository, listOf(collectionId))
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, json)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(Intent.createChooser(intent, "分享名片夹"))
                            } catch (_: Exception) {}
                        }
                    }
                )
            }
        }
    }

    // 导入联系人对话框
    if (showImportContactsDialog) {
        WindowDialog(
            show = true,
            title = "导入联系人",
            onDismissRequest = { showImportContactsDialog = false }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = "从剪贴板",
                    onClick = {
                        scope.launch {
                            try {
                                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val text = cm.primaryClip?.getItemAt(0)?.text?.toString()
                                if (text.isNullOrBlank()) throw IllegalArgumentException("剪贴板为空")
                                val count = importContactsToCollection(repository, collectionId, text)
                                withContext(Dispatchers.Main) {
                                    android.util.Log.d(TAG, "importFromClipboard: count=$count")
                                    Toast.makeText(context, "已导入 $count 位联系人", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                )
                BasicComponent(
                    title = "从文件",
                    onClick = {
                        importContactFileLauncher.launch("application/json")
                    }
                )
            }
        }
    }
}
