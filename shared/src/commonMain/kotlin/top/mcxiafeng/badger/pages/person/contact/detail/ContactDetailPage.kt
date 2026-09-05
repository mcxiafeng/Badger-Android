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
import top.mcxiafeng.badger.network.kindCanSync
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
import top.mcxiafeng.badger.platform.showToast
import top.mcxiafeng.badger.platform.BackHandler
import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.mcxiafeng.badger.shared.util.nowMs

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

        val viewModel: ContactDetailViewModel = koinViewModel()
    val scope = rememberCoroutineScope()
    // 从 ViewModel 观察状态
    val contactWithFields by viewModel.contactWithFields.collectAsStateWithLifecycle()
    val platformData by viewModel.platformData.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    // AI 标签推荐状态
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
    // 头像同步流程（平台同步/新增平台自动同步）的 in-flight 标志与版本号
    var isSettingAvatar by remember { mutableStateOf(false) }
    var avatarVersion by remember { mutableIntStateOf(0) }
    var showCropDialog by remember { mutableStateOf(false) }
    var cropSourceImage by remember { mutableStateOf<PlatformImage?>(null) }

    // PR2 fix:基础信息编辑 Dialog state
    var basicInfoEditField by remember { mutableStateOf<String?>(null) }
    var basicInfoEditCurrent by remember { mutableStateOf<String?>(null) }

    // PR3 fix:country/region 联动 — 需要先知道当前 country 才能进 region dialog
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

    // 添加到名片夹弹窗（由 TopAppBar ⭐ 触发）
    var showCollectionPicker by remember { mutableStateOf(false) }

    // 个人介绍 / 标签编辑
    var showBioEdit by remember { mutableStateOf(false) }
    var showTagPicker by remember { mutableStateOf(false) }
    var showTagManager by remember { mutableStateOf(false) }
    // AI 推荐标签预览 Dialog
    var showAiTagPreview by remember { mutableStateOf(false) }

    // 头像大图预览
    var showAvatarPreview by remember { mutableStateOf(false) }

    // 系统返回键：FloatingToolbar 显示时关闭 bar
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

    // PR3 fix:country/region 联动 — 在 fields 已知时填充 currentCountryName
    // (country cell 显示当前值;region dialog 用它作前置)
    LaunchedEffect(fields) {
        val countryValue = fields.firstOrNull { it.fieldKey == "country" }?.value?.takeIf { s -> s.isNotBlank() }
        if (countryValue != null && currentCountryName != countryValue) {
            currentCountryName = countryValue
            // externalId 留 null:此时只用于显示 title;真实拉列表用 countryValue 模糊匹配或后端 ID
            currentCountryExternalId = null
        }
    }

    // 头像位图（异步加载）：本地 avatarPath 优先，其次远程 avatarUrl（[KMP K13c] ImageBitmap）
    var avatarImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val localAvatarPath = contact?.avatarPath
    val remoteAvatarUrl = contact?.avatarUrl
    LaunchedEffect(localAvatarPath, remoteAvatarUrl, avatarVersion) {
        avatarImageBitmap = if (!localAvatarPath.isNullOrBlank()) {
            ImageFiles.loadImageBytes(localAvatarPath)?.let { bytes ->
                runCatching { bytes.decodeToImageBitmap() }.getOrNull()
            }
        } else if (!remoteAvatarUrl.isNullOrBlank()) {
            downloadImageAsPng(remoteAvatarUrl)?.let { bytes ->
                runCatching { bytes.decodeToImageBitmap() }.getOrNull()
            }
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

    // 名片夹关联
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
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Lucide.ArrowLeft,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    // ⭐ 星星 = "添加到名片夹"
                    // 已有关联时用 primary 色提示"已加入"
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
                    // 平台同步判定已下沉到 viewModel.resolvePlatformForField（参见该方法注释）。
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
                            viewModel.applySyncResult(contactId, resolved.name, avatarPath)
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
                // 点头像 → 全屏预览大图(仅在已加载到头像位图时触发)
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
                // [P1-7] 防止重复触发:正在生成中点按无副作用 + 提示
                if (aiTagLoading) {
                    showToast("AI 正在生成中…")
                    return@lambda
                }
                // bio 为空时引导用户先补内容
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
                basicInfoEditField = fieldKey
                basicInfoEditCurrent = currentValue
                // region 进入时需要传 countryName + countryId
                if (fieldKey == "region") {
                    // 若已选过国家,name 从 _state 读;首次就强制先选国家
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

// [§15 #2] AvatarPreviewDialog + upgradeAvatarUrlToHd 已抽出到 ContactDetailAvatar.kt
// buildContactShareText 已抽出到 ContactDetailUtils.kt
