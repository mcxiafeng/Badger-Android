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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import top.mcxiafeng.badger.data.PersonFieldDisplay
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.network.kindCanSync
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ocr.PLATFORM_FIELD_KEYS
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
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
 * 联系人详情页。
 *
 * 数据写操作由 ViewModel 提供可等待 API；页面只负责编排 UI 与导航。
 */
@Composable
fun ContactDetailPage(
    contactId: Long,
    onBack: () -> Unit,
    onRefreshData: (() -> Unit)? = null,
    onOpenScannerForImport: (() -> Unit)? = null,
) {
    if (contactId == -1L) {
        UserProfileDetailPage(
            onBack = onBack,
            onRefreshData = onRefreshData,
            onOpenScannerForImport = onOpenScannerForImport,
        )
        return
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: ContactDetailViewModel = koinViewModel()
    val scope = rememberCoroutineScope()
    val contactWithFields by viewModel.contactWithFields.collectAsStateWithLifecycle()
    val platformData by viewModel.platformData.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
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
    var isSettingAvatar by remember { mutableStateOf(false) }
    var avatarVersion by remember { mutableIntStateOf(0) }
    var showCropDialog by remember { mutableStateOf(false) }
    var cropSourceUri by remember { mutableStateOf<Uri?>(null) }
    var basicInfoEditField by remember { mutableStateOf<String?>(null) }
    var basicInfoEditCurrent by remember { mutableStateOf<String?>(null) }
    var currentCountryName by remember { mutableStateOf<String?>(null) }
    var currentCountryExternalId by remember { mutableStateOf<Long?>(null) }
    var showCollectionPicker by remember { mutableStateOf(false) }
    var showBioEdit by remember { mutableStateOf(false) }
    var showTagPicker by remember { mutableStateOf(false) }
    var showTagManager by remember { mutableStateOf(false) }
    var showAiTagPreview by remember { mutableStateOf(false) }
    var showAvatarPreview by remember { mutableStateOf(false) }

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
        if (uri != null) {
            cropSourceUri = uri
            showCropDialog = true
        }
    }

    val onCropConfirm: (Bitmap) -> Unit = { croppedBitmap ->
        scope.launch {
            try {
                val avatarFile = withContext(Dispatchers.IO) {
                    Methods.saveBitmapAsAvatar(context, croppedBitmap, "contact_${contactId}_avatar.webp")
                }
                viewModel.applyAvatarUpdate(contactId, avatarFile.absolutePath)
                avatarVersion++
            } catch (e: Exception) {
                Log.e("ContactDetailPage", "设置头像失败", e)
                Toast.makeText(context, "设置头像失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
        val countryValue = fields.firstOrNull { it.fieldKey == "country" }
            ?.value
            ?.takeIf { it.isNotBlank() }
        if (countryValue != null && currentCountryName != countryValue) {
            currentCountryName = countryValue
            currentCountryExternalId = null
        }
    }

    var avatarBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val localAvatarPath = contact?.avatarPath
    val remoteAvatarUrl = contact?.avatarUrl
    LaunchedEffect(localAvatarPath, remoteAvatarUrl, avatarVersion) {
        avatarBitmap = withContext(Dispatchers.IO) {
            when {
                !localAvatarPath.isNullOrBlank() -> Methods.loadAvatarBitmap(localAvatarPath)
                !remoteAvatarUrl.isNullOrBlank() -> HttpUtil.downloadBitmap(remoteAvatarUrl, timeoutMs = 5000)
                else -> null
            }
        }
    }

    val systemFields = remember(fields) {
        fields.filter { it.fieldKey != null && it.fieldKey !in PLATFORM_FIELD_KEYS }
    }
    val customFields = remember(fields) { fields.filter { it.fieldKey == null } }
    val platformFields = remember(platformData) {
        platformData.map { cp ->
            cp.platformKey to PlatformEntry(
                value = cp.value,
                displayName = cp.displayName,
                jumpLink = cp.jumpLink,
                originalLink = cp.originalLink,
                avatarUrl = cp.avatarUrl,
            )
        }.filter { it.second.jumpLink.isNotBlank() || !it.second.value.isNullOrBlank() }
    }

    val moreMenuItems = remember { listOf("附加到已有联系人", "分享联系方式") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "",
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showCollectionPicker = true }) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "添加到名片夹",
                            tint = if (contactCollectionIds.isNotEmpty()) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                        )
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
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
                                                    val shareText = buildContactShareText(contact, fields)
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
                                        },
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
                    val field = selectedField ?: return@ContactDetailFloatingToolbars
                    val fieldKey = field.fieldKey ?: return@ContactDetailFloatingToolbars
                    isSettingAvatar = true
                    scope.launch {
                        try {
                            val resolved = viewModel.resolvePlatformForField(fieldKey, field.value)
                            if (resolved == null) {
                                Toast.makeText(context, "无法获取该平台信息", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            val avatarPath = resolved.avatarUrl?.let { avatarUrl ->
                                val headers = if (avatarUrl.contains("hdslb.com") || avatarUrl.contains("bilibili.com")) BILIBILI_HEADERS else null
                                withContext(Dispatchers.IO) {
                                    downloadAndSaveAvatar(avatarUrl, context, contactId, headers)
                                }
                            }
                            viewModel.applySyncResult(contactId, resolved.name, avatarPath)
                            avatarVersion++
                        } catch (e: Exception) {
                            Log.e("ContactDetailPage", "同步失败", e)
                            Toast.makeText(context, "同步失败", Toast.LENGTH_SHORT).show()
                        } finally {
                            isSettingAvatar = false
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
                        Methods.copyToClipboard(context, pDisplayName, pEntry.value ?: pEntry.jumpLink)
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
                    syncPlatformInfo = selectedPlatform
                    showPlatformContextMenu = false
                    selectedPlatform = null
                    showSyncOptionsSheet = syncPlatformInfo != null
                },
                onPlatformDelete = {
                    val deletedEntry = selectedPlatform
                    val platformKey = deletedEntry?.first ?: return@ContactDetailFloatingToolbars
                    showPlatformContextMenu = false
                    selectedPlatform = null
                    scope.launch {
                        if (!viewModel.removePlatformAwait(contactId, platformKey)) return@launch
                        val freshContact = viewModel.getContactById(contactId)
                        val deletedAvatarUrl = deletedEntry.second.avatarUrl
                        if (
                            freshContact != null &&
                            !freshContact.avatarPath.isNullOrBlank() &&
                            deletedAvatarUrl != null &&
                            freshContact.avatarUrl == deletedAvatarUrl
                        ) {
                            val fallbackEntry = viewModel.getContactPlatforms(contactId)
                                .firstOrNull { !it.avatarUrl.isNullOrBlank() }
                            if (fallbackEntry?.avatarUrl != null) {
                                val fallbackUrl = fallbackEntry.avatarUrl
                                val headers = if (fallbackUrl.contains("hdslb.com") || fallbackUrl.contains("bilibili.com")) BILIBILI_HEADERS else null
                                val newAvatarPath = withContext(Dispatchers.IO) {
                                    downloadAndSaveAvatar(fallbackUrl, context, contactId, headers)
                                }
                                val latest = viewModel.getContactById(contactId)
                                if (latest != null) {
                                    if (newAvatarPath != null) {
                                        viewModel.updateContactAwait(latest.copy(
                                            avatarPath = newAvatarPath,
                                            avatarUrl = fallbackUrl,
                                            updateTime = System.currentTimeMillis(),
                                        ))
                                    } else {
                                        Methods.deleteAvatarFile(latest.avatarPath)
                                        viewModel.updateContactAwait(latest.copy(
                                            avatarPath = null,
                                            avatarUrl = null,
                                            updateTime = System.currentTimeMillis(),
                                        ))
                                    }
                                }
                            } else {
                                Methods.deleteAvatarFile(freshContact.avatarPath)
                                viewModel.updateContactAwait(freshContact.copy(
                                    avatarPath = null,
                                    avatarUrl = null,
                                    updateTime = System.currentTimeMillis(),
                                ))
                            }
                        }
                        viewModel.reloadContactAwait(contactId)
                        avatarVersion++
                        onRefreshData?.invoke()
                    }
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
            onAvatarClick = { if (avatarBitmap != null) showAvatarPreview = true },
            onEditNameClick = { showEditNameDialog = true },
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
            onAiTagsClick = {
                when {
                    aiTagLoading -> Toast.makeText(context, "AI 正在生成中…", Toast.LENGTH_SHORT).show()
                    contact?.bio.isNullOrBlank() -> {
                        Toast.makeText(context, "请先填写个人介绍,AI 才能更准确推荐", Toast.LENGTH_SHORT).show()
                        showBioEdit = true
                    }
                    else -> {
                        showAiTagPreview = true
                        viewModel.generateAiTags(contactId)
                    }
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
        onDismissFieldDelete = {
            showFieldDeleteDialog = false
            selectedField = null
        },
        onDeleteField = { field ->
            scope.launch {
                if (viewModel.deleteFieldValueAwait(contactId, field.valueId)) {
                    viewModel.reloadContactAwait(contactId)
                    onRefreshData?.invoke()
                }
            }
        },
        onEditFieldValueChange = { editFieldValue = it },
        onDismissEditField = { showEditFieldDialog = false },
        onSaveEditField = { newValue ->
            val fieldId = selectedField?.valueId
            if (fieldId != null) {
                scope.launch {
                    if (viewModel.updateFieldValueAwait(contactId, fieldId, newValue)) {
                        viewModel.reloadContactAwait(contactId)
                        onRefreshData?.invoke()
                    }
                }
            }
            showEditFieldDialog = false
            selectedField = null
        },
        onDismissEditName = { showEditNameDialog = false },
        onSaveEditName = { newName ->
            scope.launch {
                if (viewModel.updateNameAwait(contactId, newName)) {
                    viewModel.reloadContactAwait(contactId)
                    onRefreshData?.invoke()
                }
            }
            showEditNameDialog = false
        },
        onDismissFieldDetail = {
            showFieldDetailDialog = false
            selectedField = null
        },
        onDismissPlatformDetail = {
            showPlatformDetailDialog = false
            selectedPlatformDetail = null
        },
        onDismissAddPlatform = { showAddPlatformDialog = false },
        onConfirmAddPlatform = { fieldKey, entry ->
            showAddPlatformDialog = false
            scope.launch {
                try {
                    if (!viewModel.addOrUpdatePlatformAwait(contactId, fieldKey, entry)) return@launch
                    val freshContact = viewModel.getContactById(contactId)
                    val contactType = FIELD_DEF_MAP[fieldKey]?.contactType
                    val needsAvatar = freshContact?.avatarPath.isNullOrBlank() && freshContact?.avatarUrl.isNullOrBlank()
                    if (fieldKey.kindCanSync) {
                        try {
                            val content = entry.jumpLink.ifBlank { entry.value ?: "" }
                            val resolveResult = withContext(Dispatchers.IO) {
                                ContactNetworkResolver.getResultInfo(content, mutableMapOf(), type = contactType)
                            }
                            val resolvedAvatar = resolveResult?.avatarUrl?.takeIf { it.isNotBlank() }
                            val resolvedName = resolveResult?.nickname?.takeIf { it.isNotBlank() && it != "未知" }
                            if (resolvedName != null || resolvedAvatar != null) {
                                viewModel.addOrUpdatePlatformAwait(
                                    contactId,
                                    fieldKey,
                                    entry.copy(
                                        displayName = resolvedName ?: entry.displayName,
                                        avatarUrl = resolvedAvatar ?: entry.avatarUrl,
                                    ),
                                )
                            }
                            if (needsAvatar && resolvedAvatar != null) {
                                val newAvatarPath = withContext(Dispatchers.IO) {
                                    downloadAndSaveAvatar(resolvedAvatar, context, contactId)
                                }
                                if (newAvatarPath != null) {
                                    val latestContact = viewModel.getContactById(contactId)
                                    if (latestContact != null) {
                                        viewModel.updateContactAwait(
                                            latestContact.copy(
                                                avatarPath = newAvatarPath,
                                                updateTime = System.currentTimeMillis(),
                                            )
                                        )
                                    }
                                }
                            } else if (resolvedName != null) {
                                val latestContact = viewModel.getContactById(contactId)
                                if (latestContact != null) {
                                    viewModel.updateContactAwait(
                                        latestContact.copy(updateTime = System.currentTimeMillis())
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ContactDetailPage", "Auto-sync avatar failed from $fieldKey", e)
                        }
                    }
                    viewModel.reloadContactAwait(contactId)
                    onRefreshData?.invoke()
                    avatarVersion++
                } catch (e: Exception) {
                    Log.e("ContactDetailPage", "添加平台失败", e)
                    Toast.makeText(context, "添加平台失败", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onDismissEditPlatform = {
            showEditPlatformDialog = false
            editingPlatform = null
        },
        onConfirmEditPlatform = { fieldKey, newEntry ->
            showEditPlatformDialog = false
            editingPlatform = null
            scope.launch {
                if (viewModel.addOrUpdatePlatformAwait(contactId, fieldKey, newEntry)) {
                    viewModel.reloadContactAwait(contactId)
                    onRefreshData?.invoke()
                }
            }
        },
        onDismissCollectionPicker = { showCollectionPicker = false },
        onConfirmCollectionPicker = { addedIds, removedIds ->
            showCollectionPicker = false
            scope.launch {
                if (viewModel.updateCollectionsAwait(contactId, addedIds.toList(), removedIds.toList())) {
                    viewModel.reloadContactAwait(contactId)
                    onRefreshData?.invoke()
                    val msg = when {
                        addedIds.isNotEmpty() && removedIds.isNotEmpty() -> "名片夹已更新"
                        addedIds.isNotEmpty() -> "已添加至 ${addedIds.size} 个名片夹"
                        removedIds.isNotEmpty() -> "已从 ${removedIds.size} 个名片夹移除"
                        else -> null
                    }
                    if (msg != null) Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
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
                selectedCustomFieldIds = selectedCustomFieldIds,
            )
            selectedExistingContact = null
        },
        onCropConfirm = onCropConfirm,
        onDismissCrop = {
            cropSourceUri = null
            showCropDialog = false
        },
        onDismissSync = {
            showSyncOptionsSheet = false
            syncPlatformInfo = null
        },
        onConfirmSync = { syncName, syncAvatar ->
            val platformInfo = syncPlatformInfo
            showSyncOptionsSheet = false
            syncPlatformInfo = null
            if (platformInfo == null) return@ContactDetailPageDialogs
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
                        viewModel.addOrUpdatePlatformAwait(
                            contactId,
                            pName,
                            pEntry.copy(
                                displayName = resolvedName ?: pEntry.displayName,
                                avatarUrl = resolvedAvatar ?: pEntry.avatarUrl,
                            ),
                        )
                    }
                    val freshContact = viewModel.getContactById(contactId) ?: return@launch
                    val newName = if (syncName) {
                        resolvedName ?: pEntry.displayName?.takeIf { it.isNotBlank() }
                    } else null
                    val avatarPath = if (syncAvatar) {
                        val avatarToUse = resolvedAvatar ?: pEntry.avatarUrl
                        if (!avatarToUse.isNullOrBlank()) {
                            isSettingAvatar = true
                            try {
                                val headers = if (avatarToUse.contains("hdslb.com") || avatarToUse.contains("bilibili.com")) BILIBILI_HEADERS else null
                                withContext(Dispatchers.IO) {
                                    downloadAndSaveAvatar(avatarToUse, context, contactId, headers)
                                }
                            } finally {
                                isSettingAvatar = false
                            }
                        } else null
                    } else null
                    var updated = freshContact
                    if (newName != null) updated = updated.copy(name = newName)
                    if (avatarPath != null) updated = updated.copy(avatarPath = avatarPath)
                    if (newName != null || avatarPath != null) {
                        updated = updated.copy(updateTime = System.currentTimeMillis())
                        viewModel.updateContactAwait(updated)
                    }
                    viewModel.reloadContactAwait(contactId)
                    if (newName != null || avatarPath != null) {
                        avatarVersion++
                        Toast.makeText(context, "同步成功", Toast.LENGTH_SHORT).show()
                        onRefreshData?.invoke()
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
        onBatchResolve = viewModel::batchResolvePlatforms,
        onConfirm = { selectedItems ->
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        selectedItems.forEach { item ->
                            val entry = PlatformEntry(
                                displayName = item.resolved?.name,
                                jumpLink = item.url,
                                value = null,
                                avatarUrl = item.resolved?.avatarUrl,
                            )
                            viewModel.addOrUpdatePlatformAwait(contactId, item.fieldKey, entry)
                            if (item.fieldKey.kindCanSync && !item.resolved?.avatarUrl.isNullOrBlank()) {
                                val freshContact = viewModel.getContactById(contactId)
                                val needsAvatar = freshContact?.avatarPath.isNullOrBlank() && freshContact?.avatarUrl.isNullOrBlank()
                                if (needsAvatar) {
                                    try {
                                        val avatarPath = downloadAndSaveAvatar(item.resolved!!.avatarUrl!!, context, contactId)
                                        if (avatarPath != null) {
                                            val latestContact = viewModel.getContactById(contactId) ?: freshContact
                                            if (latestContact != null) {
                                                viewModel.updateContactAwait(
                                                    latestContact.copy(
                                                        avatarPath = avatarPath,
                                                        updateTime = System.currentTimeMillis(),
                                                    )
                                                )
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("ContactDetailPage", "批量导入头像下载失败: ${item.url}", e)
                                    }
                                }
                            }
                        }
                    }
                    viewModel.reloadContactAwait(contactId)
                    onRefreshData?.invoke()
                    avatarVersion++
                    showBatchImportDialog = false
                    Toast.makeText(context, "已添加 ${selectedItems.size} 个平台", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("ContactDetailPage", "批量导入平台失败", e)
                    Toast.makeText(context, "批量导入失败", Toast.LENGTH_SHORT).show()
                }
            }
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
            Toast.makeText(context, "请到 设置 → 标签管理 完成全局操作", Toast.LENGTH_SHORT).show()
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
                    val original = it ?: run {
                        val url = c.avatarUrl?.takeIf { value -> value.isNotBlank() }
                            ?: return@run null
                        val hdUrl = upgradeAvatarUrlToHd(url)
                        val headers = if (hdUrl.contains("hdslb.com") || hdUrl.contains("bilibili.com")) BILIBILI_HEADERS else null
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
                            "badger_avatar_${c.id}_${System.currentTimeMillis()}.png",
                        )
                    }
                    Toast.makeText(context, if (ok) "原图已保存到相册" else "保存失败", Toast.LENGTH_SHORT).show()
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
