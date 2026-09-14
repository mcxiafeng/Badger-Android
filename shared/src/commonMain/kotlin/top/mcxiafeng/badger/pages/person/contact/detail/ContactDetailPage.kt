package top.mcxiafeng.badger.pages.person.contact.detail

import top.mcxiafeng.badger.platform.SystemShare
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.model.PersonFieldDisplay
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity as ContactPlatform
import top.mcxiafeng.badger.data.model.PersonWithFields
import top.mcxiafeng.badger.data.model.PlatformEntry
import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ocr.PLATFORM_FIELD_KEYS
import top.mcxiafeng.badger.utils.BILIBILI_HEADERS
import top.mcxiafeng.badger.pages.person.contact.UserProfileDetailPage
import top.mcxiafeng.badger.platform.ImageFiles
import top.mcxiafeng.badger.platform.PlatformImage
import top.mcxiafeng.badger.platform.downloadImageAsPng
import top.mcxiafeng.badger.platform.loadOrientedImage
import top.mcxiafeng.badger.platform.rememberImagePickerLauncher
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
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Star
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.utils.SafeLog
import top.mcxiafeng.badger.platform.showToast
import top.mcxiafeng.badger.platform.BackHandler
import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.mcxiafeng.badger.shared.util.nowMs

@Composable
fun ContactDetailPage(
    contactId: Long,
    onBack: () -> Unit,
    onRefreshData: (() -> Unit)? = null,
    onOpenScannerForImport: (() -> Unit)? = null,
    embedded: Boolean = false,
) {
    
    if (contactId == -1L) {
        UserProfileDetailPage(onBack = onBack, onRefreshData = onRefreshData, onOpenScannerForImport = onOpenScannerForImport)
        return
    }

        val viewModel: ContactDetailViewModel = koinViewModel()
    val scope = rememberCoroutineScope()
    
    val contactWithFields by viewModel.contactWithFields.collectAsStateWithLifecycle()
    val platformData by viewModel.platformData.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    
    val aiTagCandidates by viewModel.aiTagCandidates.collectAsStateWithLifecycle()
    val aiTagLoading by viewModel.aiTagLoading.collectAsStateWithLifecycle()
    val aiTagError by viewModel.aiTagError.collectAsStateWithLifecycle()

    var showMoreMenu by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }
    var selectedField by remember { mutableStateOf<PersonFieldDisplay?>(null) }
    var showFieldDeleteDialog by remember { mutableStateOf(false) }
    var showFieldDetailDialog by remember { mutableStateOf(false) }
    var showPlatformDetailDialog by remember { mutableStateOf(false) }
    var selectedPlatformDetail by remember { mutableStateOf<Pair<String, PlatformEntry>?>(null) }
    var showAddPlatformDialog by remember { mutableStateOf(false) }
    var showEditPlatformDialog by remember { mutableStateOf(false) }
    var editingPlatform by remember { mutableStateOf<Pair<String, PlatformEntry>?>(null) }
    var showBatchImportDialog by remember { mutableStateOf(false) }
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
    var cropSourceImage by remember { mutableStateOf<PlatformImage?>(null) }

    
    var basicInfoEditField by remember { mutableStateOf<String?>(null) }
    var basicInfoEditCurrent by remember { mutableStateOf<String?>(null) }

    
    var currentCountryName by remember { mutableStateOf<String?>(null) }
    var currentCountryExternalId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ContactDetailEvent.ShowToast -> showToast(event.message)
                is ContactDetailEvent.RefreshData -> onRefreshData?.invoke()
            }
        }
    }

    val pickAvatarLauncher = rememberImagePickerLauncher { bytes ->
        if (bytes != null) {
            scope.launch(BadgerDispatchers.io) {
                val image = loadOrientedImage(bytes)
                if (image != null) { cropSourceImage = image; showCropDialog = true }
            }
        }
    }

    val onCropConfirm: (ByteArray) -> Unit = { croppedBytes ->
        scope.launch {
            try {
                val savedPath = ImageFiles.saveAvatarImage(croppedBytes, "contact_${contactId}_avatar.webp")
                viewModel.applyAvatarUpdate(contactId, savedPath ?: "")
                if (savedPath != null) avatarVersion++
                BadgerLog.d("ContactDetailPage", "Avatar cropped and saved: $savedPath")
            } catch (e: Exception) {
                BadgerLog.e("ContactDetailPage", "设置头像失败", e)
                showToast("设置头像失败")
            }
        }
    }

    
    var showCollectionPicker by remember { mutableStateOf(false) }

    
    var showBioEdit by remember { mutableStateOf(false) }
    var showTagPicker by remember { mutableStateOf(false) }
    var showTagManager by remember { mutableStateOf(false) }
    
    var showAiTagPreview by remember { mutableStateOf(false) }

    
    var showAvatarPreview by remember { mutableStateOf(false) }

    
    BackHandler(enabled = showContextMenu || showPlatformContextMenu) {
        showContextMenu = false
        selectedField = null
        showPlatformContextMenu = false
        selectedPlatform = null
    }

    LaunchedEffect(contactId) {
        viewModel.loadContact(contactId)
    }

    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val contact = contactWithFields?.contact
    val fields = contactWithFields?.fieldValues ?: emptyList()

    
    
    LaunchedEffect(fields) {
        val countryValue = fields.firstOrNull { it.fieldKey == "country" }?.value?.takeIf { s -> s.isNotBlank() }
        if (countryValue != null && currentCountryName != countryValue) {
            currentCountryName = countryValue
            
            currentCountryExternalId = null
        }
    }

    
    
    
    var avatarImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val localAvatarPath = contact?.avatarPath
    val remoteAvatarUrl = contact?.avatarUrl
    LaunchedEffect(localAvatarPath, remoteAvatarUrl, avatarVersion) {
        suspend fun loadAvatarFrom(source: String?): ImageBitmap? {
            if (source.isNullOrBlank()) return null
            val isRemote = source.startsWith("http://") || source.startsWith("https://")
            val bytes = if (isRemote) {
                downloadImageAsPng(source)
            } else {
                ImageFiles.loadImageBytes(source)
            }
            val bitmap = bytes?.let { b -> runCatching { b.decodeToImageBitmap() }.getOrNull() }
            BadgerLog.d(
                "ContactDetailPage",
                "loadAvatarFrom: ${if (isRemote) "remote" else "local"} source=${SafeLog.url(source)} → ${if (bitmap != null) "ok" else "empty"}",
            )
            return bitmap
        }
        avatarImageBitmap = loadAvatarFrom(localAvatarPath) ?: loadAvatarFrom(remoteAvatarUrl)
    }

    
    val systemFields = remember(fields) { fields.filter { it.fieldKey != null && it.fieldKey !in PLATFORM_FIELD_KEYS } }
    val customFields = remember(fields) { fields.filter { it.fieldKey == null } }

    
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
    
    fun buildShareText(): String = buildContactShareText(contact, fields)

    
    val moreMenuItems = remember { listOf("附加到已有联系人", "分享联系方式") }

    
    val contactCollectionIdsList by remember(contactId) {
        viewModel.collectionRepository.getContactCollectionIds(contactId)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val contactCollectionIds by remember(contactCollectionIdsList) {
        mutableStateOf(contactCollectionIdsList.toSet())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "",
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    if (!embedded) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Lucide.ArrowLeft,
                                contentDescription = "返回"
                            )
                        }
                    }
                },
                actions = {
                    
                    
                    IconButton(onClick = { showCollectionPicker = true }) {
                        Icon(
                            imageVector = Lucide.Star,
                            contentDescription = "添加到名片夹",
                            tint = if (contactCollectionIds.isNotEmpty())
                                MiuixTheme.colorScheme.primary
                            else
                                MiuixTheme.colorScheme.onSurface,
                        )
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(
                                imageVector = Lucide.EllipsisVertical,
                                contentDescription = "更多"
                            )
                        }
                        
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
                                                        SystemShare.shareText("分享联系方式", shareText)
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
                    selectedField?.let { f ->
                        Methods.copyToClipboard(f.fieldName, f.value)
                        showToast("已复制")
                    }
                    showContextMenu = false
                    selectedField = null
                },
                onFieldEdit = {
                    selectedField?.let { f -> editFieldValue = f.value }
                    if (selectedField != null) showEditFieldDialog = true
                    showContextMenu = false
                },
                onFieldSync = {
                    isSettingAvatar = true
                    val field = selectedField
                    if (field == null) { isSettingAvatar = false; return@ContactDetailFloatingToolbars }
                    val fieldKey = field.fieldKey!!
                    
                    scope.launch {
                        try {
                            val resolved = viewModel.resolvePlatformForField(fieldKey, field.value)
                            if (resolved == null) {
                                isSettingAvatar = false
                                showToast("无法获取该平台信息")
                                return@launch
                            }
                            var avatarPath: String? = null
                            if (resolved.avatarUrl != null) {
                                val headers = if (resolved.avatarUrl.contains("hdslb.com") || resolved.avatarUrl.contains("bilibili.com"))
                                    BILIBILI_HEADERS else emptyMap()
                                avatarPath = downloadAndSaveAvatar(resolved.avatarUrl!!, contactId, headers)
                            }
                            viewModel.applySyncResult(contactId, resolved.name, avatarPath, resolved.avatarUrl)
                            avatarVersion++
                            isSettingAvatar = false
                        } catch (e: Exception) {
                            BadgerLog.e("ContactDetailPage", "同步失败", e)
                            isSettingAvatar = false
                            showToast("同步失败")
                        }
                    }
                    showContextMenu = false
                    selectedField = null
                },
                onFieldDelete = {
                    showContextMenu = false
                    showFieldDeleteDialog = true
                },
                showPlatformToolbar = showPlatformContextMenu && selectedPlatform != null,
                selectedPlatform = selectedPlatform,
                onPlatformCopy = {
                    selectedPlatform?.let { (fieldKey, pEntry) ->
                        val pDisplayName = FIELD_DEF_MAP[fieldKey]?.displayName ?: fieldKey
                        val copyText = pEntry.value ?: pEntry.jumpLink
                        Methods.copyToClipboard(pDisplayName, copyText)
                        showToast("已复制")
                    }
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
                    BadgerLog.d("ContactDetailPage", "Sync info requested for platform: ${selectedPlatform?.first}")
                },
                onPlatformDelete = {
                    showPlatformContextMenu = false
                    val deletedEntry = selectedPlatform
                    val platformKey = deletedEntry?.first ?: return@ContactDetailFloatingToolbars
                    selectedPlatform = null
                    scope.launch(BadgerDispatchers.io) {
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
                                        BILIBILI_HEADERS else emptyMap()
                                    val newAvatarPath = downloadAndSaveAvatar(fallbackUrl, contactId, headers)
                                    if (newAvatarPath != null) {
                                        viewModel.updateContact(freshContact.copy(
                                            avatarPath = newAvatarPath,
                                            avatarUrl = fallbackUrl,
                                            updateTime = nowMs()
                                        ))
                                    } else {
                                        ImageFiles.deleteImageFile(freshContact.avatarPath)
                                        viewModel.updateContact(freshContact.copy(
                                            avatarPath = null,
                                            avatarUrl = null,
                                            updateTime = nowMs()
                                        ))
                                    }
                                } else {
                                    ImageFiles.deleteImageFile(freshContact.avatarPath)
                                    viewModel.updateContact(freshContact.copy(
                                        avatarPath = null,
                                        avatarUrl = null,
                                        updateTime = nowMs()
                                    ))
                                }
                            }
                        }
                        viewModel.reloadContact(contactId)
                        avatarVersion++
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
            avatarImageBitmap = avatarImageBitmap,
            systemFields = systemFields,
            customFields = customFields,
            platformFields = platformFields,
            bio = contact?.bio,
            tags = tags,
            onAvatarClick = {
                
                if (avatarImageBitmap != null) showAvatarPreview = true
            },
            onEditNameClick = {
                BadgerLog.d("ContactDetailPage", "Edit name clicked for contact ${contact?.id}")
                showEditNameDialog = true
            },
            onFieldClick = { field ->
                selectedField = field
                showFieldDetailDialog = true
            },
            onFieldLongPress = { field ->
                selectedField = field
                showPlatformContextMenu = false
                showContextMenu = true
            },
            onPlatformClick = { fieldKey, entry ->
                selectedPlatformDetail = fieldKey to entry
                showPlatformDetailDialog = true
            },
            onPlatformLongPress = { fieldKey, entry ->
                selectedPlatform = fieldKey to entry
                showContextMenu = false
                showPlatformContextMenu = true
            },
            onAddPlatformClick = { showAddPlatformDialog = true },
            onBatchImportClick = { showBatchImportDialog = true },
            onBioClick = { showBioEdit = true },
            onTagsClick = { showTagPicker = true },
            onAiTagsClick = lambda@{
                
                if (aiTagLoading) {
                    showToast("AI 正在生成中…")
                    return@lambda
                }
                
                val bio = contact?.bio
                if (bio.isNullOrBlank()) {
                    showToast("请先填写个人介绍,AI 才能更准确推荐")
                    showBioEdit = true
                } else {
                    showAiTagPreview = true
                    viewModel.generateAiTags(contactId)
                }
            },
            onBasicInfoCellClick = { fieldKey, currentValue ->
                
                if (fieldKey == "region" && currentCountryName.isNullOrBlank()) {
                    showToast("请先选择国家")
                    basicInfoEditField = "country"
                    basicInfoEditCurrent = fields.firstOrNull { it.fieldKey == "country" }?.value
                } else {
                    basicInfoEditField = fieldKey
                    basicInfoEditCurrent = currentValue
                }
            },
        )
    }

    ContactDetailDialogHost(
        contactId = contactId,
        viewModel = viewModel,
        contact = contact,
        contactWithFields = contactWithFields,
        platformData = platformData,
        contactCollectionIds = contactCollectionIds,
        tags = tags,
        aiTagCandidates = aiTagCandidates,
        aiTagLoading = aiTagLoading,
        aiTagError = aiTagError,
        basicInfoEditField = basicInfoEditField,
        basicInfoEditCurrent = basicInfoEditCurrent,
        currentCountryName = currentCountryName,
        currentCountryExternalId = currentCountryExternalId,
        onBasicInfoEditFieldChange = { basicInfoEditField = it },
        onCurrentCountryNameChange = { currentCountryName = it },
        onCurrentCountryExternalIdChange = { currentCountryExternalId = it },
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
        showBatchImportDialog = showBatchImportDialog,
        showBioEdit = showBioEdit,
        showTagPicker = showTagPicker,
        showTagManager = showTagManager,
        showAiTagPreview = showAiTagPreview,
        showAvatarPreview = showAvatarPreview,
        selectedField = selectedField,
        editFieldValue = editFieldValue,
        selectedPlatformDetail = selectedPlatformDetail,
        editingPlatform = editingPlatform,
        cropSourceImage = cropSourceImage,
        syncPlatformInfo = syncPlatformInfo,
        selectedExistingContact = selectedExistingContact,
        avatarImageBitmap = avatarImageBitmap,
        avatarVersion = avatarVersion,
        isSettingAvatar = isSettingAvatar,
        onShowFieldDeleteDialogChange = { showFieldDeleteDialog = it },
        onShowEditFieldDialogChange = { showEditFieldDialog = it },
        onShowEditNameDialogChange = { showEditNameDialog = it },
        onShowFieldDetailDialogChange = { showFieldDetailDialog = it },
        onShowPlatformDetailDialogChange = { showPlatformDetailDialog = it },
        onShowAddPlatformDialogChange = { showAddPlatformDialog = it },
        onShowEditPlatformDialogChange = { showEditPlatformDialog = it },
        onShowCollectionPickerChange = { showCollectionPicker = it },
        onShowContactPickerChange = { showContactPicker = it },
        onShowCropDialogChange = { showCropDialog = it },
        onShowSyncOptionsSheetChange = { showSyncOptionsSheet = it },
        onSelectedFieldChange = { selectedField = it },
        onEditFieldValueChange = { editFieldValue = it },
        onSelectedPlatformDetailChange = { selectedPlatformDetail = it },
        onEditingPlatformChange = { editingPlatform = it },
        onCropSourceImageChange = { cropSourceImage = it },
        onSyncPlatformInfoChange = { syncPlatformInfo = it },
        onSelectedExistingContactChange = { selectedExistingContact = it },
        onShowBatchImportDialogChange = { showBatchImportDialog = it },
        onShowBioEditChange = { showBioEdit = it },
        onShowTagPickerChange = { showTagPicker = it },
        onShowTagManagerChange = { showTagManager = it },
        onShowAiTagPreviewChange = { showAiTagPreview = it },
        onShowAvatarPreviewChange = { showAvatarPreview = it },
        onAvatarVersionIncrement = { avatarVersion++ },
        onIsSettingAvatarChange = { isSettingAvatar = it },
        onCropConfirm = onCropConfirm,
        onPickNewAvatar = {
            showAvatarPreview = false
            pickAvatarLauncher.launch()
        },
        onRefreshData = onRefreshData,
    )
}

