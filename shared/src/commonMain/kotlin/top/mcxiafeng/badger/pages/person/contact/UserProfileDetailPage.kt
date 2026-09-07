package top.mcxiafeng.badger.pages.person.contact

import top.mcxiafeng.badger.platform.SystemShare
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.AppViewModel
import top.mcxiafeng.badger.data.model.PlatformEntry
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity as UserProfile
import top.mcxiafeng.badger.data.repository.ContactMapper
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.network.kindCanSync
import top.mcxiafeng.badger.platform.ImageFiles
import top.mcxiafeng.badger.platform.PlatformImage
import top.mcxiafeng.badger.platform.loadOrientedImage
import top.mcxiafeng.badger.platform.rememberImagePickerLauncher
import top.mcxiafeng.badger.utils.Methods
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Share2
import top.mcxiafeng.badger.di.KoinComponentBy
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.platform.showToast
import top.mcxiafeng.badger.platform.BackHandler
import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.mcxiafeng.badger.shared.util.nowMs
import top.mcxiafeng.badger.shared.util.nowMs

private const val TAG = "UserProfileDetailPage"

/**
 * 网络解析平台 entry + 把解析到的 displayName/avatarUrl 回写 entry。
 *
 * 共用于 AddPlatform 自动同步（kindCanSync 触发）和 SyncOptionsBottomSheet 手动同步。
 */
internal data class PlatformSyncInfo(
    val resolvedName: String?,
    val resolvedAvatar: String?,
)

internal suspend fun resolvePlatformEntryForSync(
    userProfileRepository: UserProfileRepository,
    fieldKey: String,
    entry: PlatformEntry,
): PlatformSyncInfo {
    val content = entry.jumpLink.ifBlank { entry.value ?: "" }
    val contactType = FIELD_DEF_MAP[fieldKey]?.contactType
    val resolveResult = try {
        KoinComponentBy.get<ContactNetworkResolver>().identify(content)
    } catch (e: Exception) {
        BadgerLog.w(TAG, "网络解析失败: $fieldKey", e)
        null
    }
    val resolvedName = resolveResult?.nickname?.takeIf { it.isNotBlank() && it != "未知" }
    val resolvedAvatar = resolveResult?.avatarUrl?.takeIf { it.isNotBlank() }

    // [修复防御]: 解析到新 displayName/avatarUrl 同步回写 entry,避免下次同步重复解析。
    if (resolvedName != null || resolvedAvatar != null) {
        withContext(BadgerDispatchers.io) {
            userProfileRepository.updatePlatformField(
                fieldKey, entry.jumpLink, entry.value,
                resolvedName, resolvedAvatar, entry.originalLink
            )
        }
    }
    return PlatformSyncInfo(resolvedName, resolvedAvatar)
}

@Composable
internal fun UserProfileDetailPage(
    onBack: () -> Unit,
    onRefreshData: (() -> Unit)? = null,
    onOpenScannerForImport: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val viewModel: UserProfileDetailViewModel = koinViewModel()
    val userProfileRepository = viewModel.userProfileRepository
    val appViewModel: AppViewModel = koinViewModel()

    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showAddPlatformDialog by remember { mutableStateOf(false) }
    var showPlatformDetailDialog by remember { mutableStateOf(false) }
    var selectedPlatformDetail by remember { mutableStateOf<Pair<String, PlatformEntry>?>(null) }
    // 长按平台条目的上下文菜单
    var showPlatformContextMenu by remember { mutableStateOf(false) }
    var selectedPlatform by remember { mutableStateOf<Pair<String, PlatformEntry>?>(null) }
    // 编辑平台弹窗
    var showEditPlatformDialog by remember { mutableStateOf(false) }
    var editingPlatform by remember { mutableStateOf<Pair<String, PlatformEntry>?>(null) }
    // 同步选项底部弹窗
    var showSyncOptionsSheet by remember { mutableStateOf(false) }
    var syncPlatformInfo by remember { mutableStateOf<Pair<String, PlatformEntry>?>(null) }
    // 删除平台确认对话框
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    // [A5] 基础信息字段编辑入口（gender/birthday/country/region）
    var basicInfoEditField by remember { mutableStateOf<String?>(null) }
    var basicInfoEditCurrent by remember { mutableStateOf<String?>(null) }
    // [A5] 国家/地区关联：选国家成功后记录 externalId，地区 picker 需要前置
    var currentCountryName by remember { mutableStateOf<String?>(null) }
    var currentCountryExternalId by remember { mutableStateOf<Long?>(null) }
    // [A5] 背景图 URL 编辑器
    var showBackgroundUrlEditor by remember { mutableStateOf(false) }
    // [A6] 从平台解析导入我的名片
    var showImportFromPlatform by remember { mutableStateOf(false) }

    // 头像相关状态
    var isSettingAvatar by remember { mutableStateOf(false) }
    var avatarVersion by remember { mutableIntStateOf(0) }
    var showCropDialog by remember { mutableStateOf(false) }
    var cropSourceImage by remember { mutableStateOf<PlatformImage?>(null) }

    // [KMP K13c] 图片选择器：字节流 → EXIF 方向校正 → PlatformImage
    val pickAvatarLauncher = rememberImagePickerLauncher { bytes ->
        if (bytes != null) {
            scope.launch(BadgerDispatchers.io) {
                val image = loadOrientedImage(bytes)
                if (image != null) {
                    cropSourceImage = image
                    showCropDialog = true
                }
            }
        }
    }

    val onCropConfirm: (ByteArray) -> Unit = { croppedBytes ->
        showCropDialog = false
        isSettingAvatar = true
        scope.launch {
            try {
                val avatarPath = ImageFiles.saveAvatarImage(croppedBytes, "user_avatar.webp")
                if (avatarPath != null) {
                    // 从 DB 重新读取最新 profile，避免用过时的 UI 快照覆盖并发修改
                    val current = userProfileRepository.getUserProfileOnce() ?: UserProfile(
                        name = "用户",
                        updateTime = nowMs(),
                    )
                    val updated = current.copy(
                        avatarPath = avatarPath,
                        updateTime = nowMs()
                    )
                    userProfileRepository.saveUserProfile(updated)
                    profile = updated
                    avatarVersion++
                    // [修复防御]: 头像裁剪后通知 PersonPage 刷新我的名片。
                    appViewModel.refreshUserProfile()
                    onRefreshData?.invoke()
                    isSettingAvatar = false
                    showToast("头像已更新")
                                    } else {
                    isSettingAvatar = false
                    showToast("设置头像失败")
                }
            } catch (e: Exception) {
                BadgerLog.e(TAG, "设置头像失败", e)
                isSettingAvatar = false
                showToast("设置头像失败")
            }
        }
    }

    // 系统返回键：FloatingToolbar 显示时关闭 bar（不 null selected，避免 AnimatedVisibility 退出动画 NPE）
    BackHandler(enabled = showPlatformContextMenu) {
        showPlatformContextMenu = false
    }

    // 加载 UserProfile
    LaunchedEffect(Unit) {
        isLoading = true
        profile = userProfileRepository.getUserProfileOnce()
        isLoading = false
    }

    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    // 构建平台字段列表（fieldKey → PlatformEntry）
    val platformFields = remember(profile) {
        val p = profile ?: return@remember emptyList()
        ContactMapper.decodePlatformsMap(p.platformsJson)?.map { (key, entry) -> key to entry }
            ?.filter { it.second.jumpLink.isNotBlank() || !it.second.value.isNullOrBlank() }
        ?: emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "我的名片",
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
                    // 分享名片按钮（编辑入口已迁移到头像下方"点击名字"触发）
                    IconButton(onClick = {
                        val p = profile ?: return@IconButton
                                                val sb = StringBuilder()
                        sb.appendLine(p.name)
                        if (!p.bio.isNullOrBlank()) sb.appendLine(p.bio)
                        platformFields.forEach { (name, entry) ->
                            val display = buildString {
                                if (!entry.displayName.isNullOrBlank()) {
                                    append(entry.displayName)
                                    if (!entry.value.isNullOrBlank()) {
                                        append("（${entry.value}）")
                                    }
                                } else if (!entry.value.isNullOrBlank()) {
                                    append(entry.value)
                                } else {
                                    append(entry.jumpLink)
                                }
                            }
                            sb.appendLine("$name：$display")
                        }
                        val shareText = sb.toString().trim()
                        if (shareText.isNotBlank()) {
                            SystemShare.shareText("分享名片", shareText)
                        }
                    }) {
                        Icon(
                            imageVector = Lucide.Share2,
                            contentDescription = "分享名片"
                        )
                    }
                },
            )
        },
        floatingToolbar = {
            if (selectedPlatform != null) {
                UserProfileFloatingToolbar(
                    show = showPlatformContextMenu,
                    selectedPlatform = selectedPlatform,
                    onCopy = {
                        val (pName, pEntry) = selectedPlatform!!
                        val pDisplayName = FIELD_DEF_MAP[pName]?.displayName ?: pName
                        val copyText = pEntry.value ?: pEntry.jumpLink
                        Methods.copyToClipboard(pDisplayName, copyText)
                        showToast("已复制")
                        showPlatformContextMenu = false
                    },
                    onEdit = {
                        editingPlatform = selectedPlatform
                        showEditPlatformDialog = true
                        showPlatformContextMenu = false
                    },
                    onSync = run {
                        val (fieldKey, pEntry) = selectedPlatform!!
                        // sync 判定基于 platformKey 字符串（参见 kindCanSync），不再依赖 ContactType。
                        if (pEntry.jumpLink.isNotBlank() && fieldKey.kindCanSync) {
                            {
                                syncPlatformInfo = selectedPlatform
                                showPlatformContextMenu = false
                                showSyncOptionsSheet = true
                            }
                        } else null
                    },
                    onDelete = {
                        showPlatformContextMenu = false
                        showDeleteConfirmDialog = true
                    },
                )
            }
        },
        floatingToolbarPosition = ToolbarPosition.BottomCenter,
    ) { paddingValues ->
        UserProfileDetailContent(
            isLoading = isLoading,
            profile = profile,
            platformFields = platformFields,
            avatarVersion = avatarVersion,
            contentModifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
            paddingValues = paddingValues,
            onAvatarClick = {
                pickAvatarLauncher.launch()
            },
            // [修复防御]: 编辑入口已从 TopAppBar 的 IconButton 迁移到头像下方的「名字 + 简介」可点击区；
            // 触发后打开原 EditNameDialog（同时编辑昵称 + 简介），符合「点击昵称位置编辑」的交互。
            onEditNameClick = {
                                showEditNameDialog = true
            },
            onPlatformClick = { fieldKey, entry ->
                selectedPlatformDetail = fieldKey to entry
                showPlatformDetailDialog = true
            },
            onPlatformLongClick = { fieldKey, entry ->
                selectedPlatform = fieldKey to entry
                showPlatformContextMenu = true
            },
            onAddPlatformClick = { showAddPlatformDialog = true },
            // [A5] 基础信息字段编辑入口（性别/生日/国家/地区）
            onBasicInfoCellClick = { fieldKey, currentValue ->
                if (fieldKey == "gender") {
                    basicInfoEditField = "gender"
                } else {
                    basicInfoEditField = fieldKey
                }
                basicInfoEditCurrent = currentValue
            },
            onBackgroundUrlClick = { showBackgroundUrlEditor = true },
            // [A6] 从平台解析导入入口
            onImportFromPlatformClick = { showImportFromPlatform = true },
        )
    }

    UserProfileDetailDialogs(
        profile = profile,
        viewModel = viewModel,
        userProfileRepository = userProfileRepository,
        appViewModel = appViewModel,
        scope = scope,
        showEditNameDialog = showEditNameDialog,
        showAddPlatformDialog = showAddPlatformDialog,
        showPlatformDetailDialog = showPlatformDetailDialog,
        selectedPlatformDetail = selectedPlatformDetail,
        showEditPlatformDialog = showEditPlatformDialog,
        editingPlatform = editingPlatform,
        showSyncOptionsSheet = showSyncOptionsSheet,
        syncPlatformInfo = syncPlatformInfo,
        showDeleteConfirmDialog = showDeleteConfirmDialog,
        selectedPlatform = selectedPlatform,
        basicInfoEditField = basicInfoEditField,
        basicInfoEditCurrent = basicInfoEditCurrent,
        currentCountryName = currentCountryName,
        currentCountryExternalId = currentCountryExternalId,
        showBackgroundUrlEditor = showBackgroundUrlEditor,
        showImportFromPlatform = showImportFromPlatform,
        showCropDialog = showCropDialog,
        cropSourceImage = cropSourceImage,
        isSettingAvatar = isSettingAvatar,
        avatarVersion = avatarVersion,
        onProfileChange = { profile = it },
        onAvatarVersionChange = { avatarVersion = it },
        onIsSettingAvatarChange = { isSettingAvatar = it },
        onShowEditNameDialogChange = { showEditNameDialog = it },
        onShowAddPlatformDialogChange = { showAddPlatformDialog = it },
        onShowPlatformDetailDialogChange = { showPlatformDetailDialog = it },
        onSelectedPlatformDetailChange = { selectedPlatformDetail = it },
        onShowEditPlatformDialogChange = { showEditPlatformDialog = it },
        onEditingPlatformChange = { editingPlatform = it },
        onShowSyncOptionsSheetChange = { showSyncOptionsSheet = it },
        onSyncPlatformInfoChange = { syncPlatformInfo = it },
        onShowDeleteConfirmDialogChange = { showDeleteConfirmDialog = it },
        onSelectedPlatformChange = { selectedPlatform = it },
        onBasicInfoEditFieldChange = { basicInfoEditField = it },
        onBasicInfoEditCurrentChange = { basicInfoEditCurrent = it },
        onCurrentCountryNameChange = { currentCountryName = it },
        onCurrentCountryExternalIdChange = { currentCountryExternalId = it },
        onShowBackgroundUrlEditorChange = { showBackgroundUrlEditor = it },
        onShowImportFromPlatformChange = { showImportFromPlatform = it },
        onShowCropDialogChange = { showCropDialog = it },
        onCropSourceImageChange = { cropSourceImage = it },
        onCropConfirm = onCropConfirm,
        onRefreshData = onRefreshData,
    )
}