package top.mcxiafeng.badger.pages.social

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.hilt.navigation.compose.hiltViewModel
import top.mcxiafeng.badger.network.ShortLinkService
import top.mcxiafeng.badger.data.isDeveloperMode
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ui.glass.FloatingActionButton
import top.mcxiafeng.badger.ui.glass.scrollTopFade
import top.mcxiafeng.badger.ui.glass.springOverscroll
import top.mcxiafeng.badger.ui.glass.Scaffold
import top.mcxiafeng.badger.ui.glass.PageActionBar
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Icon
import top.mcxiafeng.badger.ui.components.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.mcxiafeng.badger.utils.Methods
import top.mcxiafeng.badger.ui.components.ImageCropDialog
import top.mcxiafeng.badger.ui.glass.glassButtonForeground

/** 手机号格式（11位数字），用于区分二维码内容类型 */
private val PHONE_NUMBER_REGEX = Regex("\\d{11}")
private const val CARD_IMAGE_FILE_NAME = "card_image.webp"


/**
 * 我的名片页面
 *
 * 页面结构：名片卡片 + 平台横滑卡片；右下角二维码按钮直接打开放大二维码弹层。
 * 数据从数据库 UserProfile 读取。
 *
 * @param navigateToContacts 导航到联系人页（已废弃）
 * @param onNavigateToProfile 导航到"我的名片"编辑页
 */
@Composable
fun SocialRoute(
    navigateToContacts: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToAddPlatform: () -> Unit = {}
) {
    val viewModel: SocialViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SocialScreen(
        uiState = uiState,
        onSelectPlatform = viewModel::selectPlatform,
        onUpdateCardImage = viewModel::updateCardImage,
        onSetNfcSupported = viewModel::setNfcSupported,
        onShowNfcWriteDialog = viewModel::showNfcWriteDialog,
        onDismissNfcWriteDialog = viewModel::dismissNfcWriteDialog,
        onStartNfcWrite = viewModel::startNfcWrite,
        onNfcWriteSuccess = viewModel::onNfcWriteSuccess,
        onNavigateToProfile = onNavigateToProfile,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToAddPlatform = onNavigateToAddPlatform,
        onLoadPlatformProfile = viewModel::loadPlatformProfile
    )
}

@Composable
fun SocialScreen(
    uiState: SocialUiState,
    onSelectPlatform: (Int) -> Unit = {},
    onUpdateCardImage: (String) -> Unit = {},
    onSetNfcSupported: (Boolean) -> Unit = {},
    onShowNfcWriteDialog: () -> Unit = {},
    onDismissNfcWriteDialog: (NfcActivityHandler) -> Unit = {},
    onStartNfcWrite: (NfcActivityHandler) -> Unit = {},
    onNfcWriteSuccess: (NfcActivityHandler) -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToAddPlatform: () -> Unit = {},
    onLoadPlatformProfile: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val nfcHandler = remember(activity) {
        object : NfcActivityHandler {
            override fun startWriting(uri: String) {
                Log.d("Tester", "NfcActivityHandler.startWriting: uri=$uri")
                val act = activity ?: run {
                    Log.w("Tester", "NfcActivityHandler.startWriting: activity is null")
                    return
                }
                NfcHelper.startWriting(act, uri)
            }
            override fun stopWriting() {
                Log.d("Tester", "NfcActivityHandler.stopWriting")
                val act = activity ?: return
                NfcHelper.stopWriting(act)
            }
        }
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { Log.d("SocialPageTester", "Full viewport; title in scroll content; progressive top blur") }
    val snackbarHostState = remember { SnackbarHostState() }

    // 长按菜单状态
    var showNfcMenu by remember { mutableStateOf(false) }

    // 二维码展开弹层
    var showQrExpanded by remember { mutableStateOf(false) }

    // 系统返回键关闭长按菜单
    BackHandler(enabled = showNfcMenu) {
        showNfcMenu = false
    }

    // 名片背景图片
    var cardBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cardImageVersion by remember { mutableIntStateOf(0) }
    val cardImagePath = uiState.profile?.cardImagePath
    LaunchedEffect(cardImagePath, cardImageVersion) {
        val newBitmap = Methods.loadBackgroundBitmap(cardImagePath, context)
        cardBitmap = newBitmap
        Log.d("SocialPageTester", "名片背景按需读取完成")
    }

    // 用户头像路径（ContactAvatar 组件内部自行加载）
    val avatarPath = uiState.profile?.avatarPath

    // 图片裁剪状态
    var showCropDialog by remember { mutableStateOf(false) }
    var cropSourceUri by remember { mutableStateOf<Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            cropSourceUri = selectedUri
            showCropDialog = true
        }
    }

    val onCropConfirm: (Bitmap) -> Unit = { croppedBitmap ->
        showCropDialog = false
        cropSourceUri = null
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val outputFile = Methods.saveBitmapAsCardImage(context, croppedBitmap, CARD_IMAGE_FILE_NAME)
                    withContext(Dispatchers.Main) {
                        onUpdateCardImage(outputFile.absolutePath)
                        cardImageVersion++
                        Toast.makeText(context, "图片已设置", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.w("SocialPage", "保存图片失败", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "保存图片失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // 上方文字与下方高亮读取同一份翻页状态，包括末尾的“添加”页。
    val platformPagerState = rememberPagerState(
        initialPage = uiState.selectedPlatformIndex.coerceIn(0, uiState.platforms.size),
        pageCount = { uiState.platforms.size + 1 }
    )
    val centeredPlatform = uiState.platforms.getOrNull(platformPagerState.currentPage)
    val isAddPage = centeredPlatform == null
    val selectedPlatform = uiState.platforms.getOrNull(uiState.selectedPlatformIndex)

    // 二维码内容：jumpLink优先，value文本兜底（微信场景）
    val qrContent = remember(selectedPlatform) {
        val entry = selectedPlatform?.second
        if (entry != null) {
            if (entry.jumpLink.isNotBlank()) {
                entry.jumpLink
            } else if (!entry.value.isNullOrBlank()) {
                // 微信号/手机号文本
                val isPhone = entry.value.matches(PHONE_NUMBER_REGEX)
                if (isPhone) "手机号：${entry.value}" else "微信号：${entry.value}"
            } else ""
        } else ""
    }

    // 昵称独立于平台标签与账号，抓取资料更新时同步刷新。
    val qrUserName = remember(selectedPlatform, uiState.platformProfiles, uiState.profile?.name) {
        uiState.qrUserNameFor(selectedPlatform?.first)
    }
    val qrPlatformValue = remember(selectedPlatform) {
        selectedPlatform?.second?.value?.trim()?.takeIf { it.isNotEmpty() }
    }

    // 初始化 NFC 硬件检测
    LaunchedEffect(Unit) {
        onSetNfcSupported(NfcHelper.isNfcSupported(context))
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

    val cardContent = uiState.cardContentFor(centeredPlatform?.first)
    LaunchedEffect(centeredPlatform) {
        Log.d("SocialPageTester", "Business card page changed: platform=${centeredPlatform?.first ?: "add"}")
        centeredPlatform?.first?.let(onLoadPlatformProfile)
    }

    Scaffold(
        progressiveTopBlur = true,
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        topBar = { PageActionBar(title = "首页") },
        floatingActionButton = {
            val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current
            // 右下角二维码按钮：直接打开放大二维码弹层
            FloatingActionButton(
                onClick = {
                    if (qrContent.isBlank()) {
                        Log.d("SocialPage", "QR button clicked but no platform content")
                        Toast.makeText(context, "请先添加平台", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.d("SocialPage", "QR button clicked, open expanded QR")
                        showQrExpanded = true
                    }
                },
                modifier = Modifier.padding(bottom = floatingBarBottomPadding),
                icon = Icons.Filled.QrCode2,
                contentDescription = "出示二维码",
            )
        }
    ) { paddingValues ->
        val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current
        LazyColumn(
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + BadgerSpacing.md + BadgerSpacing.scrollContentTopGap,
                bottom = paddingValues.calculateBottomPadding() + floatingBarBottomPadding
            ),
            modifier = Modifier.fillMaxSize().scrollTopFade().springOverscroll()
        ) {
            item {
                BlueBusinessCard(
                    cardBitmap = cardBitmap,
                    profileName = cardContent.name,
                    profileBio = cardContent.signature,
                    showNfcMenu = showNfcMenu,
                    linkUpdateState = uiState.linkUpdateState,
                    nfcSupported = uiState.nfcSupported,
                    onCardClick = if (isAddPage) {
                        {
                            Log.d("SocialPageTester", "打开添加平台页面")
                            onNavigateToAddPlatform()
                        }
                    } else onShowNfcWriteDialog,
                    onShowNfcMenu = { showNfcMenu = true },
                    onDismissNfcMenu = { showNfcMenu = false },
                    onPickImage = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )

                // 平台选择卡片：profile 加载完成后才渲染，
                // 避免 pager pageCount 首帧 1→N 跳变导致 currentPage 落错页
                if (uiState.profile != null) {
                    PlatformIconsCard(
                        platforms = uiState.platforms,
                        selectedPlatformIndex = uiState.selectedPlatformIndex,
                        pagerState = platformPagerState,
                        onSelectPlatform = onSelectPlatform,
                        onAddPlatform = {
                            Log.d("SocialPageTester", "从平台列表打开添加页面")
                            onNavigateToAddPlatform()
                        }
                    )
                }
            }
        }
    }

    // 二维码展开弹层
    if (qrContent.isNotBlank()) {
        QrCodeExpandedView(
            content = qrContent,
            visible = showQrExpanded,
            onDismiss = { showQrExpanded = false },
            userName = qrUserName,
            platformName = FIELD_DEF_MAP[selectedPlatform?.first]?.displayName ?: selectedPlatform?.first,
            platformValue = qrPlatformValue,
            avatarPath = avatarPath
        )
    }

    // 图片裁剪对话框
    if (showCropDialog && cropSourceUri != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showCropDialog = false; cropSourceUri = null },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false
            )
        ) {
            ImageCropDialog(
                imageUri = cropSourceUri!!,
                onConfirm = onCropConfirm,
                onDismiss = { showCropDialog = false; cropSourceUri = null }
            )
        }
    }

    // NFC 写入对话框
    if (uiState.showNfcWriteDialog) {
        NfcWriteDialog(
            state = uiState.nfcWriteState,
            message = uiState.nfcWriteMessage,
            shortUrl = uiState.shortUrl,
            nfcSupported = uiState.nfcSupported,
            isShortLinkConfigured = ShortLinkService.isConfigured(context) || !isDeveloperMode(context),
            onDismiss = {
                onDismissNfcWriteDialog(nfcHandler)
            },
            onRetry = {
                if (NfcHelper.isWriting) nfcHandler.stopWriting()
                NfcHelper.writeResult.value // reset
                onStartNfcWrite(nfcHandler)
            },
            onOpenNfcSettings = { NfcHelper.openNfcSettings(context) },
            onOpenShortLinkSettings = {
                onDismissNfcWriteDialog(nfcHandler)
                onNavigateToSettings()
            }
        )
    }
}
