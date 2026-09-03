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
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
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
import top.mcxiafeng.badger.ui.components.CropConfig
import top.mcxiafeng.badger.ui.components.CropMode
import top.mcxiafeng.badger.ui.components.DialogButtonRow
import top.mcxiafeng.badger.ui.components.ImageCropDialog
import top.mcxiafeng.badger.utils.HttpUtil
import top.mcxiafeng.badger.utils.Methods
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import top.mcxiafeng.badger.pages.person.contact.detail.SyncOptionsBottomSheet
import top.mcxiafeng.badger.pages.person.contact.dialogs.AddEditMode
import top.mcxiafeng.badger.pages.person.contact.dialogs.AddPlatformWindowDialog
import top.mcxiafeng.badger.pages.person.contact.dialogs.BirthdayPickerDialog
import top.mcxiafeng.badger.pages.person.contact.dialogs.CountryPickerDialog
import top.mcxiafeng.badger.pages.person.contact.dialogs.GenderPickerDialog
import top.mcxiafeng.badger.pages.person.contact.dialogs.ImportFromPlatformDialog
import top.mcxiafeng.badger.pages.person.contact.dialogs.PlatformDetailDialog
import top.mcxiafeng.badger.pages.person.contact.dialogs.RegionPickerDialog

private const val TAG = "UserProfileDetailPage"

/**
 * 网络解析平台 entry + 把解析到的 displayName/avatarUrl 回写 entry。
 *
 * 共用于 AddPlatform 自动同步（kindCanSync 触发）和 SyncOptionsBottomSheet 手动同步。
 */
private data class PlatformSyncInfo(
    val resolvedName: String?,
    val resolvedAvatar: String?,
)

private suspend fun resolvePlatformEntryForSync(
    userProfileRepository: UserProfileRepository,
    fieldKey: String,
    entry: PlatformEntry,
): PlatformSyncInfo {
    val content = entry.jumpLink.ifBlank { entry.value ?: "" }
    val contactType = FIELD_DEF_MAP[fieldKey]?.contactType
    val resolveResult = try {
        ContactNetworkResolver.getResultInfo(content, mutableMapOf(), type = contactType)
    } catch (e: Exception) {
        Log.w(TAG, "网络解析失败: $fieldKey", e)
        null
    }
    val resolvedName = resolveResult?.nickname?.takeIf { it.isNotBlank() && it != "未知" }
    val resolvedAvatar = resolveResult?.avatarUrl?.takeIf { it.isNotBlank() }

    // [修复防御]: 解析到新 displayName/avatarUrl 同步回写 entry,避免下次同步重复解析。
    if (resolvedName != null || resolvedAvatar != null) {
        withContext(Dispatchers.IO) {
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
    val context = LocalContext.current
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
    var cropSourceUri by remember { mutableStateOf<Uri?>(null) }

    // 图片选择器
    val pickAvatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            cropSourceUri = uri
            showCropDialog = true
        }
    }

    val onCropConfirm: (Bitmap) -> Unit = { croppedBitmap ->
        showCropDialog = false
        isSettingAvatar = true
        scope.launch {
            try {
                val avatarFile = Methods.saveBitmapAsAvatar(context, croppedBitmap, "user_avatar.webp")
                if (avatarFile != null) {
                    // 从 DB 重新读取最新 profile，避免用过时的 UI 快照覆盖并发修改
                    val current = userProfileRepository.getUserProfileOnce() ?: UserProfile(
                        name = "用户",
                        updateTime = System.currentTimeMillis(),
                    )
                    val updated = current.copy(
                        avatarPath = avatarFile.absolutePath,
                        updateTime = System.currentTimeMillis()
                    )
                    userProfileRepository.saveUserProfile(updated)
                    profile = updated
                    avatarVersion++
                    // [修复防御]: 头像裁剪后通知 PersonPage 刷新我的名片。
                    appViewModel.refreshUserProfile()
                    onRefreshData?.invoke()
                    isSettingAvatar = false
                    Toast.makeText(context, "头像已更新", Toast.LENGTH_SHORT).show()
                                    } else {
                    isSettingAvatar = false
                    Toast.makeText(context, "设置头像失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "设置头像失败", e)
                isSettingAvatar = false
                Toast.makeText(context, "设置头像失败", Toast.LENGTH_SHORT).show()
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                            val intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(intent, "分享名片"))
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
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
                        Methods.copyToClipboard(context, pDisplayName, copyText)
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
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
                pickAvatarLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
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

    // 编辑昵称对话框
    if (showEditNameDialog) {
        WindowDialog(
            show = true,
            title = "编辑昵称",
            summary = "",
            onDismissRequest = { showEditNameDialog = false },
    ) {
        var editName by remember(profile) { mutableStateOf(profile?.name ?: "") }
        var editBio by remember(profile) { mutableStateOf(profile?.bio ?: "") }

        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = editName,
                onValueChange = { editName = it },
                label = "昵称",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = editBio,
                onValueChange = { editBio = it },
                label = "简介",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            DialogButtonRow(
                positiveText = "保存",
                onNegative = { showEditNameDialog = false },
                onPositive = {
                    // 从 DB 重新读取最新 profile，避免用过时的 UI 快照覆盖并发修改
                    scope.launch(Dispatchers.IO) {
                        val current = userProfileRepository.getUserProfileOnce() ?: UserProfile(
                        name = "用户",
                        updateTime = System.currentTimeMillis(),
                    )
                        val updated = current.copy(
                            name = editName.ifBlank { "用户" },
                            bio = editBio.ifBlank { null },
                            updateTime = System.currentTimeMillis()
                        )
                        userProfileRepository.saveUserProfile(updated)
                        withContext(Dispatchers.Main) {
                            profile = userProfileRepository.getUserProfileOnce() ?: updated
                        }
                    }
                    showEditNameDialog = false
                }
            )
        }
    }
    }

    // 平台详情弹窗
    if (showPlatformDetailDialog) selectedPlatformDetail?.let { (platformName, entry) ->
        PlatformDetailDialog(
            show = true,
            platformName = platformName,
            entry = entry,
            onDismiss = {
                showPlatformDetailDialog = false
                selectedPlatformDetail = null
            }
        )
    }

    // 添加平台对话框
    if (showAddPlatformDialog) AddPlatformWindowDialog(
        show = true,
        mode = AddEditMode.ADD,
        existingProfile = profile,
        onDismiss = { showAddPlatformDialog = false },
        onConfirm = { fieldKey, entry ->
            showAddPlatformDialog = false
            scope.launch(Dispatchers.IO) {
                userProfileRepository.updatePlatformField(fieldKey, entry.jumpLink, entry.value, entry.displayName, entry.avatarUrl, entry.originalLink)
                val updated = userProfileRepository.getUserProfileOnce() ?: profile ?: UserProfile(
                    name = "用户",
                    updateTime = System.currentTimeMillis(),
                )
                withContext(Dispatchers.Main) { profile = updated }

                // 自动同步：当 profile 缺少头像或名字时，从新添加的 canSync 平台自动填充
                val currentProfile = userProfileRepository.getUserProfileOnce() ?: return@launch
                val needsAvatar = currentProfile.avatarPath.isNullOrBlank()
                val needsName = currentProfile.name.isBlank() || currentProfile.name == "用户"
                // sync 判定基于 platformKey 字符串（参见 kindCanSync）。
                if (fieldKey.kindCanSync && (needsAvatar || needsName)) {
                    try {
                        val (resolvedName, resolvedAvatar) = resolvePlatformEntryForSync(
                            userProfileRepository, fieldKey, entry
                        )

                        var newProfile = userProfileRepository.getUserProfileOnce() ?: currentProfile
                        if (needsName && resolvedName != null) {
                            newProfile = newProfile.copy(name = resolvedName, updateTime = System.currentTimeMillis())
                        }
                        if (needsAvatar && resolvedAvatar != null) {
                            val bitmap = HttpUtil.downloadBitmap(resolvedAvatar)
                            if (bitmap != null) {
                                val avatarFile = Methods.saveBitmapAsAvatar(context, bitmap, "user_avatar.webp")
                                if (avatarFile != null) {
                                    newProfile = newProfile.copy(avatarPath = avatarFile.absolutePath, updateTime = System.currentTimeMillis())
                                }
                            }
                        }
                        if (newProfile != userProfileRepository.getUserProfileOnce()) {
                            userProfileRepository.saveUserProfile(newProfile)
                            withContext(Dispatchers.Main) {
                                profile = userProfileRepository.getUserProfileOnce() ?: newProfile
                                avatarVersion++
                                // [修复防御]: 同步通知 PersonPage 刷新我的名片。
                                appViewModel.refreshUserProfile()
                                onRefreshData?.invoke()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Auto-sync failed from $fieldKey", e)
                    }
                }
            }
        }
    )

    // 编辑平台对话框
    if (showEditPlatformDialog) editingPlatform?.let { (platformName, entry) ->
        AddPlatformWindowDialog(
            show = true,
            mode = AddEditMode.EDIT,
            editingEntry = platformName to entry,
            onDismiss = {
                showEditPlatformDialog = false
                editingPlatform = null
            },
            onConfirm = { fieldKey, newEntry ->
                scope.launch(Dispatchers.IO) {
                    userProfileRepository.updatePlatformField(fieldKey, newEntry.jumpLink, newEntry.value, newEntry.displayName, newEntry.avatarUrl, newEntry.originalLink)
                    withContext(Dispatchers.Main) {
                        val updated = userProfileRepository.getUserProfileOnce() ?: profile ?: UserProfile(
                            name = "用户",
                            updateTime = System.currentTimeMillis(),
                        )
                        profile = updated
                    }
                }
                showEditPlatformDialog = false
                editingPlatform = null
            }
        )
    }

    // 同步选项底部弹窗
    if (showSyncOptionsSheet && syncPlatformInfo != null) {
        val currentSyncInfo = syncPlatformInfo!! // 先保存，避免在协程中被清空
        SyncOptionsBottomSheet(
            platformInfo = currentSyncInfo,
            currentProfile = profile,
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

                        // 解析平台内容并回写 entry 的 displayName/avatarUrl（共享复用 AddPlatform 自动同步逻辑）
                        val (resolvedName, resolvedAvatar) = resolvePlatformEntryForSync(
                            userProfileRepository, pName, pEntry
                        )

                        // updatePlatformField 已修改 DB 中的 platforms，重新读取以包含该更新
                        val current = withContext(Dispatchers.IO) { userProfileRepository.getUserProfileOnce() } ?: UserProfile(
                            name = "用户",
                            updateTime = System.currentTimeMillis(),
                        )

                        // 同步名字到我的名片
                        val newName = if (syncName) {
                            resolvedName ?: pEntry.displayName?.takeIf { it.isNotBlank() } ?: current.name
                        } else {
                            current.name
                        }

                        // 同步头像到我的名片
                        var newAvatarPath = current.avatarPath
                        val avatarToUse = resolvedAvatar ?: pEntry.avatarUrl
                        if (syncAvatar && !avatarToUse.isNullOrBlank()) {
                            isSettingAvatar = true
                            val bitmap = withContext(Dispatchers.IO) {
                                HttpUtil.downloadBitmap(avatarToUse)
                            }
                            if (bitmap != null) {
                                val avatarFile = Methods.saveBitmapAsAvatar(context, bitmap, "user_avatar.webp")
                                if (avatarFile != null) {
                                    newAvatarPath = avatarFile.absolutePath
                                }
                            }
                            isSettingAvatar = false
                        }

                        val updated = current.copy(
                            name = newName,
                            avatarPath = newAvatarPath,
                            updateTime = System.currentTimeMillis()
                        )
                        withContext(Dispatchers.IO) {
                            userProfileRepository.saveUserProfile(updated)
                        }
                        profile = withContext(Dispatchers.IO) { userProfileRepository.getUserProfileOnce() } ?: updated
                        avatarVersion++
                        // [修复防御]: 通知 PersonPage 列表/我的名片头像是同一份 UserProfile（id=1），
                        // Room 的 Flow 会自动重发，但 PersonRoute 持有的是 PersonViewModel 的 userProfile StateFlow，
                        // 跨页面不会自动同步；显式回调确保返回 PersonPage 时立刻刷新。
                        appViewModel.refreshUserProfile()
                        onRefreshData?.invoke()

                        Toast.makeText(context, "同步成功", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e(TAG, "同步失败", e)
                        isSettingAvatar = false
                        Toast.makeText(context, "同步失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // 删除平台确认对话框
    if (showDeleteConfirmDialog) {
        WindowDialog(
            show = true,
            title = "删除平台",
            summary = "确定要删除 ${FIELD_DEF_MAP[selectedPlatform?.first]?.displayName ?: selectedPlatform?.first ?: ""} 吗？此操作不可撤销。",
            onDismissRequest = {
                showDeleteConfirmDialog = false
                selectedPlatform = null
            },
    ) {
        DialogButtonRow(
            positiveText = "删除",
            onNegative = {
                showDeleteConfirmDialog = false
                selectedPlatform = null
            },
            onPositive = {
                showDeleteConfirmDialog = false
                val (pName, deletedEntry) = selectedPlatform ?: return@DialogButtonRow
                val currentAvatarPath = profile?.avatarPath
                val currentName = profile?.name ?: "用户"
                val deletedDisplayName = deletedEntry.displayName
                scope.launch(Dispatchers.IO) {
                    userProfileRepository.removePlatform(pName)
                    val updatedProfile = userProfileRepository.getUserProfileOnce() ?: profile
                    if (updatedProfile != null) {
                        val remainingPlatforms = ContactMapper.decodePlatformsMap(updatedProfile.platformsJson) ?: emptyMap()

                        // 头像回退：如果当前有头像，检查剩余平台是否有可用头像
                        var newAvatarPath = updatedProfile.avatarPath
                        if (currentAvatarPath != null) {
                            val fallbackEntry = remainingPlatforms.entries.firstOrNull {
                                !it.value.avatarUrl.isNullOrBlank()
                            }
                            if (fallbackEntry != null) {
                                val fallbackUrl = fallbackEntry.value.avatarUrl
                                val bitmap = if (!fallbackUrl.isNullOrBlank()) HttpUtil.downloadBitmap(fallbackUrl) else null
                                if (bitmap != null) {
                                    val avatarFile = Methods.saveBitmapAsAvatar(context, bitmap, "user_avatar.webp")
                                    newAvatarPath = avatarFile?.absolutePath
                                } else {
                                    Methods.deleteAvatarFile(currentAvatarPath)
                                    newAvatarPath = null
                                }
                            } else {
                                Methods.deleteAvatarFile(currentAvatarPath)
                                newAvatarPath = null
                            }
                        }

                        // 名字回退：如果当前名字来自被删除平台的 displayName，尝试从剩余平台获取
                        var newName = updatedProfile.name
                        if (deletedDisplayName != null && currentName == deletedDisplayName) {
                            val fallbackNameEntry = remainingPlatforms.entries.firstOrNull {
                                !it.value.displayName.isNullOrBlank()
                            }
                            newName = fallbackNameEntry?.value?.displayName ?: "用户"
                        }

                        val finalProfile = updatedProfile.copy(
                            name = newName,
                            avatarPath = newAvatarPath,
                            updateTime = System.currentTimeMillis()
                        )
                        userProfileRepository.saveUserProfile(finalProfile)
                    }
                    withContext(Dispatchers.Main) {
                        profile = userProfileRepository.getUserProfileOnce() ?: profile
                        avatarVersion++
                        // [修复防御]: 平台删除触发的头像回退也要通知 PersonPage。
                        appViewModel.refreshUserProfile()
                        onRefreshData?.invoke()
                    }
                }
                selectedPlatform = null
                Toast.makeText(context, "已删除 $pName", Toast.LENGTH_SHORT).show()
            },
            isDestructive = true
        )
    }
    }

    // [A5] 基础信息编辑 Dialogs（性别/生日/国家/地区）
    // [修复防御]: 提取公共的 updateProfileField 回调，消除 5 处重复的 refresh + notify 逻辑
    val onProfileFieldUpdated: (top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity) -> Unit = { fresh ->
        profile = fresh
        appViewModel.refreshUserProfile()
        onRefreshData?.invoke()
    }

    GenderPickerDialog(
        show = basicInfoEditField == "gender",
        current = basicInfoEditCurrent,
        onDismiss = { basicInfoEditField = null; basicInfoEditCurrent = null },
        onConfirm = { value ->
            basicInfoEditField = null
            basicInfoEditCurrent = null
            viewModel.updateProfileField("sex", value, onProfileFieldUpdated)
        },
    )
    BirthdayPickerDialog(
        show = basicInfoEditField == "birthday",
        current = basicInfoEditCurrent,
        onDismiss = { basicInfoEditField = null; basicInfoEditCurrent = null },
        onConfirm = { value ->
            basicInfoEditField = null
            basicInfoEditCurrent = null
            viewModel.updateProfileField("birthday", value, onProfileFieldUpdated)
        },
    )
    CountryPickerDialog(
        show = basicInfoEditField == "country",
        current = basicInfoEditCurrent,
        onDismiss = { basicInfoEditField = null; basicInfoEditCurrent = null },
        onConfirm = { name, externalId ->
            basicInfoEditField = null
            basicInfoEditCurrent = null
            currentCountryName = name
            currentCountryExternalId = externalId
            // [A5] 换国家时清空地区，避免地区不匹配新国家（对齐 ContactDetailPage 同策略）
            viewModel.updateProfileField("country", name, onProfileFieldUpdated)
        },
    )
    RegionPickerDialog(
        show = basicInfoEditField == "region",
        current = basicInfoEditCurrent,
        countryId = currentCountryExternalId,
        countryName = currentCountryName,
        onDismiss = { basicInfoEditField = null; basicInfoEditCurrent = null },
        onConfirm = { value ->
            basicInfoEditField = null
            basicInfoEditCurrent = null
            viewModel.updateProfileField("region", value, onProfileFieldUpdated)
        },
    )
    // [A5] 背景图 URL 手动编辑器
    if (showBackgroundUrlEditor) {
        var bgUrl by remember { mutableStateOf(profile?.backgroundURL ?: "") }
        WindowDialog(
            show = true,
            title = "背景图 URL",
            summary = "输入背景图网络地址，或点击清除移除当前背景",
            onDismissRequest = { showBackgroundUrlEditor = false },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = bgUrl,
                    onValueChange = { bgUrl = it },
                    label = "背景图 URL",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                DialogButtonRow(
                    positiveText = "保存",
                    onNegative = { showBackgroundUrlEditor = false },
                    onPositive = {
                        showBackgroundUrlEditor = false
                        viewModel.updateProfileField("backgroundURL", bgUrl.ifBlank { null }, onProfileFieldUpdated)
                    }
                )
            }
        }
    }

    // Avatar crop dialog
    if (showCropDialog && cropSourceUri != null) {
        Dialog(
            onDismissRequest = { showCropDialog = false; cropSourceUri = null },
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
                onDismiss = { showCropDialog = false; cropSourceUri = null }
            )
        }
    }

    // [A6] 从平台解析导入弹窗
    if (showImportFromPlatform) {
        ImportFromPlatformDialog(
            show = true,
            onDismiss = { showImportFromPlatform = false },
            onConfirm = { importedName, importedBio, importedAvatarPath ->
                showImportFromPlatform = false
                viewModel.importFromPlatform(importedName, importedBio, importedAvatarPath) { fresh ->
                    profile = fresh
                    if (importedAvatarPath != null) avatarVersion++
                    // [修复防御]: 跨页面(我的名片 / PersonPage)是同一份 UserProfile(id=1),
                    // 显式回调确保返回 PersonPage 时立刻刷新头像/昵称。
                    appViewModel.refreshUserProfile()
                    onRefreshData?.invoke()
                    Toast.makeText(context, "已从平台导入", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}