package top.mcxiafeng.badger.pages.person.contact

import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import top.yukonga.miuix.kmp.window.WindowDialog
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.UserProfile
import top.mcxiafeng.badger.data.rememberContactRepository
import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.network.adapter.PlatformAdapterRegistry
import top.mcxiafeng.badger.ui.components.CropConfig
import top.mcxiafeng.badger.ui.components.CropMode
import top.mcxiafeng.badger.ui.components.DialogButtonRow
import top.mcxiafeng.badger.ui.components.FirstTimeHint
import top.mcxiafeng.badger.ui.components.ImageCropDialog
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.utils.HttpUtil
import top.mcxiafeng.badger.utils.Methods
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.collections.get

@Composable
internal fun UserProfileDetailPage(
    onBack: () -> Unit,
    onOpenScannerForImport: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = rememberContactRepository()

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
                    val current = repository.getUserProfileOnce() ?: UserProfile(name = "用户")
                    val updated = current.copy(
                        avatarPath = avatarFile.absolutePath,
                        updateTime = System.currentTimeMillis()
                    )
                    repository.saveUserProfile(updated)
                    profile = updated
                    avatarVersion++
                    isSettingAvatar = false
                    Toast.makeText(context, "头像已更新", Toast.LENGTH_SHORT).show()
                    Log.d("Tester", "Avatar cropped and saved: ${avatarFile.absolutePath}")
                } else {
                    isSettingAvatar = false
                    Toast.makeText(context, "设置头像失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
        profile = repository.getUserProfileOnce()
        isLoading = false
    }

    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    // 构建平台字段列表（fieldKey → PlatformEntry）
    val platformFields = remember(profile) {
        val p = profile ?: return@remember emptyList()
        p.platforms?.map { (key, entry) -> key to entry }
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
                    // 编辑昵称按钮
                    IconButton(onClick = { showEditNameDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "编辑"
                        )
                    }
                    // 分享名片按钮
                    IconButton(onClick = {
                        val p = profile ?: return@IconButton
                        Log.d("Tester", "分享名片")
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
            // 长按社交平台时，底部显示悬浮操作栏
            val currentPlatform = selectedPlatform
            if (currentPlatform != null) {
            AnimatedVisibility(
                visible = showPlatformContextMenu,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                val (fieldKey, pEntry) = currentPlatform
                val pDisplayName = FIELD_DEF_MAP[fieldKey]?.displayName ?: fieldKey
                Box(modifier = Modifier.padding(bottom = LocalFloatingBarBottomPadding.current)) {
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
                            }
                        )
                        ToolbarAction(
                            icon = Icons.Default.Edit,
                            label = "编辑",
                            onClick = {
                                editingPlatform = currentPlatform
                                showEditPlatformDialog = true
                                showPlatformContextMenu = false
                            }
                        )
                        // 有跳转链接且适配器可同步时才显示同步按钮
                        val syncContactType = FIELD_DEF_MAP[fieldKey]?.contactType
                        val syncAdapter = syncContactType?.let { PlatformAdapterRegistry.getAdapter(it) }
                        if (pEntry.jumpLink.isNotBlank() && syncAdapter?.canSync == true) {
                            ToolbarAction(
                                icon = Icons.Default.Person,
                                label = "同步信息",
                                onClick = {
                                    syncPlatformInfo = currentPlatform
                                    showPlatformContextMenu = false
                                    showSyncOptionsSheet = true
                                }
                            )
                        }
                        ToolbarAction(
                            icon = Icons.Default.Delete,
                            label = "删除",
                            tint = Color.Red,
                            onClick = {
                                showPlatformContextMenu = false
                                showDeleteConfirmDialog = true
                            }
                        )
                    }
                }
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
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding() + 32.dp + LocalFloatingBarBottomPadding.current
                )
            ) {
                // 上方：头像 + 昵称区域
                item(key = "header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 头像：显示本地 avatarPath 或首字母占位（含相机图标提示）
                        var avatarBitmap by remember(profile?.avatarPath, avatarVersion) {
                            mutableStateOf<Bitmap?>(null)
                        }
                        val localAvatarPath = profile?.avatarPath
                        LaunchedEffect(localAvatarPath, avatarVersion) {
                            avatarBitmap = Methods.loadAvatarBitmap(localAvatarPath)
                        }

                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clickable {
                                    pickAvatarLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                val avBmp = avatarBitmap
                                if (avBmp != null) {
                                    Image(
                                        bitmap = avBmp.asImageBitmap(),
                                        contentDescription = "头像",
                                        modifier = Modifier.size(80.dp)
                                    )
                                } else {
                                    val name = profile?.name ?: ""
                                    Text(
                                        text = name.take(1).ifBlank { "?" },
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

                        Text(
                            text = profile?.name ?: "未设置",
                            style = MiuixTheme.textStyles.title1
                        )

                        val currentProfile = profile
                        if (currentProfile != null && !currentProfile.bio.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentProfile.bio!!,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onBackgroundVariant
                            )
                        }
                    }
                }

                // 社交平台
                item(key = "platform_long_press_hint") {
                    FirstTimeHint(
                        text = "长按社交平台可复制/编辑/同步",
                        hintKey = "long_press_platform",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
                item(key = "platforms") {
                    SmallTitle(text = "社交平台")
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                    ) {
                        // 已有平台条目
                        platformFields.forEach { (fieldKey, entry) ->
                            val displayName = FIELD_DEF_MAP[fieldKey]?.displayName ?: fieldKey
                            val summary = buildString {
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
                            LongPressArrowPreference(
                                title = displayName,
                                summary = summary,
                                onClick = {
                                    selectedPlatformDetail = fieldKey to entry
                                    showPlatformDetailDialog = true
                                },
                                onLongClick = {
                                    selectedPlatform = fieldKey to entry
                                    showPlatformContextMenu = true
                                }
                            )
                        }
                        // 添加社交平台（始终显示）
                        ArrowPreference(
                            title = "添加社交平台",
                            summary = "添加你的社交账号",
                            onClick = {
                                Log.d("Tester", "添加社交平台")
                                showAddPlatformDialog = true
                            }
                        )
                    }
                }
            }
        }
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
                        val current = repository.getUserProfileOnce() ?: UserProfile(name = "用户")
                        val updated = current.copy(
                            name = editName.ifBlank { "用户" },
                            bio = editBio.ifBlank { null },
                            updateTime = System.currentTimeMillis()
                        )
                        repository.saveUserProfile(updated)
                        withContext(Dispatchers.Main) {
                            profile = repository.getUserProfileOnce() ?: updated
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
            scope.launch(Dispatchers.IO) {
                repository.updatePlatformField(fieldKey, entry.jumpLink, entry.value, entry.displayName, entry.avatarUrl, entry.originalLink)
                withContext(Dispatchers.Main) {
                    val updated = repository.getUserProfileOnce() ?: profile ?: UserProfile(name = "用户")
                    profile = updated
                }
            }
            showAddPlatformDialog = false
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
                    repository.updatePlatformField(fieldKey, newEntry.jumpLink, newEntry.value, newEntry.displayName, newEntry.avatarUrl, newEntry.originalLink)
                    withContext(Dispatchers.Main) {
                        val updated = repository.getUserProfileOnce() ?: profile ?: UserProfile(name = "用户")
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

                        // 先走网络解析获取最新信息
                        val resolveResult = withContext(Dispatchers.IO) {
                            try {
                                val content = pEntry.jumpLink.ifBlank { pEntry.value ?: "" }
                                val contactType = FIELD_DEF_MAP[pName]?.contactType
                                ContactNetworkResolver.getResultInfo(content, mutableMapOf(), type = contactType)
                            } catch (_: Exception) { null }
                        }
                        val resolvedName = resolveResult?.nickname?.takeIf { it.isNotBlank() && it != "未知" }
                        val resolvedAvatar = resolveResult?.avatarUrl?.takeIf { it.isNotBlank() }

                        // 用解析结果更新平台 entry
                        if (resolvedName != null || resolvedAvatar != null) {
                            withContext(Dispatchers.IO) {
                                repository.updatePlatformField(pName, pEntry.jumpLink, pEntry.value, resolvedName, resolvedAvatar, pEntry.originalLink)
                            }
                        }

                        // updatePlatformField 已修改 DB 中的 platforms，重新读取以包含该更新
                        val current = withContext(Dispatchers.IO) { repository.getUserProfileOnce() } ?: UserProfile(name = "用户")

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
                            repository.saveUserProfile(updated)
                        }
                        profile = withContext(Dispatchers.IO) { repository.getUserProfileOnce() } ?: updated
                        avatarVersion++

                        Toast.makeText(context, "同步成功", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        e.printStackTrace()
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
                val (pName, _) = selectedPlatform ?: return@DialogButtonRow
                val currentAvatarPath = profile?.avatarPath
                scope.launch(Dispatchers.IO) {
                    repository.removePlatform(pName)
                    // 头像回退：如果当前有头像，检查剩余平台是否有可用头像
                    val updatedProfile = repository.getUserProfileOnce() ?: profile
                    if (currentAvatarPath != null) {
                        val remainingPlatforms = updatedProfile?.platforms ?: emptyMap()
                        val fallbackEntry = remainingPlatforms.entries.firstOrNull {
                            !it.value.avatarUrl.isNullOrBlank()
                        }
                        if (fallbackEntry != null) {
                            val fallbackUrl = fallbackEntry.value.avatarUrl
                            val bitmap = if (!fallbackUrl.isNullOrBlank()) HttpUtil.downloadBitmap(fallbackUrl) else null
                            if (bitmap != null) {
                                val avatarFile = Methods.saveBitmapAsAvatar(context, bitmap, "user_avatar.webp")
                                val newProfile = updatedProfile?.copy(
                                    avatarPath = avatarFile.absolutePath,
                                    updateTime = System.currentTimeMillis()
                                )
                                if (newProfile != null) {
                                    repository.saveUserProfile(newProfile)
                                }
                            } else {
                                Methods.deleteAvatarFile(currentAvatarPath)
                                HttpUtil.clearBitmapCache()
                                val newProfile = updatedProfile?.copy(
                                    avatarPath = null,
                                    updateTime = System.currentTimeMillis()
                                )
                                if (newProfile != null) {
                                    repository.saveUserProfile(newProfile)
                                }
                            }
                        } else {
                            Methods.deleteAvatarFile(currentAvatarPath)
                            HttpUtil.clearBitmapCache()
                            val newProfile = updatedProfile?.copy(
                                avatarPath = null,
                                updateTime = System.currentTimeMillis()
                            )
                            if (newProfile != null) {
                                repository.saveUserProfile(newProfile)
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        profile = repository.getUserProfileOnce() ?: profile
                        avatarVersion++
                    }
                }
                selectedPlatform = null
                Toast.makeText(context, "已删除 $pName", Toast.LENGTH_SHORT).show()
            },
            isDestructive = true
        )
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
}