package top.mcxiafeng.badger.pages.setupguide

import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing


import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import top.mcxiafeng.badger.ui.designsystem.BadgerShapes
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import top.mcxiafeng.badger.ui.designsystem.continuousClip as clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.MY_CONTACT_ID
import top.mcxiafeng.badger.data.UserProfile
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.network.adapter.PlatformAdapterRegistry
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ui.components.CropConfig
import top.mcxiafeng.badger.ui.components.CropMode
import top.mcxiafeng.badger.ui.components.ImageCropDialog
import top.mcxiafeng.badger.utils.HttpUtil
import top.mcxiafeng.badger.utils.BILIBILI_HEADERS
import top.mcxiafeng.badger.utils.Methods
import top.mcxiafeng.badger.ui.glass.scrollTopFade
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.io.FileOutputStream

@Composable
internal fun SetupStepProfile(
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    pageTrigger: Int = 2
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val setupGuideViewModel: SetupGuideViewModel = hiltViewModel()
    val userProfileRepository = setupGuideViewModel.userProfileRepository
    val contactRepository = setupGuideViewModel.contactRepository

    var userName by remember { mutableStateOf("") }
    var avatarPath by remember { mutableStateOf<String?>(null) }
    var cardImagePath by remember { mutableStateOf<String?>(null) }
    var avatarBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var cardBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    var activeCropMode by remember { mutableStateOf<CropMode?>(null) }
    var cropSourceUri by remember { mutableStateOf<Uri?>(null) }

    // 加载已有的 UserProfile。键为 pageTrigger 而非 Unit：
    // HorizontalPager 会预组合相邻页面，Unit 键意味着 Profile 在 Platforms 页
    // 就被组合过一次且不再重跑，导致后续翻到此页时数据始终为默认值。
    // 但每次 pageTrigger→2 都会触发，必须用 isBlank/null 守卫避免覆盖用户已编辑的内容。
    LaunchedEffect(pageTrigger) {
        if (pageTrigger != 2) return@LaunchedEffect
        val existing = loadMyProfile(userProfileRepository, contactRepository)
        Log.d(TAG, "[INIT] existing profile: ${existing?.let { "name=${it.name}, avatar=${it.avatarPath}, cardImage=${it.cardImagePath}" } ?: "null"}")
        if (existing != null) {
            // [修复防御]: 仅当字段为空时才从 DB 加载，避免 pageTrigger 重入时覆盖用户手动输入。
            if (userName.isBlank()) userName = existing.name
            if (avatarPath == null) {
                avatarPath = existing.avatarPath
                if (avatarPath != null) {
                    avatarBitmap = Methods.loadAvatarBitmap(avatarPath, context)
                    Log.d(TAG, "[INIT] avatarBitmap loaded: ${avatarBitmap != null}, size=${avatarBitmap?.width}x${avatarBitmap?.height}")
                }
            }
            if (cardImagePath == null) {
                cardImagePath = existing.cardImagePath
                if (cardImagePath != null) {
                    cardBitmap = Methods.loadBackgroundBitmap(cardImagePath, context)
                    Log.d(TAG, "[INIT] cardBitmap loaded: ${cardBitmap != null}")
                }
            }

            // Name 自动填充已移至 SetupStepPlatforms.runSync 中提前完成，
            // 此处不再做 auto-fill，避免 LaunchedEffect 异步竞态覆盖用户手动输入。
            if (avatarPath.isNullOrBlank()) {
                val canSyncEntry = existing.platforms?.entries?.firstOrNull { e ->
                    val ct = FIELD_DEF_MAP[e.key]?.contactType
                    val adp = ct?.let { PlatformAdapterRegistry.getAdapter(it) }
                    adp?.canSync == true && !e.value.avatarUrl.isNullOrBlank()
                }
                val fallbackEntry = existing.platforms?.entries?.firstOrNull { !it.value.avatarUrl.isNullOrBlank() }
                val chosen = canSyncEntry ?: fallbackEntry
                if (chosen != null) {
                    val bitmap = withContext(Dispatchers.IO) {
                        val url = chosen.value.avatarUrl!!
                        val headers = if (url.contains("hdslb.com") || url.contains("bilibili.com"))
                            BILIBILI_HEADERS else null
                        HttpUtil.downloadBitmap(url, headers = headers)
                    }
                    // [修复防御]: 下载期间用户可能已手动选了头像，重新检查避免覆盖。
                    if (bitmap != null && avatarPath.isNullOrBlank()) {
                        val avatarFile = withContext(Dispatchers.IO) {
                            Methods.saveBitmapAsAvatar(context, bitmap, "user_avatar.webp")
                        }
                        avatarPath = avatarFile.absolutePath
                        avatarBitmap = bitmap
                        Log.d(TAG, "[INIT] avatar auto-populated from platform ${chosen.key}")
                    }
                }
            }
        }
    }

    // 头像选择器
    val pickAvatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        Log.d(TAG, "[AVATAR_PICKER] result uri=$uri")
        if (uri != null) {
            cropSourceUri = uri
            activeCropMode = CropMode.AVATAR
            Log.d(TAG, "[AVATAR_PICKER] uri selected, cropSourceUri set, activeCropMode=AVATAR")
        }
    }

    // 背景图选择器
    val pickBackgroundLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        Log.d(TAG, "[BG_PICKER] result uri=$uri")
        if (uri != null) {
            cropSourceUri = uri
            activeCropMode = CropMode.BANNER
            Log.d(TAG, "[BG_PICKER] uri selected, cropSourceUri set, activeCropMode=BANNER")
        }
    }

    // 统一裁剪确认回调
    val onCropConfirm: (Bitmap) -> Unit = { croppedBitmap ->
        val currentCropMode = activeCropMode
        Log.d(TAG, "[CROP_CONFIRM] called! capturedCropMode=$currentCropMode, bitmap=${croppedBitmap.width}x${croppedBitmap.height}")
        scope.launch {
            when (currentCropMode) {
                CropMode.AVATAR -> {
                    val avatarFile = withContext(Dispatchers.IO) {
                        Methods.saveBitmapAsAvatar(context, croppedBitmap, "user_avatar.webp")
                    }
                    avatarPath = avatarFile.absolutePath
                    avatarBitmap = croppedBitmap
                    Log.d(TAG, "[CROP_CONFIRM] AVATAR done: path=$avatarPath, avatarBitmap set=${avatarBitmap != null}")
                }
                CropMode.BANNER -> {
                    val outputFile = withContext(Dispatchers.IO) {
                        val f = File(context.filesDir, "card_image.webp")
                        FileOutputStream(f).use { out ->
                            croppedBitmap.compress(Bitmap.CompressFormat.WEBP, 75, out)
                        }
                        f
                    }
                    cardImagePath = outputFile.absolutePath
                    cardBitmap = croppedBitmap
                    Log.d(TAG, "[CROP_CONFIRM] BANNER done: path=$cardImagePath, cardBitmap set=${cardBitmap != null}")
                }
                CropMode.COVER -> { /* not used in profile setup */ }
                CropMode.COLLECTION_BG -> { /* not used in profile setup */ }
                null -> Log.d(TAG, "[CROP_CONFIRM] capturedCropMode is NULL, skipping")
            }
            cropSourceUri = null
            activeCropMode = null
            Log.d(TAG, "[CROP_CONFIRM] dialog state cleared")
        }
    }

    SetupStepScaffold(
        onBack = onBack,
        onSkip = {
            scope.launch {
                if (userName.isNotBlank() || avatarPath != null || cardImagePath != null) {
                    // 身份字段写 contacts(-1)，名片背景图留 user_profile 行
                    saveMyProfileIdentity(userProfileRepository, contactRepository, userName, avatarPath, cardImagePath, requireName = false)
                    Log.d(TAG, "Profile step skipped, partial data saved: avatar=$avatarPath, cardImage=$cardImagePath")
                }
                onSkip()
            }
        },
        onNext = {
            scope.launch {
                saveMyProfileIdentity(userProfileRepository, contactRepository, userName, avatarPath, cardImagePath, requireName = true)
                Log.d(TAG, "[NEXT] saved: name=$userName, avatar=$avatarPath, cardImage=$cardImagePath")
                onNext()
            }
        },
        nextEnabled = userName.isNotBlank()
    ) {
        Column(
            modifier = Modifier.fillMaxSize().scrollTopFade().verticalScroll(rememberScrollState())
                .padding(start = 32.dp, end = 32.dp, top = 32.dp + BadgerSpacing.scrollContentTopGap, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "设置你的个人资料",
            style = MiuixTheme.textStyles.title2,
            color = MiuixTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "让别人认识你",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        // 头像选择
        ProfileAvatarPicker(
            avatarBitmap = avatarBitmap,
            userName = userName,
            onPickAvatar = {
                pickAvatarLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 名字输入
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "昵称", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Spacer(modifier = Modifier.height(4.dp))
                TextField(
                    value = userName,
                    onValueChange = { userName = it },
                    label = "你的名字或昵称",
                    useLabelAsPlaceholder = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 背景图选择
        ProfileBackgroundPicker(
            cardBitmap = cardBitmap,
            onPickBackground = {
                pickBackgroundLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        )
    }

        // 裁剪对话框
        Log.d(TAG, "[RENDER] cropSourceUri=$cropSourceUri, activeCropMode=$activeCropMode, avatarBitmap=${avatarBitmap != null}, cardBitmap=${cardBitmap != null}, avatarPath=$avatarPath, cardImagePath=$cardImagePath")
        if (cropSourceUri != null && activeCropMode != null) {
            ImageCropDialog(
                imageUri = cropSourceUri!!,
                onConfirm = onCropConfirm,
                onDismiss = {
                    cropSourceUri = null
                    activeCropMode = null
                },
                cropConfig = when (activeCropMode) {
                    CropMode.AVATAR -> CropConfig(mode = CropMode.AVATAR, outputWidth = 256, outputHeight = 256)
                    CropMode.BANNER -> CropConfig(mode = CropMode.BANNER, outputWidth = 1080)
                    CropMode.COVER -> CropConfig(mode = CropMode.COVER, outputWidth = 720, outputHeight = 960)
                    CropMode.COLLECTION_BG -> CropConfig(mode = CropMode.COLLECTION_BG, outputWidth = 1080)
                    null -> CropConfig()
                }
            )
        }
    }
}

@Composable
private fun ProfileAvatarPicker(
    avatarBitmap: android.graphics.Bitmap?,
    userName: String,
    onPickAvatar: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clickable { onPickAvatar() }
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(if (avatarBitmap != null) Color.Transparent else MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            if (avatarBitmap != null) {
                Image(
                    bitmap = avatarBitmap.asImageBitmap(),
                    contentDescription = "头像",
                    modifier = Modifier.size(80.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = userName.take(1).ifBlank { "?" },
                    style = MiuixTheme.textStyles.title1,
                    color = MiuixTheme.colorScheme.primary
                )
            }
        }
        // 相机图标叠加层
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
}

@Composable
private fun ProfileBackgroundPicker(
    cardBitmap: android.graphics.Bitmap?,
    onPickBackground: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(BadgerShapes.Surface)
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .clickable { onPickBackground() },
        contentAlignment = Alignment.Center
    ) {
        if (cardBitmap != null) {
            Image(
                bitmap = cardBitmap.asImageBitmap(),
                contentDescription = "名片背景",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "设置名片背景图",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
    }
}
/**
 * 保存"我的名片"身份字段（昵称/头像）到 contacts(-1)，
 * 名片背景图（cardImagePath）没有对应的 Contact 字段，继续存 user_profile 行。
 */
private suspend fun saveMyProfileIdentity(
    userProfileRepository: UserProfileRepository,
    contactRepository: ContactRepository,
    userName: String,
    avatarPath: String?,
    cardImagePath: String?,
    requireName: Boolean
) {
    contactRepository.getContactById(MY_CONTACT_ID)?.let { me ->
        contactRepository.updateContact(me.copy(
            name = if (requireName) userName.trim() else userName.trim().ifBlank { me.name },
            avatarPath = avatarPath ?: me.avatarPath,
            updateTime = System.currentTimeMillis()
        ))
    }
    if (cardImagePath != null) {
        userProfileRepository.updateCardImagePath(cardImagePath)
    }
}
