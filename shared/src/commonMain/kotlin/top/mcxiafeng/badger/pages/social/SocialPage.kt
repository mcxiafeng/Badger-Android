package top.mcxiafeng.badger.pages.social

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import top.mcxiafeng.badger.data.prefs.isDeveloperMode
import top.mcxiafeng.badger.data.prefs.isOnboardingCompleted
import top.mcxiafeng.badger.network.ShortLinkService
import top.mcxiafeng.badger.di.KoinComponentBy
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.platform.NfcWriter
import top.mcxiafeng.badger.platform.PlatformImage
import top.mcxiafeng.badger.platform.loadOrientedImage
import top.mcxiafeng.badger.platform.rememberImagePickerLauncher
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
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

/** 手机号格式（11位数字），用于区分二维码内容类型 */
private val PHONE_NUMBER_REGEX = Regex("\\d{11}")

/**
 * 「我的名片」路由入口
 *
 * 设计要点（2026-08-31 重构）：
 * - 顶部 TopAppBar：标题 + NFC 直达按钮 + 更多菜单（更换背景图 / 编辑名片 / 短链设置）
 * - 个人信息卡：左头像 + 中姓名/签名 + 右编辑入口；右上短链同步文字态
 * - 平台切换：横滑 chips（描边 + indicator），选中态三层视觉
 * - 平台信息卡：两行列表项（显示名 + ID），MIUI 列表语义
 * - 二维码卡片：占满宽度，依赖 [QrCodeCard] 自身放大弹窗
 *
 * @param navigateToContacts 跳转联系人页（保留 API 兼容；当前未在 UI 中直接调用）
 * @param onNavigateToProfile 跳转「我的名片」编辑页（头像/姓名/签名）
 * @param onNavigateToSettings 跳转设置页（短链配置）
 */
@OptIn(ExperimentalFoundationApi::class)
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
@OptIn(ExperimentalFoundationApi::class)
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
    val selectedPlatformDef = selectedPlatform?.first?.let { FIELD_DEF_MAP[it] }
    val idLabel: String = selectedPlatformDef?.inputHint?.let { hint ->
        if (hint.contains("或")) hint.substringBefore("或").trim() else hint.ifBlank { selectedPlatformDef.displayName + "号" }
    } ?: (selectedPlatformDef?.displayName?.plus("号") ?: "ID")

    // 二维码内容：jumpLink 优先，value 文本兜底（微信/手机号场景）
    val qrContent = remember(selectedPlatform) {
        val entry = selectedPlatform?.second
        if (entry != null) {
            when {
                entry.jumpLink.isNotBlank() -> entry.jumpLink
                !entry.value.isNullOrBlank() -> {
                    val entryValue = entry.value ?: ""
                    val isPhone = entryValue.matches(PHONE_NUMBER_REGEX)
                    if (isPhone) "手机号：$entryValue" else "微信号：$entryValue"
                }
                else -> ""
            }
        } else ""
    }

    // TopAppBar 菜单
    var showOverflowMenu by remember { mutableStateOf(false) }
    BackHandler(enabled = showOverflowMenu) { showOverflowMenu = false }

    // 编辑对话框
    var editTarget by remember { mutableStateOf<EditTarget?>(null) }
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
                                    optionSize = 3,
                                    isSelected = false,
                                    index = 0,
                                    onSelectedIndexChange = {
                                        showOverflowMenu = false
                                        onPickCardImage()
                                    },
                                )
                                DropdownImpl(
                                    text = "编辑名片信息",
                                    optionSize = 3,
                                    isSelected = false,
                                    index = 1,
                                    onSelectedIndexChange = {
                                        showOverflowMenu = false
                                        onNavigateToProfile()
                                    },
                                )
                                DropdownImpl(
                                    text = "短链服务设置",
                                    optionSize = 3,
                                    isSelected = false,
                                    index = 2,
                                    onSelectedIndexChange = {
                                        showOverflowMenu = false
                                        onNavigateToSettings()
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current
        val contentPadding = PaddingValues(
            top = paddingValues.calculateTopPadding() + BadgerSpacing.sm,
            bottom = paddingValues.calculateBottomPadding() + floatingBarBottomPadding,
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
            LazyColumn(
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

                if (selectedPlatform != null) {
                    val entry = selectedPlatform.second
                    item(key = "platform_info_${selectedPlatform.first}") {
                        PlatformInfoCard(
                            displayName = entry.displayName,
                            value = entry.value,
                            idLabel = idLabel,
                            onEditDisplayName = {
                                editText = entry.displayName ?: ""
                                editTarget = EditTarget.NAME
                            },
                            onEditValue = {
                                editText = entry.value ?: ""
                                editTarget = EditTarget.VALUE
                            },
                        )
                    }
                }

                if (qrContent.isNotBlank() && selectedPlatform != null) {
                    item(key = "qr_code") {
                        val entry = selectedPlatform.second
                        val displayValue = buildString {
                            if (!entry.displayName.isNullOrBlank() && !entry.value.isNullOrBlank()) {
                                append(entry.displayName)
                                append("（")
                                append(entry.value)
                                append("）")
                            } else if (!entry.value.isNullOrBlank()) {
                                append(entry.value)
                            }
                        }
                        Box(
                            modifier = Modifier.padding(horizontal = BadgerSpacing.lg, vertical = BadgerSpacing.sm)
                        ) {
                            QrCodeCard(
                                content = qrContent,
                                userName = entry.displayName ?: selectedPlatformDef?.displayName ?: selectedPlatform.first,
                                platformName = selectedPlatformDef?.displayName ?: selectedPlatform.first,
                                platformValue = displayValue.ifBlank { null },
                                avatarPath = avatarPath,
                            )
                        }
                    }
                } else if (selectedPlatform != null) {
                    item(key = "qr_missing_value") {
                        Card(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = BadgerSpacing.lg, vertical = BadgerSpacing.sm),
                            insideMargin = PaddingValues(BadgerSpacing.lg),
                        ) {
                            Text(
                                text = "请先填写「$idLabel」后再生成二维码",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
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
    val currentTarget = editTarget
    if (currentTarget != null && selectedPlatform != null) {
        val entry = selectedPlatform.second
        val dialogTitle = when (currentTarget) {
            EditTarget.NAME -> "编辑平台昵称"
            EditTarget.VALUE -> "编辑$idLabel"
        }
        val fieldLabel = when (currentTarget) {
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
                val newDisplayName = if (currentTarget == EditTarget.NAME) {
                    editText.trim().ifBlank { null }
                } else entry.displayName
                val newValue = if (currentTarget == EditTarget.VALUE) {
                    editText.trim().ifBlank { null }
                } else entry.value
                onUpdatePlatform(
                    selectedPlatform.first,
                    entry.jumpLink,
                    newValue,
                    newDisplayName,
                    entry.avatarUrl,
                    entry.originalLink,
                )
                BadgerLog.d(TAG, "更新: target=$editTarget, value=$editText")
                editTarget = null
            },
            onDismiss = { editTarget = null },
        )
    }

    // NFC 写入对话框
    if (uiState.showNfcWriteDialog) {
        NfcWriteDialog(
            state = uiState.nfcWriteState,
            message = uiState.nfcWriteMessage,
            shortUrl = uiState.shortUrl,
            nfcSupported = uiState.nfcSupported,
            isShortLinkConfigured = ShortLinkService.isConfigured() || !isDeveloperMode(),
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
