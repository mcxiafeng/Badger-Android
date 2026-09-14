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
import top.mcxiafeng.badger.network.canSyncViaManifest
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

private const val TAG = "UserProfileDetailPage"

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
    if (content.isBlank()) {
        BadgerLog.w(TAG, "网络解析跳过: $fieldKey 无可用输入")
        return PlatformSyncInfo(null, null)
    }
    val contactType = FIELD_DEF_MAP[fieldKey]?.contactType
    
    val resolveResult = withContext(BadgerDispatchers.io) {
        try {
            KoinComponentBy.get<ContactNetworkResolver>().identify(content)
        } catch (e: Exception) {
            BadgerLog.w(TAG, "网络解析失败: $fieldKey", e)
            null
        }
    }
    val resolvedName = resolveResult?.nickname?.takeIf { it.isNotBlank() && it != "未知" }
    val resolvedAvatar = resolveResult?.avatarUrl?.takeIf { it.isNotBlank() }

    
    
    
    
    if (resolvedName != null || resolvedAvatar != null) {
        val defLabel = FIELD_DEF_MAP[fieldKey]?.displayName
        withContext(BadgerDispatchers.io) {
            userProfileRepository.updatePlatformField(
                fieldKey, entry.jumpLink, entry.value,
                resolvedName ?: entry.displayName?.takeIf { it != defLabel },
                resolvedAvatar ?: entry.avatarUrl,
                entry.originalLink
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
    
    var showPlatformContextMenu by remember { mutableStateOf(false) }
    var selectedPlatform by remember { mutableStateOf<Pair<String, PlatformEntry>?>(null) }
    
    var showEditPlatformDialog by remember { mutableStateOf(false) }
    var editingPlatform by remember { mutableStateOf<Pair<String, PlatformEntry>?>(null) }
    
    var showSyncOptionsSheet by remember { mutableStateOf(false) }
    var syncPlatformInfo by remember { mutableStateOf<Pair<String, PlatformEntry>?>(null) }
    
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    
    var basicInfoEditField by remember { mutableStateOf<String?>(null) }
    var basicInfoEditCurrent by remember { mutableStateOf<String?>(null) }
    
    var currentCountryName by remember { mutableStateOf<String?>(null) }
    var currentCountryExternalId by remember { mutableStateOf<Long?>(null) }
    
    var showBackgroundUrlEditor by remember { mutableStateOf(false) }
    
    var showImportFromPlatform by remember { mutableStateOf(false) }

    
    var isSettingAvatar by remember { mutableStateOf(false) }
    var avatarVersion by remember { mutableIntStateOf(0) }
    var showCropDialog by remember { mutableStateOf(false) }
    var cropSourceImage by remember { mutableStateOf<PlatformImage?>(null) }

    
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
                    
                    val updated = userProfileRepository.editUserProfile { it.copy(avatarPath = avatarPath) }
                    profile = updated
                    avatarVersion++
                    
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

    
    BackHandler(enabled = showPlatformContextMenu) {
        showPlatformContextMenu = false
    }

    
    
    
    
    LaunchedEffect(Unit) {
        userProfileRepository.getUserProfile().collect {
            profile = it
            isLoading = false
        }
    }

    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    
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
                        
                        if (pEntry.jumpLink.isNotBlank() && fieldKey.canSyncViaManifest()) {
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
            
            onBasicInfoCellClick = { fieldKey, currentValue ->
                if (fieldKey == "gender") {
                    basicInfoEditField = "gender"
                } else {
                    basicInfoEditField = fieldKey
                }
                basicInfoEditCurrent = currentValue
            },
            onBackgroundUrlClick = { showBackgroundUrlEditor = true },
            
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