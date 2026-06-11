package top.mcxiafeng.badger.pages.person.contact

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactFieldDisplay
import top.mcxiafeng.badger.data.ContactPlatform
import top.mcxiafeng.badger.data.ContactWithFields
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.ScanResult
import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.network.adapter.PlatformAdapterRegistry
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ocr.PLATFORM_FIELD_KEYS
import top.mcxiafeng.badger.utils.BILIBILI_HEADERS
import top.mcxiafeng.badger.utils.HttpUtil
import top.mcxiafeng.badger.utils.Methods
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 联系人详情页
 *
 * 纯 Miuix UI 的联系人详情展示页。
 * 布局：上方 Column（头像+姓名+备注+时间），下方分组 Card（ArrowPreference 列表）。
 * 作为独立二级页面显示，完全覆盖一级界面。
 *
 * @param contactId 联系人 ID
 * @param onBack 返回回调
 * @param onRefreshData 数据变更后的刷新回调（可选，用于通知外部刷新列表）
 */
@Composable
fun ContactDetailPage(
    contactId: Long,
    onBack: () -> Unit,
    onRefreshData: (() -> Unit)? = null,
    onOpenScannerForImport: (() -> Unit)? = null
) {
    // contactId = -1L 表示"我的名片"，走 UserProfile 展示页
    if (contactId == -1L) {
        UserProfileDetailPage(onBack = onBack, onRefreshData = onRefreshData, onOpenScannerForImport = onOpenScannerForImport)
        return
    }

    val context = LocalContext.current
    val viewModel: ContactDetailViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    // 从 ViewModel 观察状态
    val contactWithFields by viewModel.contactWithFields.collectAsState()
    val platformData by viewModel.platformData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showMoreMenu by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }
    var selectedField by remember { mutableStateOf<ContactFieldDisplay?>(null) }
    var showFieldDeleteDialog by remember { mutableStateOf(false) }
    var showFieldDetailDialog by remember { mutableStateOf(false) }
    var showPlatformDetailDialog by remember { mutableStateOf(false) }
    var selectedPlatformDetail by remember { mutableStateOf<Pair<String, PlatformEntry>?>(null) }
    var showAddPlatformDialog by remember { mutableStateOf(false) }
    var showEditPlatformDialog by remember { mutableStateOf(false) }
    var editingPlatform by remember { mutableStateOf<Pair<String, PlatformEntry>?>(null) }
    var showPlatformContextMenu by remember { mutableStateOf(false) }
    var selectedPlatform by remember { mutableStateOf<Pair<String, PlatformEntry>?>(null) }
    var showSyncOptionsSheet by remember { mutableStateOf(false) }
    var syncPlatformInfo by remember { mutableStateOf<Pair<String, PlatformEntry>?>(null) }
    var showEditFieldDialog by remember { mutableStateOf(false) }
    var editFieldValue by remember { mutableStateOf("") }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showContactPicker by remember { mutableStateOf(false) }
    var selectedExistingContact by remember { mutableStateOf<Contact?>(null) }
    var isSettingAvatar by remember { mutableStateOf(false) }
    var avatarVersion by remember { mutableIntStateOf(0) }
    var showCropDialog by remember { mutableStateOf(false) }
    var cropSourceUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ContactDetailEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is ContactDetailEvent.RefreshData -> onRefreshData?.invoke()
            }
        }
    }

    val pickAvatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) { cropSourceUri = uri; showCropDialog = true }
    }

    val onCropConfirm: (Bitmap) -> Unit = { croppedBitmap ->
        scope.launch {
            try {
                val avatarFile = Methods.saveBitmapAsAvatar(context, croppedBitmap, "contact_${contactId}_avatar.webp")
                viewModel.applyAvatarUpdate(contactId, avatarFile.absolutePath)
                avatarVersion++
                Log.d("ContactDetailPage", "Avatar cropped and saved: ${avatarFile.absolutePath}")
            } catch (e: Exception) {
                Log.e("ContactDetailPage", "设置头像失败", e)
                Toast.makeText(context, "设置头像失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val scanResultsFlow = remember(contactId) { viewModel.collectionRepository.getScanResultsByContact(contactId) }
    val scanResults by scanResultsFlow.collectAsState(initial = emptyList())
    val contactCollectionIds by remember(scanResults) {
        mutableStateOf(scanResults.map { it.collectionId }.distinct().toSet())
    }

    val collections by viewModel.collectionRepository.getAllCollections().collectAsState(initial = emptyList())
    val collectionNameMap by remember(collections) {
        mutableStateOf(collections.associate { it.id to it.name })
    }

    var showScanResultDetailDialog by remember { mutableStateOf(false) }
    var clickedScanResult by remember { mutableStateOf<ScanResult?>(null) }

    // 添加到名片夹弹窗
    var showCollectionPicker by remember { mutableStateOf(false) }

    // 样式详情对话框
    var selectedScanResult by remember { mutableStateOf<ScanResult?>(null) }
    // 样式上下文菜单
    var showStyleContextMenu by remember { mutableStateOf(false) }

    // 系统返回键：FloatingToolbar 显示时关闭 bar
    BackHandler(enabled = showContextMenu || showStyleContextMenu || showPlatformContextMenu) {
        showContextMenu = false
        selectedField = null
        showStyleContextMenu = false
        showPlatformContextMenu = false
        selectedPlatform = null
    }

    LaunchedEffect(contactId) {
        viewModel.loadContact(contactId)
    }

    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val contact = contactWithFields?.contact
    val fields = contactWithFields?.fieldValues ?: emptyList()

    // 头像位图（异步加载）：本地 avatarPath 优先，其次远程 avatarUrl
    var avatarBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val localAvatarPath = contact?.avatarPath
    val remoteAvatarUrl = contact?.avatarUrl
    LaunchedEffect(localAvatarPath, remoteAvatarUrl, avatarVersion) {
        avatarBitmap = if (!localAvatarPath.isNullOrBlank()) {
            Methods.loadAvatarBitmap(localAvatarPath)
        } else if (!remoteAvatarUrl.isNullOrBlank()) {
            HttpUtil.downloadBitmap(remoteAvatarUrl, timeoutMs = 5000)
        } else null
    }

    // 按系统字段/自定义字段分组，平台字段不再从 ContactFieldValue 中显示
    val systemFields = remember(fields) { fields.filter { it.fieldKey != null && it.fieldKey !in PLATFORM_FIELD_KEYS } }
    val customFields = remember(fields) { fields.filter { it.fieldKey == null } }

    // 社交平台列表（从 contact_platforms 表加载）
    val platformFields = remember(platformData) {
        platformData.map { cp ->
            cp.platformKey to PlatformEntry(
                value = cp.value,
                displayName = cp.displayName,
                jumpLink = cp.jumpLink,
                originalLink = cp.originalLink,
                avatarUrl = cp.avatarUrl
            )
        }.filter { it.second.jumpLink.isNotBlank() || !it.second.value.isNullOrBlank() }
    }
    // 分享联系方式文本
    fun buildShareText(): String = buildContactShareText(contact, fields)

    // 更多菜单选项
    val moreMenuItems = remember { listOf("附加到已有联系人", "分享联系方式") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "",
                scrollBehavior = topAppBarScrollBehavior,
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
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "更多"
                            )
                        }
                        // 右上角下拉菜单：锚点为 Box（IconButton 位置），紧贴按钮弹出
                        OverlayListPopup(
                            show = showMoreMenu,
                            alignment = PopupPositionProvider.Align.TopEnd,
                            popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                            onDismissRequest = { showMoreMenu = false },
                        ) {
                            ListPopupColumn {
                                moreMenuItems.forEachIndexed { index, text ->
                                    DropdownImpl(
                                        text = text,
                                        optionSize = moreMenuItems.size,
                                        isSelected = false,
                                        index = index,
                                        onSelectedIndexChange = { selectedIdx ->
                                            showMoreMenu = false
                                            when (selectedIdx) {
                                                0 -> showContactPicker = true
                                                1 -> {
                                                    val shareText = buildShareText()
                                                    if (shareText.isNotBlank()) {
                                                        val intent = Intent().apply {
                                                            action = Intent.ACTION_SEND
                                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                                            type = "text/plain"
                                                        }
                                                        context.startActivity(Intent.createChooser(intent, "分享联系方式"))
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
            )
        },
        floatingToolbar = {
            ContactDetailFloatingToolbars(
                showFieldToolbar = showContextMenu && selectedField != null,
                selectedField = selectedField,
                onFieldCopy = {
                    Methods.copyToClipboard(context, selectedField!!.fieldName, selectedField!!.value)
                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                    showContextMenu = false
                    selectedField = null
                },
                onFieldEdit = {
                    editFieldValue = selectedField!!.value
                    showEditFieldDialog = true
                    showContextMenu = false
                },
                onFieldSync = {
                    isSettingAvatar = true
                    val field = selectedField
                    if (field == null) { isSettingAvatar = false; return@ContactDetailFloatingToolbars }
                    val fieldKey = field.fieldKey!!
                    val contactType = FIELD_DEF_MAP[fieldKey]?.contactType
                    val adapter = contactType?.let { PlatformAdapterRegistry.getAdapter(it) }
                    scope.launch {
                        try {
                            val resolved = viewModel.resolvePlatformForField(fieldKey, field.value)
                            if (resolved == null) {
                                isSettingAvatar = false
                                Toast.makeText(context, "无法获取该平台信息", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            var avatarPath: String? = null
                            if (resolved.avatarUrl != null) {
                                val headers = if (resolved.avatarUrl.contains("hdslb.com") || resolved.avatarUrl.contains("bilibili.com"))
                                    BILIBILI_HEADERS else null
                                avatarPath = withContext(Dispatchers.IO) {
                                    downloadAndSaveAvatar(resolved.avatarUrl!!, context, contactId, headers)
                                }
                            }
                            viewModel.applySyncResult(contactId, resolved.name, avatarPath)
                            avatarVersion++
                            isSettingAvatar = false
                        } catch (e: Exception) {
                            Log.e("ContactDetailPage", "同步失败", e)
                            isSettingAvatar = false
                            Toast.makeText(context, "同步失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                    showContextMenu = false
                    selectedField = null
                },
                onFieldDelete = {
                    showContextMenu = false
                    showFieldDeleteDialog = true
                },
                showStyleToolbar = showStyleContextMenu && selectedScanResult != null,
                onStyleDelete = {
                    val scanResultId = selectedScanResult?.id
                    showStyleContextMenu = false
                    selectedScanResult = null
                    if (scanResultId != null) {
                        viewModel.deleteScanResult(scanResultId)
                        viewModel.reloadContact(contactId)
                    }
                },
                showPlatformToolbar = showPlatformContextMenu && selectedPlatform != null,
                selectedPlatform = selectedPlatform,
                onPlatformCopy = {
                    val (fieldKey, pEntry) = selectedPlatform!!
                    val pDisplayName = FIELD_DEF_MAP[fieldKey]?.displayName ?: fieldKey
                    val copyText = pEntry.value ?: pEntry.jumpLink
                    Methods.copyToClipboard(context, pDisplayName, copyText)
                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                    showPlatformContextMenu = false
                    selectedPlatform = null
                },
                onPlatformEdit = {
                    editingPlatform = selectedPlatform
                    showEditPlatformDialog = true
                    showPlatformContextMenu = false
                    selectedPlatform = null
                },
                onPlatformSync = {
                    syncPlatformInfo = selectedPlatform
                    showPlatformContextMenu = false
                    selectedPlatform = null
                    showSyncOptionsSheet = true
                    Log.d("ContactDetailPage", "Sync info requested for platform: ${selectedPlatform?.first}")
                },
                onPlatformDelete = {
                    showPlatformContextMenu = false
                    val deletedEntry = selectedPlatform
                    val platformKey = deletedEntry?.first ?: return@ContactDetailFloatingToolbars
                    selectedPlatform = null
                    scope.launch(Dispatchers.IO) {
                        viewModel.removePlatform(contactId, platformKey)
                        val freshContact = viewModel.getContactById(contactId) ?: return@launch
                        if (!freshContact.avatarPath.isNullOrBlank()) {
                            val deletedAvatarUrl = deletedEntry.second.avatarUrl
                            val currentAvatarMatchesDeleted = deletedAvatarUrl != null &&
                                freshContact.avatarUrl == deletedAvatarUrl
                            if (currentAvatarMatchesDeleted) {
                                val remainingPlatforms = viewModel.getContactPlatforms(contactId)
                                val fallbackEntry = remainingPlatforms.firstOrNull {
                                    !it.avatarUrl.isNullOrBlank()
                                }
                                if (fallbackEntry != null) {
                                    val fallbackUrl = fallbackEntry.avatarUrl!!
                                    val headers = if (fallbackUrl.contains("hdslb.com") || fallbackUrl.contains("bilibili.com"))
                                        BILIBILI_HEADERS else null
                                    val newAvatarPath = downloadAndSaveAvatar(fallbackUrl, context, contactId, headers)
                                    if (newAvatarPath != null) {
                                        viewModel.updateContact(freshContact.copy(
                                            avatarPath = newAvatarPath,
                                            avatarUrl = fallbackUrl,
                                            updateTime = System.currentTimeMillis()
                                        ))
                                    } else {
                                        Methods.deleteAvatarFile(freshContact.avatarPath)
                                        viewModel.updateContact(freshContact.copy(
                                            avatarPath = null,
                                            avatarUrl = null,
                                            updateTime = System.currentTimeMillis()
                                        ))
                                    }
                                } else {
                                    Methods.deleteAvatarFile(freshContact.avatarPath)
                                    viewModel.updateContact(freshContact.copy(
                                        avatarPath = null,
                                        avatarUrl = null,
                                        updateTime = System.currentTimeMillis()
                                    ))
                                }
                            }
                        }
                        viewModel.reloadContact(contactId)
                    }
                    onRefreshData?.invoke()
                },
            )
        },
        floatingToolbarPosition = ToolbarPosition.BottomCenter,
    ) { paddingValues ->
        ContactDetailPageContent(
            isLoading = isLoading,
            contact = contact,
            contentModifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
            paddingValues = paddingValues,
            avatarBitmap = avatarBitmap,
            systemFields = systemFields,
            customFields = customFields,
            platformFields = platformFields,
            scanResults = scanResults,
            collectionNameMap = collectionNameMap,
            contactCollectionIds = contactCollectionIds,
            onAvatarClick = {
                pickAvatarLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onEditNameClick = {
                Log.d("ContactDetailPage", "Edit name clicked for contact ${contact?.id}")
                showEditNameDialog = true
            },
            onFieldClick = { field ->
                selectedField = field
                showFieldDetailDialog = true
            },
            onFieldLongPress = { field ->
                selectedField = field
                showStyleContextMenu = false
                showPlatformContextMenu = false
                showContextMenu = true
            },
            onPlatformClick = { fieldKey, entry ->
                selectedPlatformDetail = fieldKey to entry
                showPlatformDetailDialog = true
            },
            onPlatformLongClick = { fieldKey, entry ->
                selectedPlatform = fieldKey to entry
                showContextMenu = false
                showStyleContextMenu = false
                showPlatformContextMenu = true
            },
            onScanResultClick = { scanResult ->
                clickedScanResult = scanResult
                showScanResultDetailDialog = true
                Log.d("ContactDetail", "scanResult clicked: id=${scanResult.id}, collectionId=${scanResult.collectionId}")
            },
            onScanResultLongClick = { scanResult ->
                selectedField = null
                selectedScanResult = scanResult
                showContextMenu = false
                showStyleContextMenu = true
            },
            onAddPlatformClick = { showAddPlatformDialog = true },
            onAddToCollectionClick = { showCollectionPicker = true },
        )
    }
    ContactDetailPageDialogs(
        contactId = contactId,
        viewModel = viewModel,
        contact = contact,
        contactWithFields = contactWithFields,
        platformData = platformData,
        contactCollectionIds = contactCollectionIds,
        scanResults = scanResults,
        collectionNameMap = collectionNameMap,
        // 对话框显示状态
        showFieldDeleteDialog = showFieldDeleteDialog,
        showEditFieldDialog = showEditFieldDialog,
        showEditNameDialog = showEditNameDialog,
        showFieldDetailDialog = showFieldDetailDialog,
        showPlatformDetailDialog = showPlatformDetailDialog,
        showAddPlatformDialog = showAddPlatformDialog,
        showEditPlatformDialog = showEditPlatformDialog,
        showCollectionPicker = showCollectionPicker,
        showContactPicker = showContactPicker,
        showCropDialog = showCropDialog,
        showSyncOptionsSheet = showSyncOptionsSheet,
        showScanResultDetailDialog = showScanResultDetailDialog,
        // 对话框数据
        selectedField = selectedField,
        editFieldValue = editFieldValue,
        selectedPlatformDetail = selectedPlatformDetail,
        editingPlatform = editingPlatform,
        cropSourceUri = cropSourceUri,
        syncPlatformInfo = syncPlatformInfo,
        clickedScanResult = clickedScanResult,
        selectedExistingContact = selectedExistingContact,
        // 回调
        onDismissFieldDelete = { showFieldDeleteDialog = false; selectedField = null },
        onDeleteField = { field ->
            viewModel.deleteFieldValue(contactId, field.valueId)
            viewModel.reloadContact(contactId)
        },
        onEditFieldValueChange = { editFieldValue = it },
        onDismissEditField = { showEditFieldDialog = false },
        onSaveEditField = { newValue ->
            viewModel.updateFieldValue(contactId, selectedField!!.valueId, newValue)
            viewModel.reloadContact(contactId)
            selectedField = null
        },
        onDismissEditName = { showEditNameDialog = false },
        onSaveEditName = { newName ->
            Log.d("ContactDetailPage", "Saving new name: $newName for contact ${contact?.id}")
            viewModel.updateName(contactId, newName)
        },
        onDismissFieldDetail = { showFieldDetailDialog = false; selectedField = null },
        onDismissPlatformDetail = { showPlatformDetailDialog = false; selectedPlatformDetail = null },
        onDismissAddPlatform = { showAddPlatformDialog = false },
        onConfirmAddPlatform = { fieldKey, entry ->
            showAddPlatformDialog = false
            scope.launch(Dispatchers.IO) {
                viewModel.addOrUpdatePlatform(contactId, fieldKey, entry)
                val freshContact = viewModel.getContactById(contactId)
                val contactType = FIELD_DEF_MAP[fieldKey]?.contactType
                val adapter = contactType?.let { PlatformAdapterRegistry.getAdapter(it) }
                val needsAvatar = freshContact?.avatarPath.isNullOrBlank() && freshContact?.avatarUrl.isNullOrBlank()
                // [修复防御]: 原条件 `adapter?.canSync == true && needsAvatar` 会让已有头像的联系人
                // 跳过整段同步——导致确定时不拿信息。现确保只要平台支持同步，
                // 都会去解析昵称/头像并写回 PlatformEntry；下载/写联系人头像仅在需要时执行。
                if (adapter?.canSync == true) {
                    Log.d("ContactDetailPage", "Auto-sync from new platform $fieldKey (needsAvatar=$needsAvatar)")
                    try {
                        val content = entry.jumpLink.ifBlank { entry.value ?: "" }
                        val resolveResult = withContext(Dispatchers.IO) {
                            ContactNetworkResolver.getResultInfo(content, mutableMapOf(), type = contactType)
                        }
                        val resolvedAvatar = resolveResult?.avatarUrl?.takeIf { it.isNotBlank() }
                        val resolvedName = resolveResult?.nickname?.takeIf { it.isNotBlank() && it != "未知" }
                        if (resolvedName != null || resolvedAvatar != null) {
                            viewModel.addOrUpdatePlatform(contactId, fieldKey, entry.copy(
                                displayName = resolvedName ?: entry.displayName,
                                avatarUrl = resolvedAvatar ?: entry.avatarUrl
                            ))
                        }
                        if (needsAvatar && resolvedAvatar != null) {
                            val newAvatarPath = downloadAndSaveAvatar(resolvedAvatar, context, contactId)
                            if (newAvatarPath != null) {
                                val latestContact = viewModel.getContactById(contactId) ?: freshContact
                                viewModel.updateContact(latestContact!!.copy(
                                    avatarPath = newAvatarPath,
                                    updateTime = System.currentTimeMillis()
                                ))
                                Log.d("ContactDetailPage", "Auto-sync avatar success from $fieldKey")
                            }
                        } else if (resolvedName != null) {
                            val latestContact = viewModel.getContactById(contactId) ?: freshContact
                            viewModel.updateContact(latestContact!!.copy(
                                updateTime = System.currentTimeMillis()
                            ))
                        }
                    } catch (e: Exception) {
                        Log.e("ContactDetailPage", "Auto-sync avatar failed from $fieldKey", e)
                    }
                }
                viewModel.reloadContact(contactId)
            }
            onRefreshData?.invoke()
        },
        onDismissEditPlatform = { showEditPlatformDialog = false; editingPlatform = null },
        onConfirmEditPlatform = { fieldKey, newEntry ->
            showEditPlatformDialog = false
            editingPlatform = null
            viewModel.addOrUpdatePlatform(contactId, fieldKey, newEntry)
            viewModel.reloadContact(contactId)
            onRefreshData?.invoke()
        },
        onDismissCollectionPicker = { showCollectionPicker = false },
        onConfirmCollectionPicker = { addedIds, removedIds ->
            viewModel.updateCollections(contactId, addedIds.toList(), removedIds.toList())
            onRefreshData?.invoke()
            showCollectionPicker = false
            val msg = when {
                addedIds.isNotEmpty() && removedIds.isNotEmpty() -> "名片夹已更新"
                addedIds.isNotEmpty() -> "已添加至 ${addedIds.size} 个名片夹"
                removedIds.isNotEmpty() -> "已从 ${removedIds.size} 个名片夹移除"
                else -> null
            }
            if (msg != null) {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        },
        onDismissContactPicker = { showContactPicker = false },
        onContactSelected = { targetContact ->
            selectedExistingContact = targetContact
            showContactPicker = false
        },
        onDismissAttachField = { selectedExistingContact = null },
        onConfirmAttachField = { selectedFieldKeys, selectedCustomFieldIds ->
            val sourceData = contactWithFields
            val existing = selectedExistingContact
            if (sourceData == null || existing == null) return@ContactDetailPageDialogs
            viewModel.attachToExisting(
                sourceContact = sourceData.contact,
                sourceFields = sourceData.fieldValues,
                existingContact = existing,
                selectedFieldKeys = selectedFieldKeys,
                selectedCustomFieldIds = selectedCustomFieldIds
            )
            selectedExistingContact = null
            viewModel.reloadContact(contactId)
            Toast.makeText(context, "已成功附加到 ${existing.name}", Toast.LENGTH_SHORT).show()
        },
        onCropConfirm = onCropConfirm,
        onDismissCrop = { cropSourceUri = null },
        onDismissSync = { showSyncOptionsSheet = false; syncPlatformInfo = null },
        onConfirmSync = { syncName, syncAvatar ->
            val platformInfo = syncPlatformInfo
            showSyncOptionsSheet = false
            syncPlatformInfo = null
            if (platformInfo == null) {
                Log.w("ContactDetailPage", "onConfirmSync: syncPlatformInfo is null, aborting")
                return@ContactDetailPageDialogs
            }
            scope.launch {
                try {
                    val (pName, pEntry) = platformInfo
                    val resolveResult = withContext(Dispatchers.IO) {
                        try {
                            val content = pEntry.jumpLink.ifBlank { pEntry.value ?: "" }
                            val ct = FIELD_DEF_MAP[pName]?.contactType
                            ContactNetworkResolver.getResultInfo(content, mutableMapOf(), type = ct)
                        } catch (e: Exception) {
                            Log.w("ContactDetailPage", "平台信息解析失败", e)
                            null
                        }
                    }
                    val resolvedName = resolveResult?.nickname?.takeIf { it.isNotBlank() && it != "未知" }
                    val resolvedAvatar = resolveResult?.avatarUrl?.takeIf { it.isNotBlank() }
                    if (resolvedName != null || resolvedAvatar != null) {
                        viewModel.addOrUpdatePlatform(contactId, pName, pEntry.copy(
                            displayName = resolvedName ?: pEntry.displayName,
                            avatarUrl = resolvedAvatar ?: pEntry.avatarUrl
                        ))
                    }
                    val freshContact = viewModel.getContactById(contactId) ?: return@launch
                    var newName: String? = null
                    if (syncName) {
                        newName = resolvedName ?: pEntry.displayName?.takeIf { it.isNotBlank() }
                    }
                    var avatarPath: String? = null
                    if (syncAvatar) {
                        val avatarToUse = resolvedAvatar ?: pEntry.avatarUrl
                        if (!avatarToUse.isNullOrBlank()) {
                            isSettingAvatar = true
                            val headers = if (avatarToUse.contains("hdslb.com") || avatarToUse.contains("bilibili.com"))
                                BILIBILI_HEADERS else null
                            avatarPath = withContext(Dispatchers.IO) {
                                downloadAndSaveAvatar(avatarToUse, context, contactId, headers)
                            }
                            isSettingAvatar = false
                        }
                    }
                    var updated = freshContact
                    if (newName != null) updated = updated.copy(name = newName)
                    if (avatarPath != null) updated = updated.copy(avatarPath = avatarPath)
                    if (newName != null || avatarPath != null) {
                        updated = updated.copy(updateTime = System.currentTimeMillis())
                        viewModel.updateContact(updated)
                        avatarVersion++
                        Toast.makeText(context, "同步成功", Toast.LENGTH_SHORT).show()
                        Log.d("ContactDetailPage", "Sync success for $pName: name=${updated.name}")
                    } else {
                        Toast.makeText(context, "未获取到可同步的信息", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("ContactDetailPage", "同步失败", e)
                    isSettingAvatar = false
                    Toast.makeText(context, "同步失败", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onDismissScanDetail = { showScanResultDetailDialog = false; clickedScanResult = null },
    )
}

private fun buildContactShareText(contact: Contact?, fields: List<ContactFieldDisplay>): String {
    if (contact == null) return ""
    val sb = StringBuilder()
    sb.appendLine(contact.name)
    if (!contact.note.isNullOrBlank()) {
        sb.appendLine("备注：${contact.note}")
    }
    fields.forEach { field ->
        sb.appendLine("${field.fieldName}：${field.value}")
    }
    return sb.toString().trim()
}
