package top.mcxiafeng.badger.pages.person.contact

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.ContactFieldDisplay
import top.mcxiafeng.badger.data.ContactWithFields
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.ScanResult
import top.mcxiafeng.badger.data.UserProfile
import top.mcxiafeng.badger.data.rememberContactRepository
import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.network.ContactType
import top.mcxiafeng.badger.network.adapter.PlatformAdapterRegistry
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ocr.PLATFORM_FIELD_KEYS
import top.mcxiafeng.badger.ocr.buildPlatformLink
import top.mcxiafeng.badger.ui.components.CropConfig
import top.mcxiafeng.badger.ui.components.CropMode
import top.mcxiafeng.badger.ui.components.DialogButtonRow
import top.mcxiafeng.badger.ui.components.FirstTimeHint
import top.mcxiafeng.badger.ui.components.ImageCropDialog
import top.mcxiafeng.badger.utils.HttpUtil
import top.mcxiafeng.badger.utils.Methods
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.mcxiafeng.badger.data.Contact
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        UserProfileDetailPage(onBack = onBack, onOpenScannerForImport = onOpenScannerForImport)
        return
    }

    val context = LocalContext.current
    val repository = rememberContactRepository()
    val scope = rememberCoroutineScope()

    var contactWithFields by remember(contactId) { mutableStateOf<ContactWithFields?>(null) }
    var isLoading by remember(contactId) { mutableStateOf(true) }
    var showMoreMenu by remember { mutableStateOf(false) }

    // 长按上下文菜单状态
    var showContextMenu by remember { mutableStateOf(false) }
    var selectedField by remember { mutableStateOf<ContactFieldDisplay?>(null) }
    var showFieldDeleteDialog by remember { mutableStateOf(false) }

    // 字段删除确认状态

    // 联系方式详情弹窗状态
    var showFieldDetailDialog by remember { mutableStateOf(false) }

    // 社交平台详情弹窗状态
    var showPlatformDetailDialog by remember { mutableStateOf(false) }
    var selectedPlatformDetail by remember { mutableStateOf<Pair<String, PlatformEntry>?>(null) }

    // 添加平台对话框状态
    var showAddPlatformDialog by remember { mutableStateOf(false) }

    // 长按平台上下文菜单状态
    var showPlatformContextMenu by remember { mutableStateOf(false) }
    var selectedPlatform by remember { mutableStateOf<Pair<String, PlatformEntry>?>(null) }

    // 平台同步选项弹窗状态
    var showSyncOptionsSheet by remember { mutableStateOf(false) }
    var syncPlatformInfo by remember { mutableStateOf<Pair<String, PlatformEntry>?>(null) }

    // 编辑字段值对话框状态
    var showEditFieldDialog by remember { mutableStateOf(false) }
    var editFieldValue by remember { mutableStateOf("") }
    // 编辑姓名对话框状态
    var showEditNameDialog by remember { mutableStateOf(false) }

    // 附加到已有联系人状态
    var showContactPicker by remember { mutableStateOf(false) }
    var selectedExistingContact by remember { mutableStateOf<Contact?>(null) }

    // 头像相关状态
    var isSettingAvatar by remember { mutableStateOf(false) }
    var avatarVersion by remember { mutableIntStateOf(0) }
    var showCropDialog by remember { mutableStateOf(false) }
    var cropSourceUri by remember { mutableStateOf<Uri?>(null) }

    // 加载数据
    suspend fun loadData() {
        isLoading = true
        val result = withContext(Dispatchers.IO) {
            repository.getContactWithFieldsById(contactId)
        }
        contactWithFields = result
        isLoading = false
    }

    // 图片选择器（选择头像）
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
                val currentContact = contactWithFields?.contact ?: return@launch
                val avatarFile = Methods.saveBitmapAsAvatar(context, croppedBitmap, "contact_${contactId}_avatar.webp")
                val updated = currentContact.copy(
                    avatarPath = avatarFile.absolutePath,
                    updateTime = System.currentTimeMillis()
                )
                repository.updateContact(updated)
                contactWithFields = contactWithFields?.copy(contact = updated)
                avatarVersion++
                Toast.makeText(context, "头像已更新", Toast.LENGTH_SHORT).show()
                Log.d("ContactDetailPage", "Avatar cropped and saved: ${avatarFile.absolutePath}")
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "设置头像失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 检查联系人所在的名片夹
    val scanResultsFlow = remember(contactId) { repository.getScanResultsByContact(contactId) }
    val scanResults by scanResultsFlow.collectAsState(initial = emptyList())
    val contactCollectionIds by remember(scanResults) {
        mutableStateOf(scanResults.map { it.collectionId }.distinct().toSet())
    }

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
        loadData()
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

    // 社交平台列表（从 Contact.platforms 渲染）
    val platformFields = remember(contact) {
        contact?.platforms?.map { (key, entry) -> key to entry }
            ?.filter { it.second.jumpLink.isNotBlank() || !it.second.value.isNullOrBlank() }
        ?: emptyList()
    }

    // 分享联系方式文本
    fun buildShareText(): String {
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
            // 长按联系方式时，底部显示悬浮操作栏
            if (showContextMenu && selectedField != null) {
                FloatingToolbar(cornerRadius = 16.dp) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        ToolbarAction(
                            icon = Icons.Default.ContentCopy,
                            label = "复制",
                            onClick = {
                                Methods.copyToClipboard(context, selectedField!!.fieldName, selectedField!!.value)
                                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                                showContextMenu = false
                                selectedField = null
                            }
                        )
                        ToolbarAction(
                            icon = Icons.Default.Edit,
                            label = "编辑",
                            onClick = {
                                editFieldValue = selectedField!!.value
                                showEditFieldDialog = true
                                showContextMenu = false
                            }
                        )
                        // 「同步信息」按钮：对有 adapter 的平台字段显示
                        if (selectedField!!.fieldKey != null) {
                            val platformKey = selectedField!!.fieldKey!!
                            val contactType = FIELD_DEF_MAP[platformKey]?.contactType
                            val adapter = contactType?.let { PlatformAdapterRegistry.getAdapter(it) }
                            if (adapter != null && adapter.canSync) {
                                ToolbarAction(
                                    icon = Icons.Default.Person,
                                    label = "同步信息",
                                    onClick = {
                                    isSettingAvatar = true
                                    val field = selectedField
                                    if (field == null) { isSettingAvatar = false; return@ToolbarAction }
                                    val fieldValue = field.value
                                    val currentContact = contact
                                    val type = contactType
                                    if (currentContact == null) { isSettingAvatar = false; return@ToolbarAction }
                                    Log.d("ContactDetailPage", "Syncing info for ${adapter.label}, canSync=${adapter.canSync}")

                                    scope.launch {
                                        try {
                                            // 优先使用 value（如 QQ 号），其次用 linkTemplate，最后用原始值
                                            val link = fieldValue.ifBlank {
                                                FIELD_DEF_MAP[platformKey]?.let { def ->
                                                    def.linkTemplate?.replace("%s", fieldValue) ?: buildPlatformLink(platformKey, fieldValue)
                                                }
                                            } ?: fieldValue
                                            val result = withContext(Dispatchers.IO) { adapter.resolve(link) }
                                            if (result != null) {
                                                var updated: Contact? = currentContact
                                                if (!result.avatarUrl.isNullOrBlank()) {
                                                    val bitmap = withContext(Dispatchers.IO) {
                                                        val headers = if (result.avatarUrl.contains("hdslb.com") || result.avatarUrl.contains("bilibili.com")) mapOf("Referer" to "https://space.bilibili.com/") else null
                                                        HttpUtil.downloadBitmap(result.avatarUrl, headers = headers)
                                                    }
                                                    if (bitmap != null) {
                                                        val avatarFile = Methods.saveBitmapAsAvatar(context, bitmap, "contact_${contactId}_avatar.webp")
                                                        updated = updated.copy(avatarPath = avatarFile.absolutePath, updateTime = System.currentTimeMillis())
                                                    }
                                                }
                                                if (!result.name.isNullOrBlank()) {
                                                    updated = updated.copy(name = result.name, updateTime = System.currentTimeMillis())
                                                }
                                                if (updated != currentContact) {
                                                    repository.updateContact(updated)
                                                    contactWithFields = contactWithFields?.copy(contact = updated)
                                                    avatarVersion++
                                                    isSettingAvatar = false
                                                    Toast.makeText(context, "同步成功", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    isSettingAvatar = false
                                                    Toast.makeText(context, "未获取到可同步的信息", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                isSettingAvatar = false
                                                Toast.makeText(context, "无法获取该平台信息", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            isSettingAvatar = false
                                            Toast.makeText(context, "同步失败", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    showContextMenu = false
                                    selectedField = null
                                }
                                )
                            }
                        }
                        ToolbarAction(
                            icon = Icons.Default.Delete,
                            label = "删除",
                            tint = Color.Red,
                            onClick = {
                                val field = selectedField
                                if (field == null) {
                                    showContextMenu = false
                                    return@ToolbarAction
                                }
                                showContextMenu = false
                                showFieldDeleteDialog = true
                            }
                        )
                    }
                }
            }
            // 长按样式时，底部显示悬浮操作栏
            if (showStyleContextMenu && selectedScanResult != null) {
                FloatingToolbar(cornerRadius = 16.dp) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        ToolbarAction(
                            icon = Icons.Default.Delete,
                            label = "删除",
                            tint = Color.Red,
                            onClick = {
                                val scanResultId = selectedScanResult?.id
                                showStyleContextMenu = false
                                selectedScanResult = null
                                if (scanResultId != null) {
                                    scope.launch(Dispatchers.IO) {
                                        repository.deleteScanResultById(scanResultId)
                                        withContext(Dispatchers.Main) { loadData() }
                                    }
                                }
                            }
                        )
                    }
                }
            }
            // 长按社交平台时，底部显示悬浮操作栏
            if (showPlatformContextMenu && selectedPlatform != null) {
                val (fieldKey, pEntry) = selectedPlatform!!
                val pDisplayName = FIELD_DEF_MAP[fieldKey]?.displayName ?: fieldKey
                FloatingToolbar(cornerRadius = 16.dp) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        ToolbarAction(
                            icon = Icons.Default.ContentCopy,
                            label = "复制",
                            onClick = {
                                val copyText = pEntry.value ?: pEntry.jumpLink
                                Methods.copyToClipboard(context, pDisplayName, copyText)
                                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                                showPlatformContextMenu = false
                                selectedPlatform = null
                            }
                        )
                        ToolbarAction(
                            icon = Icons.Default.Edit,
                            label = "编辑",
                            onClick = {
                                showPlatformContextMenu = false
                                showAddPlatformDialog = true
                                selectedPlatform = null
                            }
                        )
                        // 同步信息按钮：仅对支持同步的平台显示
                        val syncContactType = FIELD_DEF_MAP[fieldKey]?.contactType
                        val syncAdapter = syncContactType?.let { PlatformAdapterRegistry.getAdapter(it) }
                        if (pEntry.jumpLink.isNotBlank() && syncAdapter?.canSync == true) {
                            ToolbarAction(
                                icon = Icons.Default.Person,
                                label = "同步信息",
                                onClick = {
                                    syncPlatformInfo = selectedPlatform
                                    showPlatformContextMenu = false
                                    selectedPlatform = null
                                    showSyncOptionsSheet = true
                                    Log.d("ContactDetailPage", "Sync info requested for platform: $fieldKey")
                                }
                            )
                        }
                        ToolbarAction(
                            icon = Icons.Default.Delete,
                            label = "删除",
                            tint = Color.Red,
                            onClick = {
                                showPlatformContextMenu = false
                                scope.launch(Dispatchers.IO) {
                                    repository.removeContactPlatform(contactId, fieldKey)
                                    withContext(Dispatchers.Main) { loadData() }
                                }
                                selectedPlatform = null
                                onRefreshData?.invoke()
                            }
                        )
                    }
                }
            }
        },
        floatingToolbarPosition = ToolbarPosition.BottomCenter,
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (contact == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("联系人不存在", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = 32.dp
                )
            ) {
                // ========== 上方：头像 + 姓名区域 ==========
                item(key = "header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 头像（含相机图标提示）
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clickable {
                                    pickAvatarLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                        ) {
                            if (avatarBitmap != null) {
                                Image(
                                    bitmap = avatarBitmap!!.asImageBitmap(),
                                    contentDescription = "头像",
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = contact.name.take(1),
                                        style = MiuixTheme.textStyles.title1,
                                        color = MiuixTheme.colorScheme.primary
                                    )
                                }
                            }
                            // 相机图标覆盖层
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(MiuixTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CameraAlt,
                                    contentDescription = "更换头像",
                                    modifier = Modifier.size(14.dp),
                                    tint = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    Log.d("ContactDetailPage", "Edit name clicked for contact ${contact.id}")
                                    showEditNameDialog = true
                                }
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = contact.name,
                                style = MiuixTheme.textStyles.title1,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "编辑姓名",
                                modifier = Modifier.size(16.dp),
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }

                        if (!contact.note.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = contact.note,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onBackgroundVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        val dateStr = SimpleDateFormat(
                            "yyyy-MM-dd HH:mm",
                            Locale.getDefault()
                        ).format(Date(contact.createTime))
                        Text(
                            text = "创建于 $dateStr",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }

                // ========== 下方：联系方式分组（支持长按） ==========
                item(key = "long_press_hint") {
                    FirstTimeHint(
                        text = "长按联系方式可复制/编辑",
                        hintKey = "long_press_contact_field",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
                if (systemFields.isNotEmpty()) {
                    Log.i("Tester", "ContactDetailPage: $systemFields")
                    item(key = "system_section") {
                        ContactFieldSection(
                            title = "联系方式",
                            fields = systemFields,
                            onClick = { field ->
                                selectedField = field
                                showFieldDetailDialog = true
                            },
                            onLongPress = { field ->
                                selectedField = field
                                showStyleContextMenu = false
                                showPlatformContextMenu = false
                                showContextMenu = true
                            },
                        )
                    }
                }

                // ========== 社交平台（从 Contact.platforms 渲染） ==========
                if (platformFields.isNotEmpty()) {
                    item(key = "platforms_section") {
                        SmallTitle(text = "社交平台")
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp),
                        ) {
                            platformFields.forEach { (fieldKey, entry) ->
                                val displayName = FIELD_DEF_MAP[fieldKey]?.displayName ?: fieldKey
                                val summary = buildString {
                                    if (!entry.displayName.isNullOrBlank()) {
                                        append(entry.displayName)
                                        if (!entry.value.isNullOrBlank()) append("（${entry.value}）")
                                    } else if (!entry.value.isNullOrBlank()) {
                                        append(entry.value)
                                    } else {
                                        append(entry.jumpLink)
                                    }
                                }
                                LongPressArrowPreference(
                                    title = displayName,
                                    summary = summary,
                                    onClick = {
                                        selectedPlatformDetail = fieldKey to entry
                                        showPlatformDetailDialog = true
                                    },
                                    onLongClick = {
                                        selectedPlatform = fieldKey to entry
                                        showContextMenu = false
                                        showStyleContextMenu = false
                                        showPlatformContextMenu = true
                                    }
                                )
                            }
                            ArrowPreference(
                                title = "添加社交平台",
                                summary = "添加对方的社交账号",
                                onClick = { showAddPlatformDialog = true }
                            )
                        }
                    }
                } else {
                    // 没有平台时也显示添加入口
                    item(key = "platforms_add") {
                        SmallTitle(text = "社交平台")
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp),
                        ) {
                            ArrowPreference(
                                title = "添加社交平台",
                                summary = "添加对方的社交账号",
                                onClick = { showAddPlatformDialog = true }
                            )
                        }
                    }
                }

                if (customFields.isNotEmpty()) {
                    item(key = "custom_section") {
                        ContactFieldSection(
                            title = "自定义信息",
                            fields = customFields,
                            onClick = { field ->
                                selectedField = field
                                showFieldDetailDialog = true
                            },
                            onLongPress = { field ->
                                selectedField = field
                                showStyleContextMenu = false
                                showPlatformContextMenu = false
                                showContextMenu = true
                            },
                        )
                    }
                }

                // ========== 扫描记录（ScanResult） ==========
                item(key = "styles") {
                    SmallTitle(text = "扫描记录")
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                    ) {
                        scanResults.forEachIndexed { index, scanResult ->
                            val dateLabel = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                .format(Date(scanResult.scannedTime))
                            LongPressArrowPreference(
                                title = "记录${index + 1}",
                                summary = dateLabel,
                                showArrow = false,
                                onClick = {
                                    Log.d("ContactDetail", "Style clicked: ${scanResult.id}")
                                },
                                onLongClick = {
                                    selectedField = null
                                    selectedScanResult = scanResult
                                    showContextMenu = false
                                    showStyleContextMenu = true
                                }
                            )
                        }
                    }
                }

                // ========== 添加至名片夹 ==========
                item(key = "add_to_collection") {
                    SmallTitle(text = "名片夹")
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                    ) {
                        ArrowPreference(
                            title = "添加到名片夹",
                            summary = if (contactCollectionIds.isEmpty()) "未添加" else "已添加 ${contactCollectionIds.size} 个名片夹",
                            onClick = { showCollectionPicker = true }
                        )
                    }
                }
            }
        }
    }

    // 字段删除确认对话框
    if (showFieldDeleteDialog && selectedField != null) {
        val field = selectedField!!
        WindowDialog(
            show = true,
            title = "删除联系方式",
            summary = "确定要删除「${field.fieldName}」吗？此操作不可撤销。",
            onDismissRequest = { showFieldDeleteDialog = false; selectedField = null }
        ) {
            DialogButtonRow(
                positiveText = "删除",
                onNegative = { showFieldDeleteDialog = false; selectedField = null },
                onPositive = {
                    showFieldDeleteDialog = false
                    val f = selectedField
                    selectedField = null
                    if (f != null) {
                        scope.launch(Dispatchers.IO) {
                            val allValues = repository.getFieldValuesByContactOnce(contactId)
                            val target = allValues.find { it.id == f.valueId }
                            if (target != null) {
                                repository.deleteFieldValue(target)
                                withContext(Dispatchers.Main) { loadData() }
                            }
                        }
                    }
                },
                isDestructive = true
            )
        }
    }

    // 编辑字段值对话框
    if (showEditFieldDialog && selectedField != null) {
        val field = selectedField!!
        WindowDialog(
            show = showEditFieldDialog,
            title = "编辑${field.fieldName}",
            summary = field.value,
            onDismissRequest = { showEditFieldDialog = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                TextField(
                    value = editFieldValue,
                    onValueChange = { editFieldValue = it },
                    label = field.fieldName,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
                DialogButtonRow(
                    positiveText = "保存",
                    onNegative = { showEditFieldDialog = false },
                    onPositive = {
                        val newValue = editFieldValue.trim()
                        if (newValue.isNotBlank() && newValue != field.value) {
                            scope.launch(Dispatchers.IO) {
                                val allValues = repository.getFieldValuesByContactOnce(contactId)
                                val target = allValues.find { it.id == field.valueId }
                                if (target != null) {
                                    repository.updateFieldValue(
                                        target.copy(value = newValue, updateTime = System.currentTimeMillis())
                                    )
                                    withContext(Dispatchers.Main) { loadData() }
                                }
                            }
                        }
                        showEditFieldDialog = false
                        selectedField = null
                    }
                )
            }
        }
    }

    // 编辑姓名对话框
    if (showEditNameDialog && contact != null) {
        WindowDialog(
            show = showEditNameDialog,
            title = "编辑姓名",
            summary = "",
            onDismissRequest = { showEditNameDialog = false },
        ) {
            var editName by remember(contact) { mutableStateOf(contact.name) }
            Column(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = "姓名",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                DialogButtonRow(
                    positiveText = "保存",
                    onNegative = { showEditNameDialog = false },
                    onPositive = {
                        val newName = editName.trim()
                        if (newName.isNotBlank()) {
                            Log.d("ContactDetailPage", "Saving new name: $newName for contact ${contact.id}")
                            val updated = contact.copy(name = newName, updateTime = System.currentTimeMillis())
                            scope.launch(Dispatchers.IO) {
                                repository.updateContact(updated)
                            }
                            contactWithFields = contactWithFields?.copy(contact = updated)
                        }
                        showEditNameDialog = false
                    }
                )
            }
        }
    }
    // 联系方式详情弹窗
    if (showFieldDetailDialog && selectedField != null) {
        FieldDetailDialog(
            field = selectedField!!,
            show = showFieldDetailDialog,
            onDismiss = {
                showFieldDetailDialog = false
                selectedField = null
            }
        )
    }

    // 社交平台详情弹窗
    selectedPlatformDetail?.let { (platformName, entry) ->
        PlatformDetailDialog(
            show = showPlatformDetailDialog,
            platformName = platformName,
            entry = entry,
            onDismiss = {
                showPlatformDetailDialog = false
                selectedPlatformDetail = null
            }
        )
    }

    // 添加社交平台对话框
    if (showAddPlatformDialog) {
        AddPlatformWindowDialog(
            show = showAddPlatformDialog,
            mode = AddEditMode.ADD,
            existingProfile = contact?.platforms?.let { UserProfile(platforms = it) },
            onDismiss = { showAddPlatformDialog = false },
            onConfirm = { fieldKey, entry ->
                showAddPlatformDialog = false
                scope.launch(Dispatchers.IO) {
                    repository.updateContactPlatform(contactId, fieldKey, entry)
                    withContext(Dispatchers.Main) { loadData() }
                }
                onRefreshData?.invoke()
            }
        )
    }

// 添加到名片夹弹窗（B站收藏夹风格）
    if (showCollectionPicker) {
        CollectionPickerDialog(
            repository = repository,
            contactId = contactId,
            currentCollectionIds = contactCollectionIds,
            onDismiss = { showCollectionPicker = false },
            onConfirm = { addedIds, removedIds ->
                scope.launch {
                    for (collectionId in addedIds) {
                        repository.addContactToCollection(
                            contactId = contactId,
                            collectionId = collectionId,
                            sourceType = "manual"
                        )
                    }
                    for (collectionId in removedIds) {
                        repository.removeContactFromCollection(contactId, collectionId)
                    }
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
                }
            }
        )
    }

    // 附加到已有联系人：联系人选择器
    if (showContactPicker) {
        ContactDetailPickerDialog(
            repository = repository,
            excludeContactId = contactId,
            onDismiss = { showContactPicker = false },
            onContactSelected = { targetContact ->
                selectedExistingContact = targetContact
                showContactPicker = false
            }
        )
    }

    // 附加到已有联系人：字段附加确认
    if (selectedExistingContact != null && contactWithFields != null) {
        val existing = selectedExistingContact!!
        val sourceData = contactWithFields!!
        ContactDetailAttachFieldDialog(
            sourceContact = sourceData.contact,
            sourceFields = sourceData.fieldValues,
            existingContact = existing,
            repository = repository,
            onDismiss = {
                selectedExistingContact = null
            },
            onConfirm = { selectedFieldKeys, selectedCustomFieldIds ->
                scope.launch(Dispatchers.IO) {
                    attachCurrentContactToExisting(
                        repository = repository,
                        sourceContact = sourceData.contact,
                        sourceFields = sourceData.fieldValues,
                        existingContact = existing,
                        selectedFieldKeys = selectedFieldKeys,
                        selectedCustomFieldIds = selectedCustomFieldIds
                    )
                    withContext(Dispatchers.Main) {
                        selectedExistingContact = null
                        loadData()
                        Toast.makeText(context, "已成功附加到 ${existing.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Avatar crop dialog
    if (showCropDialog && cropSourceUri != null) {
        Dialog(
            onDismissRequest = { },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnClickOutside = false
            )
        ) {
            ImageCropDialog(
                imageUri = cropSourceUri!!,
                cropConfig = CropConfig(mode = CropMode.AVATAR, outputWidth = 256, outputHeight = 256),
                onConfirm = onCropConfirm,
                onDismiss = { cropSourceUri = null }
            )
        }
    }

    // 同步选项底部弹窗
    if (showSyncOptionsSheet && syncPlatformInfo != null) {
        val currentSyncInfo = syncPlatformInfo!!
        SyncOptionsBottomSheet(
            platformInfo = currentSyncInfo,
            currentProfile = null,
            onDismiss = {
                showSyncOptionsSheet = false
                syncPlatformInfo = null
            },
            onConfirm = { syncName, syncAvatar ->
                showSyncOptionsSheet = false
                syncPlatformInfo = null
                scope.launch(Dispatchers.Main) {
                    try {
                        val (pName, pEntry) = currentSyncInfo
                        val currentContact = contact ?: return@launch

                        // 网络解析获取最新信息
                        val resolveResult = withContext(Dispatchers.IO) {
                            try {
                                val content = pEntry.jumpLink.ifBlank { pEntry.value ?: "" }
                                val contactType = FIELD_DEF_MAP[pName]?.contactType
                                ContactNetworkResolver.getResultInfo(content, mutableMapOf(), type = contactType)
                            } catch (_: Exception) { null }
                        }
                        val resolvedName = resolveResult?.nickname?.takeIf { it.isNotBlank() && it != "未知" }
                        val resolvedAvatar = resolveResult?.avatarUrl?.takeIf { it.isNotBlank() }

                        // 更新平台 entry
                        if (resolvedName != null || resolvedAvatar != null) {
                            withContext(Dispatchers.IO) {
                                repository.updateContactPlatform(
                                    contactId, pName,
                                    pEntry.copy(
                                        displayName = resolvedName ?: pEntry.displayName,
                                        avatarUrl = resolvedAvatar ?: pEntry.avatarUrl
                                    )
                                )
                            }
                        }

                        var updated = currentContact

                        // 同步名字到联系人
                        if (syncName) {
                            val newName = resolvedName ?: pEntry.displayName?.takeIf { it.isNotBlank() }
                            if (newName != null) {
                                updated = updated.copy(name = newName, updateTime = System.currentTimeMillis())
                            }
                        }

                        // 同步头像到联系人
                        if (syncAvatar) {
                            val avatarToUse = resolvedAvatar ?: pEntry.avatarUrl
                            if (!avatarToUse.isNullOrBlank()) {
                                isSettingAvatar = true
                                val bitmap = withContext(Dispatchers.IO) {
                                    val headers = if (avatarToUse.contains("hdslb.com") || avatarToUse.contains("bilibili.com"))
                                        mapOf("Referer" to "https://space.bilibili.com/") else null
                                    HttpUtil.downloadBitmap(avatarToUse, headers = headers)
                                }
                                if (bitmap != null) {
                                    val avatarFile = Methods.saveBitmapAsAvatar(context, bitmap, "contact_${contactId}_avatar.webp")
                                    updated = updated.copy(avatarPath = avatarFile.absolutePath, updateTime = System.currentTimeMillis())
                                }
                                isSettingAvatar = false
                            }
                        }

                        if (updated != currentContact) {
                            withContext(Dispatchers.IO) { repository.updateContact(updated) }
                            contactWithFields = contactWithFields?.copy(contact = updated)
                            avatarVersion++
                            Toast.makeText(context, "同步成功", Toast.LENGTH_SHORT).show()
                            Log.d("ContactDetailPage", "Sync success for $pName: name=${updated.name}")
                        } else {
                            Toast.makeText(context, "未获取到可同步的信息", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        isSettingAvatar = false
                        Toast.makeText(context, "同步失败", Toast.LENGTH_SHORT).show()
                        Log.e("ContactDetailPage", "Sync failed", e)
                    }
                }
            }
        )
    }

}