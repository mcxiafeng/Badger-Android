package top.mcxiafeng.badger.pages.social

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import top.mcxiafeng.badger.data.prefs.isOnboardingCompleted
import top.mcxiafeng.badger.data.model.PlatformEntry
import top.mcxiafeng.badger.di.KoinComponentBy
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.platform.NfcWriter
import top.mcxiafeng.badger.platform.PlatformImage
import top.mcxiafeng.badger.platform.loadOrientedImage
import top.mcxiafeng.badger.platform.rememberImagePickerLauncher
import top.mcxiafeng.badger.ui.components.BadgerFloatingBarList
import top.mcxiafeng.badger.ui.components.badgerListContentPadding
import top.mcxiafeng.badger.ui.components.BadgerInputDialog
import top.mcxiafeng.badger.ui.components.FirstTimeHint
import top.mcxiafeng.badger.ui.components.ImageCropDialog
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Nfc
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.platform.showToast
import top.mcxiafeng.badger.platform.BackHandler
import top.mcxiafeng.badger.shared.util.BadgerDispatchers

private const val TAG = "SocialPage"

private enum class EditTarget { NAME, VALUE }

/** 编辑对话框上下文：显式携带发起编辑时的平台，避免依赖"当前选中"的间接状态 */
private data class PlatformEditContext(
    val fieldKey: String,
    val entry: PlatformEntry,
    val target: EditTarget,
)

/**
 * 「我的名片」路由入口
 *
 * 设计要点（2026-08-31 重构）：
 * - 顶部 TopAppBar：标题 + NFC 直达按钮 + 更多菜单（更换背景图 / 编辑名片）
 * - 个人信息卡：左头像 + 中姓名/签名 + 右编辑入口；右上短链同步文字态
 * - 平台切换：横滑 chips（描边 + indicator），选中态三层视觉
 * - 平台信息卡：两行列表项（显示名 + ID），MIUI 列表语义
 * - 二维码卡片：占满宽度，依赖 [QrCodeCard] 自身放大弹窗
 *
 * @param navigateToContacts 跳转联系人页（保留 API 兼容；当前未在 UI 中直接调用）
 * @param onNavigateToProfile 跳转「我的名片」编辑页（头像/姓名/签名）
 * @param onNavigateToSettings 跳转设置页（短链配置）
 */
@Composable
fun SocialRoute(
    @Suppress("UNUSED_PARAMETER") navigateToContacts: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
) {
    val viewModel: SocialViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SocialScreen(
        uiState = uiState,
        onSelectPlatform = viewModel::selectPlatform,
        onSetNfcSupported = viewModel::setNfcSupported,
        onShowNfcWriteDialog = viewModel::showNfcWriteDialog,
        onDismissNfcWriteDialog = viewModel::dismissNfcWriteDialog,
        onStartNfcWrite = viewModel::startNfcWrite,
        onNfcWriteSuccess = viewModel::onNfcWriteSuccess,
        onNavigateToProfile = onNavigateToProfile,
        onNavigateToSettings = onNavigateToSettings,
        onUpdatePlatform = viewModel::addOrUpdatePlatform,
    )
}

/**
 * 「我的名片」屏主体
 *
 * 与路由解耦，传入 [SocialUiState] 和回调以保持可测试性。
 */
@Composable
fun SocialScreen(
    uiState: SocialUiState,
    onSelectPlatform: (Int) -> Unit = {},
    onSetNfcSupported: (Boolean) -> Unit = {},
    onShowNfcWriteDialog: () -> Unit = {},
    onDismissNfcWriteDialog: (NfcActivityHandler) -> Unit = {},
    onStartNfcWrite: (NfcActivityHandler) -> Unit = {},
    onNfcWriteSuccess: (NfcActivityHandler) -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onUpdatePlatform: (String, String, String?, String?, String?, String?) -> Unit = { _, _, _, _, _, _ -> },
) {
    // [KMP K13c] Activity 依赖走 ActivityHost 注册表（androidMain，MainActivity 挂钩），
    // UI 层不再下探 Context as? Activity
    // [修复防御]: handler 生命周期语义保留；remember { } 避免对话框重开时重建，
    // 但不能放进全局 ViewModel，否则 Activity 泄漏。
    // [KMP K11] NFC 平台边界：写入状态收口到 shared 的 NfcWriter 单例
    val nfcWriter = remember { KoinComponentBy.get<NfcWriter>() }
    DisposableEffect(Unit) {
        onDispose { /* NFC 写入状态由 NfcWriter 单例托管 */ }
    }
    val nfcHandler = remember {
        object : NfcActivityHandler {
            override fun startWriting(uri: String) {
                nfcWriter.startWriting(uri)
            }
            override fun stopWriting() {
                nfcWriter.stopWriting()
            }
        }
    }
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val snackbarHostState = remember { SnackbarHostState() }

    // 头像（ContactAvatar 内部自行加载）
    val avatarPath = uiState.profile?.avatarPath

    // 名片展示文案
    val profileName = remember(uiState.profile) {
        uiState.profile?.name?.takeIf { it.isNotBlank() }
    }
    val profileBio = remember(uiState.profile) {
        uiState.profile?.bio?.ifBlank { null }
    }

    // 平台列表
    val platforms = uiState.platforms
    val selectedPlatform = platforms.getOrNull(uiState.selectedPlatformIndex)

    // TopAppBar 菜单
    var showOverflowMenu by remember { mutableStateOf(false) }
    BackHandler(enabled = showOverflowMenu) { showOverflowMenu = false }

    // 编辑对话框（[修复防御]: 上下文显式携带 fieldKey+entry——滑动切换后仍编辑发起时的平台，
    // 不依赖"当前选中"的间接状态）
    var editContext by remember { mutableStateOf<PlatformEditContext?>(null) }
    var editText by remember { mutableStateOf("") }

    // 图片裁剪
    var showCropDialog by remember { mutableStateOf(false) }
    var cropSourceImage by remember { mutableStateOf<PlatformImage?>(null) }
    val scope = rememberCoroutineScope()

    val photoPickerLauncher = rememberImagePickerLauncher { bytes ->
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

    val onPickCardImage: () -> Unit = {
        photoPickerLauncher.launch()
    }

    val onCropConfirm: (ByteArray) -> Unit = { _ ->
        showCropDialog = false
        cropSourceImage = null
        // [修复防御]: V2 cache 已不再支持 cardImagePath(V2 改用服务端 coverAvatarUrl)。
        // 此处只做用户反馈，避免误以为已生效。
        showToast("暂未支持自定义背景图")
    }

    // 初始化 NFC 硬件检测
    LaunchedEffect(Unit) {
        onSetNfcSupported(nfcWriter.isSupported())
    }

    // NFC 写入对话框打开时自动开始写入流程
    LaunchedEffect(uiState.showNfcWriteDialog) {
        if (uiState.showNfcWriteDialog) {
            onStartNfcWrite(nfcHandler)
        }
    }

    // NFC 写入成功后自动关闭
    LaunchedEffect(uiState.nfcWriteState) {
        if (uiState.nfcWriteState == NfcWriteState.SUCCESS) {
            onNfcWriteSuccess(nfcHandler)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = "我的名片",
                scrollBehavior = topAppBarScrollBehavior,
                actions = {
                    // NFC 直达：仅在 NFC 可用 + 已选平台时亮起
                    IconButton(
                        onClick = onShowNfcWriteDialog,
                        enabled = uiState.nfcSupported && selectedPlatform != null,
                    ) {
                        Icon(
                            imageVector = Lucide.Nfc,
                            contentDescription = "写入 NFC 标签",
                            tint = if (uiState.nfcSupported && selectedPlatform != null) {
                                MiuixTheme.colorScheme.onSurface
                            } else {
                                MiuixTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            },
                        )
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                imageVector = Lucide.EllipsisVertical,
                                contentDescription = "更多",
                            )
                        }
                        OverlayListPopup(
                            show = showOverflowMenu,
                            alignment = PopupPositionProvider.Align.TopEnd,
                            popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                            onDismissRequest = { showOverflowMenu = false },
                        ) {
                            ListPopupColumn {
                                DropdownImpl(
                                    text = "更换背景图",
                                    optionSize = 2,
                                    isSelected = false,
                                    index = 0,
                                    onSelectedIndexChange = {
                                        showOverflowMenu = false
                                        onPickCardImage()
                                    },
                                )
                                DropdownImpl(
                                    text = "编辑名片信息",
                                    optionSize = 2,
                                    isSelected = false,
                                    index = 1,
                                    onSelectedIndexChange = {
                                        showOverflowMenu = false
                                        onNavigateToProfile()
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        val contentPadding = badgerListContentPadding(
            scaffoldTop = paddingValues.calculateTopPadding(),
            scaffoldBottom = paddingValues.calculateBottomPadding(),
            topExtra = BadgerSpacing.sm,
        )

        if (platforms.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
            ) {
                SocialProfileHeader(
                    profileName = profileName,
                    profileBio = profileBio,
                    avatarPath = avatarPath,
                    linkUpdateState = uiState.linkUpdateState,
                    onEditProfile = onNavigateToProfile,
                )
                if (isOnboardingCompleted()) {
                    FirstTimeHint(
                        text = "点击右上角「更多」可编辑名片或更换背景图",
                        hintKey = "social_empty_platforms",
                        modifier = Modifier.padding(horizontal = BadgerSpacing.lg, vertical = BadgerSpacing.xs),
                    )
                }
                PlatformEmptyCard(onNavigateToProfile = onNavigateToProfile)
            }
        } else {
            BadgerFloatingBarList(
                contentPadding = contentPadding,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
            ) {
                item(key = "profile_header") {
                    SocialProfileHeader(
                        profileName = profileName,
                        profileBio = profileBio,
                        avatarPath = avatarPath,
                        linkUpdateState = uiState.linkUpdateState,
                        onEditProfile = onNavigateToProfile,
                    )
                }

                item(key = "platform_chips") {
                    PlatformChipsRow(
                        platforms = platforms,
                        selectedPlatformIndex = uiState.selectedPlatformIndex,
                        onSelectPlatform = onSelectPlatform,
                    )
                }

                // [滑动切换] 平台信息卡 + 二维码卡随 Pager 左右滑动，与 chips 双向同步
                item(key = "platform_content") {
                    PlatformContentPager(
                        platforms = platforms,
                        selectedPlatformIndex = uiState.selectedPlatformIndex,
                        avatarPath = avatarPath,
                        onSelectPlatform = onSelectPlatform,
                        onEditDisplayName = { fieldKey, entry ->
                            editText = entry.displayName ?: ""
                            editContext = PlatformEditContext(fieldKey, entry, EditTarget.NAME)
                        },
                        onEditValue = { fieldKey, entry ->
                            editText = entry.value ?: ""
                            editContext = PlatformEditContext(fieldKey, entry, EditTarget.VALUE)
                        },
                    )
                }
            }
        }
    }

    // 图片裁剪对话框
    if (showCropDialog && cropSourceImage != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showCropDialog = false; cropSourceImage = null },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
            ),
        ) {
            ImageCropDialog(
                image = cropSourceImage!!,
                onConfirm = onCropConfirm,
                onDismiss = { showCropDialog = false; cropSourceImage = null },
            )
        }
    }

    // 编辑名字 / ID 对话框
    val ctx = editContext
    if (ctx != null) {
        val idLabel = idLabelFor(ctx.fieldKey)
        val dialogTitle = when (ctx.target) {
            EditTarget.NAME -> "编辑平台昵称"
            EditTarget.VALUE -> "编辑$idLabel"
        }
        val fieldLabel = when (ctx.target) {
            EditTarget.NAME -> "平台昵称"
            EditTarget.VALUE -> idLabel
        }
        BadgerInputDialog(
            show = true,
            title = dialogTitle,
            value = editText,
            onValueChange = { editText = it },
            label = fieldLabel,
            onConfirm = {
                val newDisplayName = if (ctx.target == EditTarget.NAME) {
                    editText.trim().ifBlank { null }
                } else ctx.entry.displayName
                val newValue = if (ctx.target == EditTarget.VALUE) {
                    editText.trim().ifBlank { null }
                } else ctx.entry.value
                onUpdatePlatform(
                    ctx.fieldKey,
                    ctx.entry.jumpLink,
                    newValue,
                    newDisplayName,
                    ctx.entry.avatarUrl,
                    ctx.entry.originalLink,
                )
                BadgerLog.d(TAG, "更新: key=${ctx.fieldKey}, target=${ctx.target}, value=$editText")
                editContext = null
            },
            onDismiss = { editContext = null },
        )
    }

    // NFC 写入对话框
    if (uiState.showNfcWriteDialog) {
        NfcWriteDialog(
            state = uiState.nfcWriteState,
            message = uiState.nfcWriteMessage,
            shortUrl = uiState.shortUrl,
            nfcSupported = uiState.nfcSupported,
            isShortLinkConfigured = uiState.shortLinkConfigured,
            onDismiss = { onDismissNfcWriteDialog(nfcHandler) },
            onRetry = {
                if (nfcWriter.isWriting) nfcHandler.stopWriting()
                nfcWriter.writeResult.value // reset
                onStartNfcWrite(nfcHandler)
            },
            onOpenNfcSettings = { nfcWriter.openNfcSettings() },
            onOpenShortLinkSettings = {
                onDismissNfcWriteDialog(nfcHandler)
                onNavigateToSettings()
            },
        )
    }
}
