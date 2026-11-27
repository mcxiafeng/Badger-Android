package top.mcxiafeng.badger.pages.person.contact

import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactFieldDisplay
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.domain.platformNicknameOrNull
import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.network.adapter.PlatformAdapterRegistry
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ocr.PLATFORM_FIELD_KEYS
import top.mcxiafeng.badger.ocr.buildLaunchAction
import top.mcxiafeng.badger.ui.components.executeLaunchAction
import top.mcxiafeng.badger.utils.BILIBILI_HEADERS
import top.mcxiafeng.badger.utils.HttpUtil
import top.mcxiafeng.badger.utils.Methods
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 联系人详情页
 *
 * 纯 Miuix UI 的联系人详情展示页。
 * 布局：上方 Column（头像+姓名+备注），下方分组 Card（ArrowPreference 列表），创建时间在全部内容底部。
 * 作为独立二级页面显示，完全覆盖一级界面。
 * contactId = MY_CONTACT_ID(-1) 时为"我的名片"：与普通联系人完全同一页面和数据表，
 * 仅由 App.kt 在数据变更后做差异化的刷新处理。
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
    onNavigateToAddPlatform: () -> Unit = {},
    onNavigateToEdit: (Long) -> Unit = {},
    onNavigateToAvatar: (Long) -> Unit = {},
    viewModel: ContactDetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
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
    var showEditPlatformDialog by remember { mutableStateOf(false) }
    var editingPlatform by remember { mutableStateOf<Pair<String, PlatformEntry>?>(null) }
    var showPlatformContextMenu by remember { mutableStateOf(false) }
    var selectedPlatform by remember { mutableStateOf<Pair<String, PlatformEntry>?>(null) }
    var showContactPicker by remember { mutableStateOf(false) }
    var selectedExistingContact by remember { mutableStateOf<Contact?>(null) }
    // 平台同步流程（同步昵称与头像）的 in-flight 标志与版本号
    var isSettingAvatar by remember { mutableStateOf(false) }
    var avatarVersion by remember { mutableIntStateOf(0) }

    // 基础信息编辑 Dialog state（性别 / 生日 / 国家 / 地区，详情页直接编辑）
    var basicInfoEditField by remember { mutableStateOf<String?>(null) }
    var basicInfoEditCurrent by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ContactDetailEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is ContactDetailEvent.RefreshData -> onRefreshData?.invoke()
            }
        }
    }

    // 添加到名片夹弹窗（由 TopAppBar ⭐ 触发）
    var showCollectionPicker by remember { mutableStateOf(false) }

    // 个人介绍跳编辑器；标签在详情页直接编辑
    var showTagPicker by remember { mutableStateOf(false) }
    var showTagManager by remember { mutableStateOf(false) }

    // AI 推荐标签预览 Dialog
    var showAiTagPreview by remember { mutableStateOf(false) }

    val selectFieldActions: (ContactFieldDisplay) -> Unit = { field ->
        Log.d("ContactDetailPageTester", "显示联系方式操作，fieldKey=${field.fieldKey}")
        selectedPlatform = null
        showPlatformContextMenu = false
        selectedField = field
        showContextMenu = true
    }
    val selectPlatformActions: (String, PlatformEntry) -> Unit = { fieldKey, entry ->
        Log.d("ContactDetailPageTester", "显示平台操作，fieldKey=$fieldKey")
        selectedField = null
        showContextMenu = false
        selectedPlatform = fieldKey to entry
        showPlatformContextMenu = true
    }
    val dismissActions = {
        Log.d("ContactDetailPageTester", "关闭详情页操作工具栏")
        showContextMenu = false
        selectedField = null
        showPlatformContextMenu = false
        selectedPlatform = null
    }
    // 平台信息长按 ListPopup 的操作回调（替代原底部悬浮工具栏）
    val platformCopyAction: () -> Unit = {
        val (fieldKey, pEntry) = selectedPlatform!!
        val pDisplayName = FIELD_DEF_MAP[fieldKey]?.displayName ?: fieldKey
        val copyText = pEntry.value ?: pEntry.jumpLink
        Methods.copyToClipboard(context, pDisplayName, copyText)
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
        showPlatformContextMenu = false
        selectedPlatform = null
    }
    val platformEditAction: () -> Unit = {
        // 平台编辑弹新版的平台编辑表单（AddPlatformWindowDialog EDIT 模式）。
        editingPlatform = selectedPlatform
        showEditPlatformDialog = true
        showPlatformContextMenu = false
        selectedPlatform = null
    }
    // 平台信息"同步信息"：去掉二次确认框，点击直接同步昵称与头像
    // （网络解析值优先，回落平台已有值；无可同步内容时提示）
    val platformSyncAction: () -> Unit = {
        val platformInfo = selectedPlatform
        showPlatformContextMenu = false
        selectedPlatform = null
        if (platformInfo == null) {
            Log.w("ContactDetailPage", "platformSyncAction: selectedPlatform is null, aborting")
        } else {
            val (pName, pEntry) = platformInfo
            scope.launch {
                try {
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
                    val resolvedName = platformNicknameOrNull(pName, resolveResult?.nickname, pEntry)
                    val savedName = platformNicknameOrNull(pName, pEntry.displayName, pEntry)
                    val resolvedAvatar = resolveResult?.avatarUrl?.takeIf { it.isNotBlank() }
                    if (resolvedName != null || resolvedAvatar != null) {
                        viewModel.addOrUpdatePlatform(contactId, pName, pEntry.copy(
                            displayName = resolvedName ?: savedName,
                            avatarUrl = resolvedAvatar ?: pEntry.avatarUrl
                        ))
                    }
                    val freshContact = viewModel.getContactById(contactId) ?: return@launch
                    val newName = resolvedName ?: savedName
                    var avatarPath: String? = null
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
        }
    }
    val platformDeleteAction: () -> Unit = {
        showPlatformContextMenu = false
        val deletedEntry = selectedPlatform
        selectedPlatform = null
        if (deletedEntry != null) {
            val platformKey = deletedEntry.first
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
        }
    }
    val openContactAction: (String?, String, String) -> Unit = { fieldKey, value, jumpLink ->
        Log.d("ContactDetailPageTester", "单击直接跳转，fieldKey=$fieldKey")
        dismissActions()
        val action = buildLaunchAction(fieldKey.orEmpty(), value, jumpLink)
        scope.launch { executeLaunchAction(context, action) }
    }
    BackHandler(enabled = showContextMenu || showPlatformContextMenu, onBack = dismissActions)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        Log.d("ContactDetailPageTester", "详情页恢复，重读平台资料")
        viewModel.loadContact(contactId)
    }

    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val contact = contactWithFields?.contact
    val fields = contactWithFields?.fieldValues ?: emptyList()

    val currentCountryName = fields.firstOrNull { it.fieldKey == "country" }?.value

    // 头像位图（异步加载）：本地 avatarPath 优先，其次远程 avatarUrl
    var avatarBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val localAvatarPath = contact?.avatarPath
    val remoteAvatarUrl = contact?.avatarUrl
    LaunchedEffect(localAvatarPath, remoteAvatarUrl, avatarVersion) {
        avatarBitmap = if (!localAvatarPath.isNullOrBlank()) {
            Methods.loadAvatarBitmap(localAvatarPath, context)
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

    // 更多菜单选项（编辑入口已由顶栏编辑按钮承担）
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
            SmallTopAppBar(
                title = "",
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = {
                    // Collection membership keeps its existing accent indication.
                    IconButton(onClick = { showCollectionPicker = true }) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "添加到名片夹",
                            tint = if (contactCollectionIds.isNotEmpty())
                                MiuixTheme.colorScheme.primary
                            else LocalContentColor.current,
                        )
                    }
                    // 编辑名片：只编辑姓名/备注/介绍等文本，其余在本页直接编辑
                    IconButton(onClick = {
                        Log.d("ContactDetailPageTester", "打开文本编辑页")
                        onNavigateToEdit(contactId)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "编辑名片",
                            tint = MiuixTheme.colorScheme.primary,
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
                onDismissActions = dismissActions,
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
                // 点头像 → 进入头像页（查看原图 / 编辑 / 清空，替代原头像预览弹窗）
                Log.d("ContactDetailPageTester", "点头像进入头像页, contactId=$contactId")
                onNavigateToAvatar(contactId)
            },
            onEditNameClick = {
                Log.d("ContactDetailPage", "Edit name clicked for contact ${contact?.id}")
                onNavigateToEdit(contactId)
            },
            onFieldClick = { field -> openContactAction(field.fieldKey, field.value, "") },
            onFieldLongPress = selectFieldActions,
            onPlatformClick = { fieldKey, entry ->
                openContactAction(fieldKey, entry.value.orEmpty(), entry.jumpLink)
            },
            onPlatformLongPress = selectPlatformActions,
            onAddPlatformClick = {
                Log.d("ContactDetailPageTester", "打开添加平台页面")
                onNavigateToAddPlatform()
            },
            onBioClick = { onNavigateToEdit(contactId) },
            onTagsClick = { showTagPicker = true },
            onAiTagsClick = lambda@{
                // [P1-7] 防止重复触发:正在生成中点按无副作用 + 提示
                if (aiTagLoading) {
                    Toast.makeText(context, "AI 正在生成中…", Toast.LENGTH_SHORT).show()
                    return@lambda
                }
                // [修复防御]: 用户选"按钮始终显示,无 bio 时弹错"。按钮永远可点;
                // 这里前置校验 bio:为空时不调 generateAiTags(否则 ViewModel 内仍要走完整 try-catch),
                // 直接 toast + 跳转统一编辑器,引导用户去补内容。
                val bio = contact?.bio
                if (bio.isNullOrBlank()) {
                    Toast.makeText(context, "请先填写个人介绍,AI 才能更准确推荐", Toast.LENGTH_SHORT).show()
                    onNavigateToEdit(contactId)
                } else {
                    showAiTagPreview = true
                    viewModel.generateAiTags(contactId)
                }
            },
            onBasicInfoCellClick = { fieldKey, currentValue ->
                basicInfoEditField = fieldKey
                basicInfoEditCurrent = currentValue
            },
            selectedPlatformKey = selectedPlatform?.first,
            onDismissPlatformActions = dismissActions,
            onPlatformCopy = platformCopyAction,
            onPlatformEdit = platformEditAction,
            onPlatformSync = platformSyncAction,
            onPlatformDelete = platformDeleteAction,
        )
    }

    // 基础信息编辑 Dialog(性别 / 生日 / 国家 / 地区) —— 详情页直接编辑
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
        onConfirm = { name ->
            viewModel.updateCountry(contactId, name)
            basicInfoEditField = null
        },
    )
    RegionPickerDialog(
        show = basicInfoEditField == "region",
        current = basicInfoEditCurrent,
        countryName = currentCountryName,
        contactId = contactId,
        onDismiss = { basicInfoEditField = null },
        onConfirm = { value, path ->
            viewModel.updateRegion(contactId, currentCountryName, value, path)
            basicInfoEditField = null
        },
    )

    // 名片夹关联已提前到 Scaffold 之前(详见顶部),用于 ⭐ tint 与 CollectionPickerDialog。

    ContactDetailPageDialogs(
        contactId = contactId,
        viewModel = viewModel,
        contactWithFields = contactWithFields,
        contactCollectionIds = contactCollectionIds,
        // 对话框显示状态
        showFieldDeleteDialog = showFieldDeleteDialog,
        showEditPlatformDialog = showEditPlatformDialog,
        showCollectionPicker = showCollectionPicker,
        showContactPicker = showContactPicker,
        // 对话框数据
        selectedField = selectedField,
        editingPlatform = editingPlatform,
        selectedExistingContact = selectedExistingContact,
        // 回调
        onDismissFieldDelete = { showFieldDeleteDialog = false; selectedField = null },
        onDeleteField = { field ->
            viewModel.deleteFieldValue(contactId, field.valueId)
            viewModel.reloadContact(contactId)
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
    )

    // ====== 标签 Dialogs（详情页直接编辑） ======
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
