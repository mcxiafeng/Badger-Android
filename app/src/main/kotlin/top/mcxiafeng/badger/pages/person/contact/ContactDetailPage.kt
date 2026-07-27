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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.ContactFieldDisplay
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity as ContactPlatform
import top.mcxiafeng.badger.data.ContactWithFields
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
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.window.WindowDialog
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
    val viewModel: ContactDetailViewModel = koinViewModel()
    val scope = rememberCoroutineScope()
    // 从 ViewModel 观察状态
    val contactWithFields by viewModel.contactWithFields.collectAsState()
    val platformData by viewModel.platformData.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    // AI 标签推荐状态
    val aiTagCandidates by viewModel.aiTagCandidates.collectAsState()
    val aiTagLoading by viewModel.aiTagLoading.collectAsState()
    val aiTagError by viewModel.aiTagError.collectAsState()

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

    // 名片夹关联(用于 CollectionPickerDialog 的 currentCollectionIds 与 ⭐ tint 状态)
    // [修复防御]: 提前到 Scaffold 之前,让 TopAppBar ⭐ IconButton 也能根据是否有名片夹切换 tint。
    // [性能优化]: 改用专门的 getContactCollectionIds,只返回 collectionId 列,避免下载完整 ScanResult。
    val contactCollectionIdsList by remember(contactId) {
        viewModel.collectionRepository.getContactCollectionIds(contactId)
    }.collectAsState(initial = emptyList())
    val contactCollectionIds by remember(contactCollectionIdsList) {
        mutableStateOf(contactCollectionIdsList.toSet())
    }

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
                    // [修复防御]: 已有任意名片夹关联时切到主题色 primary,提示"已加入";无关联时
                    // 用 onSurface(默认近黑),避免无意义的全屏主色噪声。
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
                    // 平台同步判定已下沉到 viewModel.resolvePlatformForField（参见该方法注释）。
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
            bio = contact?.bio,
            tags = tags,
            onAvatarClick = {
                // 点头像 → 全屏预览大图(仅在已加载到头像位图时触发)
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
            onBioClick = { showBioEdit = true },
            onTagsClick = { showTagPicker = true },
            onAiTagsClick = lambda@{
                // [P1-7] 防止重复触发:正在生成中点按无副作用 + 提示
                if (aiTagLoading) {
                    Toast.makeText(context, "AI 正在生成中…", Toast.LENGTH_SHORT).show()
                    return@lambda
                }
                // [修复防御]: 用户选"按钮始终显示,无 bio 时弹错"。按钮永远可点;
                // 这里前置校验 bio:为空时不调 generateAiTags(否则 ViewModel 内仍要走完整 try-catch),
                // 直接 toast + 自动打开 bio 编辑对话框,引导用户去补内容。
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
                // region 进入时需要传 countryName + countryId
                if (fieldKey == "region") {
                    // 若已选过国家,name 从 _state 读;首次就强制先选国家
                }
            },
        )
    }

    // PR2 fix:基础信息编辑 Dialog(性别 / 生日 / 国家 / 地区)
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
            // 选中国家同时清空地区(避免地区不匹配新国家)
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

    // 名片夹关联已提前到 Scaffold 之前(详见顶部),用于 ⭐ tint 与 CollectionPickerDialog。

    ContactDetailPageDialogs(
        contactId = contactId,
        viewModel = viewModel,
        contact = contact,
        contactWithFields = contactWithFields,
        platformData = platformData,
        contactCollectionIds = contactCollectionIds,
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
        // 对话框数据
        selectedField = selectedField,
        editFieldValue = editFieldValue,
        selectedPlatformDetail = selectedPlatformDetail,
        editingPlatform = editingPlatform,
        cropSourceUri = cropSourceUri,
        syncPlatformInfo = syncPlatformInfo,
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
                val needsAvatar = freshContact?.avatarPath.isNullOrBlank() && freshContact?.avatarUrl.isNullOrBlank()
                // [修复防御]: 原条件 `adapter?.canSync == true && needsAvatar` 会让已有头像的联系人
                // 跳过整段同步——导致确定时不拿信息。现确保只要平台支持同步，
                // 都会去解析昵称/头像并写回 PlatformEntry；下载/写联系人头像仅在需要时执行。
                // sync 判定基于 platformKey（参见 kindCanSync），不再依赖 ContactType。
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
    )

    // ====== 个人介绍 / 标签 / AI 预览 Dialogs ======
    ContactDetailBioEditDialog(
        show = showBioEdit,
        currentBio = contact?.bio,
        onDismiss = { showBioEdit = false },
        onSave = { newBio ->
            viewModel.updateBio(contactId, newBio)
        },
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
            // [修复防御]: 关闭 picker 后再开 manager,避免 WindowDialog 嵌套闪烁
            // (同一 WindowDialog 内根据状态切换内容)
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
            // [修复防御]: 详情页暂不直接跳转到全局标签管理（路由透传未在所有调用点补全），
            // 用 Toast 兜底引导用户到 设置 → 标签管理。后续可加 onOpenSettings 回调。
            showTagManager = false
            android.widget.Toast.makeText(context, "请到 设置 → 标签管理 完成全局操作", android.widget.Toast.LENGTH_SHORT).show()
        },
    )

    // AI 推荐标签预览 —— candidates 由 ViewModel.generateAiTags 异步填充,
    // show=true 触发请求,候选到达后 Dialog 内 FlowRow 自动渲染;用户取消时清空 candidates 避免下次复用。
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

    // 头像大图预览:固定 320dp 方盒 + Image Fit 居中
    // [修复防御]: 列表项拉 100×100 缩略图;只有预览/保存时才拉高清。
    // QQ 域(q1.qlogo.cn / q.qlogo.cn / p.qlogo.cn)在此阶段升级到 640 接口。
    // 预览时拉一次,保存复用 previewBitmap,不再二次下载。
    val hasOriginal = !contact?.avatarUrl.isNullOrBlank()
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(showAvatarPreview, contact?.avatarUrl) {
        if (showAvatarPreview) {
            val url = contact?.avatarUrl?.takeIf { it.isNotBlank() }
            if (url != null) {
                val hdUrl = upgradeAvatarUrlToHd(url)
                Log.d("AvatarPreview", "original=$url, hd=$hdUrl")
                val headers = if (hdUrl.contains("hdslb.com") || hdUrl.contains("bilibili.com"))
                    BILIBILI_HEADERS else null
                // 优先拉高清;失败回退原始 URL(可能平台不支持高清参数)
                var bmp = HttpUtil.downloadBitmap(hdUrl, headers = headers, timeoutMs = 8000)
                if (bmp == null && hdUrl != url) {
                    Log.w("AvatarPreview", "hd download failed, fallback to original $url")
                    bmp = HttpUtil.downloadBitmap(url, headers = headers, timeoutMs = 8000)
                }
                if (bmp != null) {
                    Log.d("AvatarPreview", "downloaded ${bmp.width}x${bmp.height} from $hdUrl")
                } else {
                    Log.w("AvatarPreview", "all download failed for $url")
                }
                previewBitmap = bmp
                previewUrl = url
            } else {
                Log.d("AvatarPreview", "no avatarUrl, fallback to avatarBitmap ${avatarBitmap?.width}x${avatarBitmap?.height}")
                previewBitmap = null
                previewUrl = null
            }
        } else {
            previewBitmap?.recycle()
            previewBitmap = null
            previewUrl = null
        }
    }
    val displayBitmap = previewBitmap ?: avatarBitmap
    WindowDialog(
        show = showAvatarPreview && displayBitmap != null,
        onDismissRequest = {
            showAvatarPreview = false
            previewBitmap?.recycle()
            previewBitmap = null
            previewUrl = null
        },
        backgroundColor = MiuixTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
        ) {
            // 图片区:固定 320×320dp 方盒,Image Fit 居中(保留原比例,不裁不糊)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                displayBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "头像大图",
                        modifier = Modifier.size(320.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            // 操作区:Miuix surface 色背景,与图片区物理分离
            // [修复防御]: "保存原图"必须从 avatarUrl 重新联网拉原图写入相册;
            // 没有任何 fallback 到预览位图——预览位图已经是缩放过的内存图,
            // 保存这种图会让用户误以为拿到了原图,实则画质损失。
            // 没有 avatarUrl(纯本地导入头像)则不显示"保存原图"按钮,避免误操作。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MiuixTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (hasOriginal) {
                    TextButton(
                        text = "保存原图",
                        onClick = {
                            // 复用预览时已拉的高清图;若用户没先预览就点保存,现场拉一次
                            showAvatarPreview = false
                            scope.launch {
                                try {
                                    val c = contact
                                    if (c == null) {
                                        Toast.makeText(context, "无联系人数据", Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }
                                    val original = previewBitmap
                                        ?: run {
                                            // 未预览:同步拉一次高清
                                            val url = c.avatarUrl.takeIf { it.isNotBlank() }
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
                                    // previewBitmap 由 Dialog 的 onDismissRequest 负责 recycle,这里不释放
                                    val msg = if (ok) "原图已保存到相册" else "保存失败"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Log.e("ContactDetailPage", "保存原图失败", e)
                                    Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                TextButton(
                    text = "更换头像",
                    onClick = {
                        showAvatarPreview = false
                        pickAvatarLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.weight(if (hasOriginal) 1f else 2f),
                )
            }
        }
    }
}

/**
 * 把平台头像 URL 升级到高清接口。
 *
 * 列表项拉 100×100 缩略图;只有预览/保存时才调此函数取高清。
 * - QQ 个人号 (q1.qlogo.cn / q.qlogo.cn) → `headimg_dl` 640 接口
 * - QQ 群 (p.qlogo.cn/gh/...) → 末尾加 `/640`
 * - 其它平台(B 站 / 微信等)→ 原样返回
 */
internal fun upgradeAvatarUrlToHd(url: String): String {
    return when {
        // QQ 个人号:g?b=qq&nk=xxx&s=100 → headimg_dl spec=640
        url.contains("qlogo.cn/g") && url.contains("b=qq") -> {
            val nk = Regex("[?&]nk=(\\d+)").find(url)?.groupValues?.get(1)
            if (nk != null) "http://q.qlogo.cn/headimg_dl?dst_uin=$nk&spec=640&img_type=jpg"
            else url
        }
        // QQ 个人号直链已经走 headimg_dl:把 spec 升到 640
        url.contains("q.qlogo.cn/headimg_dl") -> {
            if (url.contains("spec=")) url.replace(Regex("spec=\\d+"), "spec=640")
            else "$url&spec=640"
        }
        // QQ 群头像:https://p.qlogo.cn/gh/{g}/{g}/ 末尾加 /640
        url.contains("p.qlogo.cn/gh/") -> {
            when {
                Regex("/640$").containsMatchIn(url) -> url
                Regex("/\\d+$").containsMatchIn(url) -> "$url/640"
                else -> url
            }
        }
        else -> url
    }
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
