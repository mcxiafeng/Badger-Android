package top.mcxiafeng.badger.pages.social

import top.mcxiafeng.badger.data.isOnboardingCompleted
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.hilt.navigation.compose.hiltViewModel
import top.mcxiafeng.badger.pages.social.NfcHelper
import top.mcxiafeng.badger.network.ShortLinkService
import top.mcxiafeng.badger.pages.setupguide.isDeveloperMode
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.utils.Methods
import top.mcxiafeng.badger.pages.social.NfcWriteState
import top.mcxiafeng.badger.pages.social.LinkUpdateState
import top.mcxiafeng.badger.pages.social.SocialViewModel
import top.mcxiafeng.badger.pages.social.SocialUiState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.miuixShape
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.window.WindowDialog
import java.io.File
import java.io.FileOutputStream
import androidx.core.net.toUri
import top.mcxiafeng.badger.ui.components.FirstTimeHint
import top.mcxiafeng.badger.ui.components.ImageCropDialog
import top.mcxiafeng.badger.ui.components.PlatformIcon

private enum class EditTarget { NAME, VALUE }


/**
 * 我的名片页面
 *
 * 展示个人社交信息（名片），支持二维码分享和 NFC 标签写入。
 * 数据从数据库 UserProfile 读取。
 *
 * @param navigateToContacts 导航到联系人页（已废弃）
 * @param onNavigateToProfile 导航到"我的名片"编辑页
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SocialRoute(
    navigateToContacts: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
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
        onUpdatePlatform = viewModel::addOrUpdatePlatform
    )
}

@Composable
fun SocialScreen(
    uiState: SocialUiState,
    onSelectPlatform: (Int) -> Unit = {},
    onUpdateCardImage: (String) -> Unit = {},
    onSetNfcSupported: (Boolean) -> Unit = {},
    onShowNfcWriteDialog: () -> Unit = {},
    onDismissNfcWriteDialog: (android.app.Activity) -> Unit = {},
    onStartNfcWrite: (android.app.Activity) -> Unit = {},
    onNfcWriteSuccess: (android.app.Activity) -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onUpdatePlatform: (String, String, String?, String?, String?, String?) -> Unit = { _, _, _, _, _, _ -> }
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val snackbarHostState = remember { SnackbarHostState() }

    // 长按菜单状态
    var showNfcMenu by remember { mutableStateOf(false) }

    // 系统返回键关闭长按菜单
    BackHandler(enabled = showNfcMenu) {
        showNfcMenu = false
    }

    // 名片背景图片
    var cardBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cardImageVersion by remember { mutableIntStateOf(0) }
    val cardImagePath = uiState.profile?.cardImagePath
    LaunchedEffect(cardImagePath, cardImageVersion) {
        cardBitmap = withContext(Dispatchers.IO) {
            if (!cardImagePath.isNullOrBlank()) {
                val file = File(cardImagePath)
                if (file.exists()) BitmapFactory.decodeFile(cardImagePath) else null
            } else null
        }
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
                    val outputFile = File(context.filesDir, "card_image.webp")
                    FileOutputStream(outputFile).use { out ->
                        croppedBitmap.compress(Bitmap.CompressFormat.WEBP, 75, out)
                    }
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

    // 平台列表
    val platformNames = uiState.platforms.map { it.first }
    val selectedPlatform = uiState.platforms.getOrNull(uiState.selectedPlatformIndex)

    // 二维码内容：jumpLink优先，value文本兜底（微信场景）
    val qrContent = remember(selectedPlatform) {
        val entry = selectedPlatform?.second
        if (entry != null) {
            if (entry.jumpLink.isNotBlank()) {
                entry.jumpLink
            } else if (!entry.value.isNullOrBlank()) {
                // 微信号/手机号文本
                val isPhone = entry.value.matches(Regex("\\d{11}"))
                if (isPhone) "手机号：${entry.value}" else "微信号：${entry.value}"
            } else ""
        } else ""
    }

    // 初始化 NFC 硬件检测
    LaunchedEffect(Unit) {
        onSetNfcSupported(NfcHelper.isNfcSupported(context))
    }

    // NFC 写入对话框打开时自动开始写入流程
    LaunchedEffect(uiState.showNfcWriteDialog) {
        if (uiState.showNfcWriteDialog && activity != null) {
            onStartNfcWrite(activity)
        }
    }

    // NFC 写入成功后自动关闭
    LaunchedEffect(uiState.nfcWriteState) {
        if (uiState.nfcWriteState == NfcWriteState.SUCCESS && activity != null) {
            onNfcWriteSuccess(activity)
        }
    }

    // 名片显示文字
    val profileName = remember(uiState.profile) {
        val profile = uiState.profile ?: return@remember null
        val name = profile.name
        if (name.isBlank()) null else name
    }
    val profileBio = remember(uiState.profile) {
        uiState.profile?.bio?.ifBlank { null }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        topBar = {
            TopAppBar(title = "我的名片", scrollBehavior = topAppBarScrollBehavior)
        }
    ) { paddingValues ->
        val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current
        LazyColumn(
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + 15.dp,
                bottom = paddingValues.calculateBottomPadding() + floatingBarBottomPadding
            ),
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
        ) {
            item {
                // 判断是否需要导航到设置页（无名字或名字是默认值"用户"）
                val needSetup = (profileName == null || profileName == "用户") && platformNames.isEmpty()

                if (needSetup) {
                    // 未设置信息 → 显示"创建你的名片"引导卡片（顶替蓝色名片卡片位置）
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        insideMargin = PaddingValues(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().clickable {
                                Log.d("SocialPage", "Create card clicked (needSetup), calling onNavigateToProfile()")
                                onNavigateToProfile()
                            },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "创建你的名片",
                                style = MiuixTheme.textStyles.title3,
                                color = MiuixTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "添加社交账号，生成二维码分享给朋友",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            top.yukonga.miuix.kmp.basic.TextButton(
                                text = "开始设置",
                                onClick = {
                                    Log.d("SocialPage", "Start setup clicked, calling onNavigateToProfile()")
                                    onNavigateToProfile()
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                // 蓝色名片卡片
                val cardShape = miuixShape(24.dp)
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .shadow(
                            elevation = 16.dp, shape = cardShape,
                            ambientColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.2f),
                            spotColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                        .clip(cardShape)
                        .background(MiuixTheme.colorScheme.primary, cardShape)
                        .combinedClickable(
                            onClick = {
                                Log.d("SocialPage", "Blue card clicked, calling onShowNfcWriteDialog()")
                                onShowNfcWriteDialog()
                            },
                            onLongClick = { showNfcMenu = true }
                        )
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (cardBitmap != null) {
                        Image(
                            bitmap = cardBitmap!!.asImageBitmap(),
                            contentDescription = "名片背景",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
                        if (profileName != null) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            ) {
                                Text(
                                    text = profileName, color = Color.White,
                                    style = MiuixTheme.textStyles.title2,
                                    modifier = Modifier.graphicsLayer { shadowElevation = 4.dp.toPx() }
                                )
                                if (profileBio != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = profileBio, color = Color.White.copy(alpha = 0.85f), style = MiuixTheme.textStyles.body2,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.graphicsLayer { shadowElevation = 2.dp.toPx() }
                                    )
                                }
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            if (profileName != null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = profileName, color = Color.White, style = MiuixTheme.textStyles.title2)
                                    if (profileBio != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = profileBio, color = Color.White.copy(alpha = 0.85f), style = MiuixTheme.textStyles.body2,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "创建名片",
                                    color = Color.White.copy(alpha = 0.9f),
                                    style = MiuixTheme.textStyles.title2,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // NFC 支持指示器（右上角小图标，提示可写入 NFC 标签）
                    // 右上角状态指示器
                    when (uiState.linkUpdateState) {
                        LinkUpdateState.UPDATING -> {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(24.dp)
                                    .background(MiuixTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    size = 14.dp,
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                        LinkUpdateState.SUCCESS -> {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(24.dp)
                                    .background(MiuixTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "更新成功",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        LinkUpdateState.ERROR -> {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(24.dp)
                                    .background(MiuixTheme.colorScheme.error, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "更新失败",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        LinkUpdateState.IDLE -> {
                            if (uiState.nfcSupported) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(24.dp)
                                        .background(MiuixTheme.colorScheme.primary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.material3.Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "NFC 就绪",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 长按菜单（仅图片相关）
                    OverlayListPopup(
                        show = showNfcMenu,
                        alignment = PopupPositionProvider.Align.TopEnd,
                        popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                        onDismissRequest = { showNfcMenu = false }
                    ) {
                        ListPopupColumn {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showNfcMenu = false
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                    .padding(horizontal = 24.dp, vertical = 16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.Icon(
                                        imageVector = Icons.Filled.Image, contentDescription = null,
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = if (cardBitmap != null) "更改图片" else "设置图片",
                                        style = MiuixTheme.textStyles.body1, color = MiuixTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }
                    }
                }

                // 社交平台切换（横向图标行）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                ) {
                    uiState.platforms.forEachIndexed { index, (fieldKey, _) ->
                        val isSelected = index == uiState.selectedPlatformIndex
                        val displayName = FIELD_DEF_MAP[fieldKey]?.displayName ?: fieldKey
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onSelectPlatform(index) }
                        ) {
                            PlatformIcon(
                                fieldKey = fieldKey,
                                color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.3f),
                                sizeDp = 36f
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = displayName,
                                style = MiuixTheme.textStyles.footnote2,
                                color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 1
                            )
                        }
                    }
                }
            } // close else (needSetup == false)

                // 引导完成后的提示
                if (isOnboardingCompleted(context) && !needSetup && platformNames.isEmpty()) {
                    FirstTimeHint(
                        text = "点击上方名片卡片或按钮来编辑你的名片信息",
                        hintKey = "social_edit_profile",
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                // 名字和平台 ID
                if (selectedPlatform != null) {
                    val entry = selectedPlatform.second
                    val platformDef = FIELD_DEF_MAP[selectedPlatform.first]
                    val idLabel = platformDef?.inputHint?.let { hint ->
                        if (hint.contains("或")) hint.substringBefore("或").trim() else hint.ifBlank { platformDef.displayName + "号" }
                    } ?: (platformDef?.displayName?.plus("号") ?: "ID")

                    var editTarget by remember { mutableStateOf<EditTarget?>(null) }
                    var editText by remember { mutableStateOf("") }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .height(IntrinsicSize.Min)
                    ) {
                        // 左框：昵称
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(miuixShape(8.dp))
                                .background(MiuixTheme.colorScheme.surfaceContainer)
                                .clickable {
                                    editText = entry.displayName ?: ""
                                    editTarget = EditTarget.NAME
                                }
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Text(text = "名字", textAlign = TextAlign.Center, style = MiuixTheme.textStyles.subtitle)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = entry.displayName ?: "未设置", textAlign = TextAlign.Center, style = MiuixTheme.textStyles.subtitle, color = MiuixTheme.colorScheme.onSurfaceSecondary, maxLines = 1)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "点击修改", textAlign = TextAlign.Center, style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        // 右框：平台 ID
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(miuixShape(8.dp))
                                .background(MiuixTheme.colorScheme.surfaceContainer)
                                .clickable {
                                    editText = entry.value ?: ""
                                    editTarget = EditTarget.VALUE
                                }
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Text(text = idLabel, textAlign = TextAlign.Center, style = MiuixTheme.textStyles.subtitle)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = entry.value ?: "未设置", textAlign = TextAlign.Center, style = MiuixTheme.textStyles.subtitle, color = MiuixTheme.colorScheme.onSurfaceSecondary, maxLines = 1)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "点击修改", textAlign = TextAlign.Center, style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                        }
                    }

                    // 编辑对话框
                    val currentTarget = editTarget
                    if (currentTarget != null) {
                        val dialogTitle = when (currentTarget) {
                            EditTarget.NAME -> "编辑名字"
                            EditTarget.VALUE -> "编辑$idLabel"
                        }
                        val fieldLabel = when (currentTarget) {
                            EditTarget.NAME -> "平台昵称"
                            EditTarget.VALUE -> idLabel
                        }
                        WindowDialog(
                            show = true,
                            title = dialogTitle,
                            onDismissRequest = { editTarget = null }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(text = fieldLabel, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                Spacer(modifier = Modifier.height(4.dp))
                                TextField(
                                    value = editText,
                                    onValueChange = { editText = it },
                                    label = fieldLabel,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    TextButton(
                                        text = "取消",
                                        onClick = { editTarget = null },
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(20.dp))
                                    Button(
                                        onClick = {
                                            val newDisplayName = if (currentTarget == EditTarget.NAME) editText.trim().ifBlank { null } else entry.displayName
                                            val newValue = if (currentTarget == EditTarget.VALUE) editText.trim().ifBlank { null } else entry.value
                                            onUpdatePlatform(
                                                selectedPlatform.first,
                                                entry.jumpLink,
                                                newValue,
                                                newDisplayName,
                                                entry.avatarUrl,
                                                entry.originalLink
                                            )
                                            Log.d("SocialPage", "更新: target=$editTarget, value=$editText")
                                            editTarget = null
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColorsPrimary()
                                    ) {
                                        Text(text = "保存")
                                    }
                                }
                            }
                        }
                    }
                }

                // 二维码
                if (qrContent.isNotBlank()) {
                val pEntry = selectedPlatform?.second
                val displayValue = buildString {
                    if (!pEntry?.displayName.isNullOrBlank() && !pEntry?.value.isNullOrBlank()) {
                        append(pEntry.displayName)
                        append("（")
                        append(pEntry.value)
                        append("）")
                    } else if (!pEntry?.value.isNullOrBlank()) {
                        append(pEntry.value)
                    }
                }
                QrCodeCard(
                    content = qrContent,
                    userName = selectedPlatform?.second?.displayName ?: FIELD_DEF_MAP[selectedPlatform?.first]?.displayName ?: selectedPlatform?.first,
                    platformName = FIELD_DEF_MAP[selectedPlatform?.first]?.displayName ?: selectedPlatform?.first,
                    platformValue = displayValue.ifBlank { null },
                    avatarPath = avatarPath
                )
                }
            }
        }
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
                if (activity != null) onDismissNfcWriteDialog(activity)
            },
            onRetry = {
                if (activity != null) {
                    if (NfcHelper.isWriting) NfcHelper.stopWriting(activity)
                    NfcHelper.writeResult.value // reset
                    onStartNfcWrite(activity)
                }
            },
            onOpenNfcSettings = { NfcHelper.openNfcSettings(context) },
            onOpenShortLinkSettings = {
                if (activity != null) onDismissNfcWriteDialog(activity)
                onNavigateToSettings()
            }
        )
    }
}