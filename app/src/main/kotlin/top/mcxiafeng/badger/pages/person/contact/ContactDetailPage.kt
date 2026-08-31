package top.mcxiafeng.badger.pages.person.contact

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.PersonFieldDisplay
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity as ContactPlatform
import top.mcxiafeng.badger.data.PersonWithFields
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.network.kindCanSync
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
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
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
    val contactCollectionIds by viewModel.contactCollectionIds.collectAsStateWithLifecycle()

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
    var cropSourceUri by remember { mutableStateOf<Uri?>(null) }

    // PR2 fix:基础信息编辑 Dialog state
    var basicInfoEditField by remember { mutableStateOf<String?>(null) }
    var basicInfoEditCurrent by remember { mutableStateOf<String?>(null) }

    // PR3 fix:country/region 联动 — 需要先知道当前 country 才能进 region dialog
    var currentCountryName by remember { mutableStateOf<String?>(null) }
    var currentCountryExternalId by remember { mutableStateOf<Long?>(null) }

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
            // 沉浸:TopAppBar 完全透明,覆盖在头图上视觉上浮在头图
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
                    // ⭐ 星星 = "添加到名片夹"（接管原名片夹 Section 入口）
                    IconButton(onClick = { showCollectionPicker = true }) {
                        Icon(
                            imageVector = Icons.Default.Star,
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
                                imageVector = Icons.Default.MoreVert,
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
                    selectedField?.let { f ->
                        Methods.copyToClipboard(context, f.fieldName, f.value)
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
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
                showPlatformToolbar = showPlatformContextMenu && selectedPlatform != null,
                selectedPlatform = selectedPlatform,
                onPlatformCopy = {
                    selectedPlatform?.let { (fieldKey, pEntry) ->
                        val pDisplayName = FIELD_DEF_MAP[fieldKey]?.displayName ?: fieldKey
                        val copyText = pEntry.value ?: pEntry.jumpLink
                        Methods.copyToClipboard(context, pDisplayName, copyText)
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
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
                    val platformInfo = selectedPlatform
                    syncPlatformInfo = platformInfo
                    showPlatformContextMenu = false
                    selectedPlatform = null
                    showSyncOptionsSheet = platformInfo != null
                    Log.d("ContactDetailPage", "Sync info requested for platform: ${platformInfo?.first}")
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
            bio = contact?.bio,
            tags = tags,
            onAvatarClick = {
                if (avatarBitmap != null) showAvatarPreview = true
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
                    Toast.makeText(context, "AI 正在生成中…", Toast.LENGTH_SHORT).show()
                    return@lambda
                }
                val bio = contact?.bio
                if (bio.isNullOrBlank()) {
                    Toast.makeText(context, "请先填写个人介绍,AI 才能更准确推荐", Toast.LENGTH_SHORT).show()
                    showBioEdit = true
                } else {
                    showAiTagPreview = true
                    viewModel.generateAiTags(contactId)
                }
            },
            onBasicInfoCellClick = { fieldKey, currentValue ->
                basicInfoEditField = fieldKey
                basicInfoEditCurrent = currentValue
            },
        )
    }

    GenderPickerDialog(
        show = basicInfoEditField == "gender",
        current = basicInfoEditCurrent,
        onDismiss = { basicInfoEditField = null },
        onConfirm = { value ->
            viewModel.updateBasicInfoField(contactId, "gender", value)
            basicInfoEditField = null
        },
    )
    BirthdayPickerDialog(
        show = basicInfoEditField == "birthday",
        current = basicInfoEditCurrent,
        onDismiss = { basicInfoEditField = null },
        onConfirm = { value ->
            viewModel.updateBasicInfoField(contactId, "birthday", value)
            basicInfoEditField = null
        },
    )
    CountryPickerDialog(
        show = basicInfoEditField == "country",
        current = basicInfoEditCurrent,
        onDismiss = { basicInfoEditField = null },
        onConfirm = { name, externalId ->
            viewModel.updateBasicInfoField(contactId, "country", name)
            currentCountryName = name
            currentCountryExternalId = externalId
            viewModel.updateBasicInfoField(contactId, "region", "")
            basicInfoEditField = null
        },
    )
    RegionPickerDialog(
        show = basicInfoEditField == "region",
        current = basicInfoEditCurrent,
        countryId = currentCountryExternalId,
        countryName = currentCountryName,
        onDismiss = { basicInfoEditField = null },
        onConfirm = { value ->
            viewModel.updateBasicInfoField(contactId, "region", value)
            basicInfoEditField = null
        },
    )

    ContactDetailPageDialogs(
        contactId = contactId,
        viewModel = viewModel,
        contact = contact,
        contactWithFields = contactWithFields,
        platformData = platformData,
        contactCollectionIds = contactCollectionIds,
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
        selectedField = selectedField,
        editFieldValue = editFieldValue,
        selectedPlatformDetail = selectedPlatformDetail,
        editingPlatform = editingPlatform,
        cropSourceUri = cropSourceUri,
        syncPlatformInfo = syncPlatformInfo,
        selectedExistingContact = selectedExistingContact,
        onDismissFieldDelete = { showFieldDeleteDialog = false; selectedField = null },
        onDeleteField = { field ->
            viewModel.deleteFieldValue(contactId, field.valueId)
            viewModel.reloadContact(contactId)
        },
        onEditFieldValueChange = { editFieldValue = it },
        onDismissEditField = { showEditFieldDialog = false },
        onSaveEditField = { newValue ->
            selectedField?.valueId?.let { fid ->
                viewModel.updateFieldValue(contactId, fid, newValue)
                viewModel.reloadContact(contactId)
            }
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
                val needsAvatar = freshContact?.avatarPath.isNullOrBlank() && freshContact?.avatarUrl.isNullOrBlank()
                if (fieldKey.kindCanSync) {
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
            if (msg != null) Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
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
    )

    BatchImportPlatformsDialog(
        show = showBatchImportDialog,
        onDismiss = { showBatchImportDialog = false },
        onBatchResolve = { urls -> viewModel.batchResolvePlatforms(urls) },
        onConfirm = { selectedItems ->
            scope.launch(Dispatchers.IO) {
                selectedItems.forEach { item ->
                    val entry = PlatformEntry(
                        displayName = item.resolved?.name,
                        jumpLink = item.url,
                        value = null,
                        avatarUrl = item.resolved?.avatarUrl,
                    )
                    viewModel.addOrUpdatePlatform(contactId, item.fieldKey, entry)
                    if (item.fieldKey.kindCanSync) {
                        val freshContact = viewModel.getContactById(contactId)
                        val needsAvatar = freshContact?.avatarPath.isNullOrBlank() && freshContact?.avatarUrl.isNullOrBlank()
                        if (needsAvatar && item.resolved?.avatarUrl != null) {
                            try {
                                val avatarPath = downloadAndSaveAvatar(item.resolved.avatarUrl!!, context, contactId)
                                if (avatarPath != null) {
                                    val latestContact = viewModel.getContactById(contactId) ?: freshContact
                                    viewModel.updateContact(latestContact!!.copy(
                                        avatarPath = avatarPath,
                                        updateTime = System.currentTimeMillis(),
                                    ))
                                }
                            } catch (e: Exception) {
                                Log.e("ContactDetailPage", "批量导入头像下载失败: ${item.url}", e)
                            }
                        }
                    }
                }
                viewModel.reloadContact(contactId)
            }
            onRefreshData?.invoke()
            Toast.makeText(context, "已添加 ${selectedItems.size} 个平台", Toast.LENGTH_SHORT).show()
        },
    )

    ContactDetailBioEditDialog(
        show = showBioEdit,
        currentBio = contact?.bio,
        onDismiss = { showBioEdit = false },
        onSave = { newBio -> viewModel.updateBio(contactId, newBio) },
    )
    TagPickerDialog(
        show = showTagPicker,
        tagRepository = viewModel.tagRepository,
        currentTagIds = tags.map { it.id }.toSet(),
        onDismiss = { showTagPicker = false },
        onConfirm = { addedIds, removedIds ->
            viewModel.updateTags(contactId, addedIds, removedIds)
            showTagPicker = false
        },
        onManageTags = {
            showTagPicker = false
            showTagManager = true
        },
    )
    TagQuickManageDialog(
        show = showTagManager,
        contactId = contactId,
        tagRepository = viewModel.tagRepository,
        onDismiss = { showTagManager = false },
        onOpenFullManager = {
            showTagManager = false
            android.widget.Toast.makeText(context, "请到 设置 → 标签管理 完成全局操作", android.widget.Toast.LENGTH_SHORT).show()
        },
    )

    val aiCandidatesNonEmpty = aiTagCandidates.isNotEmpty() || aiTagLoading || aiTagError != null
    AiTagPreviewDialog(
        show = showAiTagPreview && aiCandidatesNonEmpty,
        candidates = aiTagCandidates,
        isLoading = aiTagLoading,
        errorMessage = aiTagError,
        onDismiss = {
            showAiTagPreview = false
            viewModel.clearAiTagCandidates()
        },
        onConfirm = { selected ->
            viewModel.applyAiTagCandidates(contactId, selected)
            showAiTagPreview = false
        },
    )

    AvatarPreviewDialog(
        contactId = contact?.id ?: -1L,
        avatarUrl = contact?.avatarUrl,
        fallbackBitmap = avatarBitmap,
        show = showAvatarPreview,
        onDismiss = { showAvatarPreview = false },
        onSaveOriginal = {
            showAvatarPreview = false
            scope.launch {
                try {
                    val c = contact
                    if (c == null) {
                        Toast.makeText(context, "无联系人数据", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val original = it
                        ?: run {
                            val url = c.avatarUrl?.takeIf { it.isNotBlank() }
                                ?: return@run null
                            val hdUrl = upgradeAvatarUrlToHd(url)
                            val headers = if (hdUrl.contains("hdslb.com") || hdUrl.contains("bilibili.com"))
                                BILIBILI_HEADERS else null
                            withContext(Dispatchers.IO) {
                                HttpUtil.downloadBitmap(hdUrl, headers = headers, timeoutMs = 8000)
                                    ?: if (hdUrl != url) HttpUtil.downloadBitmap(url, headers = headers, timeoutMs = 8000) else null
                            }
                        }
                    if (original == null) {
                        Toast.makeText(context, "无法获取原图,请检查网络", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val ok = withContext(Dispatchers.IO) {
                        Methods.saveBitmapToGallery(
                            context,
                            original,
                            "badger_avatar_${c.id}_${System.currentTimeMillis()}.png"
                        )
                    }
                    val msg = if (ok) "原图已保存到相册" else "保存失败"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("ContactDetailPage", "保存原图失败", e)
                    Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onPickNewAvatar = {
            showAvatarPreview = false
            pickAvatarLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
    )
}

// [§15 #2] AvatarPreviewDialog + upgradeAvatarUrlToHd 已抽出到 ContactDetailAvatar.kt
// buildContactShareText 已抽出到 ContactDetailUtils.kt
