package top.mcxiafeng.badger.pages.setupguide

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.UserProfile
import top.mcxiafeng.badger.data.rememberContactRepository
import top.mcxiafeng.badger.network.adapter.PlatformAdapterRegistry
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ui.components.CropConfig
import top.mcxiafeng.badger.ui.components.CropMode
import top.mcxiafeng.badger.ui.components.ImageCropDialog
import top.mcxiafeng.badger.utils.HttpUtil
import top.mcxiafeng.badger.utils.Methods
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
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = rememberContactRepository()

    var userName by rememberSaveable { mutableStateOf("") }
    var avatarPath by remember { mutableStateOf<String?>(null) }
    var cardImagePath by remember { mutableStateOf<String?>(null) }
    var avatarBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var cardBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    var activeCropMode by remember { mutableStateOf<CropMode?>(null) }
    var cropSourceUri by remember { mutableStateOf<Uri?>(null) }

    // 加载已有的 UserProfile，并从平台数据自动填充空字段
    LaunchedEffect(Unit) {
        val existing = repository.getUserProfileOnce()
        Log.d(TAG, "[INIT] existing profile: ${existing?.let { "name=${it.name}, avatar=${it.avatarPath}, cardImage=${it.cardImagePath}" } ?: "null"}")
        if (existing != null) {
            userName = existing.name
            avatarPath = existing.avatarPath
            cardImagePath = existing.cardImagePath
            if (avatarPath != null) {
                avatarBitmap = Methods.loadAvatarBitmap(avatarPath)
                Log.d(TAG, "[INIT] avatarBitmap loaded: ${avatarBitmap != null}, size=${avatarBitmap?.width}x${avatarBitmap?.height}")
            }
            if (cardImagePath != null) {
                val file = File(cardImagePath!!)
                if (file.exists()) {
                    cardBitmap = withContext(Dispatchers.IO) {
                        android.graphics.BitmapFactory.decodeFile(cardImagePath)
                    }
                    Log.d(TAG, "[INIT] cardBitmap loaded: ${cardBitmap != null}")
                } else {
                    Log.d(TAG, "[INIT] cardImage file NOT found at: $cardImagePath")
                }
            }

            // 自动从平台数据填充空的个人资料字段（仅对 canSync=true 的平台优先）
            if (userName.isBlank()) {
                val canSyncEntry = existing.platforms?.entries?.firstOrNull { e ->
                    val ct = FIELD_DEF_MAP[e.key]?.contactType
                    val adp = ct?.let { PlatformAdapterRegistry.getAdapter(it) }
                    adp?.canSync == true && !e.value.displayName.isNullOrBlank()
                }
                val fallbackEntry = existing.platforms?.entries?.firstOrNull { !it.value.displayName.isNullOrBlank() }
                val chosen = canSyncEntry ?: fallbackEntry
                if (chosen != null) {
                    userName = chosen.value.displayName!!
                    Log.d(TAG, "[INIT] userName auto-populated from platform ${chosen.key}: ${chosen.value.displayName}")
                }
            }
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
                            mapOf("Referer" to "https://space.bilibili.com/") else null
                        HttpUtil.downloadBitmap(url, headers = headers)
                    }
                    if (bitmap != null) {
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
                if (avatarPath != null || cardImagePath != null) {
                    val existing = repository.getUserProfileOnce()
                    val updated = (existing ?: UserProfile()).copy(
                        name = existing?.name ?: "",
                        avatarPath = avatarPath ?: existing?.avatarPath,
                        cardImagePath = cardImagePath ?: existing?.cardImagePath,
                        updateTime = System.currentTimeMillis()
                    )
                    repository.saveUserProfile(updated)
                    Log.d(TAG, "Profile step skipped, partial data saved: avatar=$avatarPath, cardImage=$cardImagePath")
                }
                onSkip()
            }
        },
        onNext = {
            scope.launch {
                val existing = repository.getUserProfileOnce()
                Log.d(TAG, "[NEXT] before save: userName=$userName, avatarPath=$avatarPath, cardImagePath=$cardImagePath, existing=${existing?.let { "name=${it.name}, avatar=${it.avatarPath}, cardImage=${it.cardImagePath}" } ?: "null"}")
                val updated = (existing ?: UserProfile()).copy(
                    name = userName.trim(),
                    avatarPath = avatarPath ?: existing?.avatarPath,
                    cardImagePath = cardImagePath ?: existing?.cardImagePath,
                    updateTime = System.currentTimeMillis()
                )
                Log.d(TAG, "[NEXT] saving: name=${updated.name}, avatar=${updated.avatarPath}, cardImage=${updated.cardImagePath}")
                repository.saveUserProfile(updated)
                val verify = repository.getUserProfileOnce()
                Log.d(TAG, "[NEXT] verify after save: name=${verify?.name}, avatar=${verify?.avatarPath}, cardImage=${verify?.cardImagePath}")
                onNext()
            }
        },
        nextEnabled = userName.isNotBlank()
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
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
            Dialog(
                onDismissRequest = { cropSourceUri = null; activeCropMode = null },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                    dismissOnClickOutside = false
                )
            ) {
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
            .clip(RoundedCornerShape(16.dp))
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