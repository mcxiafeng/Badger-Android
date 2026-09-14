package top.mcxiafeng.badger.pages.setupguide

import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity as UserProfile
import top.mcxiafeng.badger.network.kindCanSync
import top.mcxiafeng.badger.ui.components.CropConfig
import top.mcxiafeng.badger.ui.components.CropMode
import top.mcxiafeng.badger.ui.components.ImageCropDialog
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.utils.BILIBILI_HEADERS
import top.mcxiafeng.badger.utils.Methods
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.User
import top.mcxiafeng.badger.platform.ImageCodec
import top.mcxiafeng.badger.platform.ImageFiles
import top.mcxiafeng.badger.platform.PlatformImage
import top.mcxiafeng.badger.platform.downloadImage
import top.mcxiafeng.badger.platform.loadOrientedImage
import top.mcxiafeng.badger.platform.rememberImagePickerLauncher
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.mcxiafeng.badger.shared.util.nowMs

private const val PROFILE_TAG = "SetupStepProfile"

private const val AVATAR_WEBP_QUALITY = 60
private const val PAGE_INDEX = 2

@Composable
internal fun SetupStepProfile(
    onBack: () -> Unit,
    onNext: () -> Unit,
    pageTrigger: Int = 2,
) {
    val scope = rememberCoroutineScope()
    val setupGuideViewModel: SetupGuideViewModel = koinViewModel()
    val profile by setupGuideViewModel.profile.collectAsState()

    var userName by remember { mutableStateOf("") }
    var avatarPath by remember { mutableStateOf<String?>(null) }
    var avatarImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    var cropSourceImage by remember { mutableStateOf<PlatformImage?>(null) }

    
    
    
    
    fun avatarFileExists(): Boolean = ImageFiles.avatarFileExists("user_avatar.webp")

    
    LaunchedEffect(userName) {
        setupGuideViewModel.setPageValid(PAGE_INDEX, userName.isNotBlank())
    }

    
    
    
    LaunchedEffect(profile, pageTrigger) {
        if (pageTrigger != 2) return@LaunchedEffect
        val existing = profile
        BadgerLog.d(
            PROFILE_TAG,
            "[INIT] existing profile: ${existing?.let { "name=${it.name}, avatar=${it.avatarPath}" } ?: "null"}"
        )
        if (existing != null) {
            if (userName.isBlank()) userName = existing.name
            if (avatarPath == null) {
                avatarPath = existing.avatarPath
                if (avatarPath != null) {
                    avatarImageBitmap = ImageFiles.loadImageBytes(avatarPath)?.let { bytes ->
                        runCatching { bytes.decodeToImageBitmap() }.getOrNull()
                    }
                    BadgerLog.d(
                        PROFILE_TAG,
                        "[INIT] avatarImageBitmap loaded: ${avatarImageBitmap != null}, size=${avatarImageBitmap?.width}x${avatarImageBitmap?.height}"
                    )
                }
            }

            
            
            
            
            
            
            
            
            val initialAvatarPath = avatarPath
            if (initialAvatarPath.isNullOrBlank()) {
                val platformsMap = top.mcxiafeng.badger.data.repository.ContactMapper.decodePlatformsMap(existing.platformsJson)
                val canSyncEntry = platformsMap?.entries?.firstOrNull { e ->
                    e.key.kindCanSync && !e.value.avatarUrl.isNullOrBlank()
                }
                val fallbackEntry = platformsMap?.entries?.firstOrNull { !it.value.avatarUrl.isNullOrBlank() }
                val chosen = canSyncEntry ?: fallbackEntry
                if (chosen != null) {
                    val downloaded = runCatching {
                        val url = chosen.value.avatarUrl!!
                        val headers = if (url.contains("hdslb.com") || url.contains("bilibili.com"))
                            BILIBILI_HEADERS else emptyMap()
                        downloadImage(url, headers = headers)
                    }.getOrNull()
                    
                    
                    
                    if (downloaded != null && avatarPath.isNullOrBlank() && !avatarFileExists()) {
                        val scaled = ImageCodec.scaleToMaxSide(downloaded, ImageCodec.AVATAR_SIZE)
                        val bytes = ImageCodec.encodeWebp(scaled, AVATAR_WEBP_QUALITY)
                        val savedPath = bytes?.let { ImageFiles.saveAvatarImage(it, "user_avatar.webp") }
                        
                        
                        
                        if (avatarPath.isNullOrBlank() && savedPath != null) {
                            avatarPath = savedPath
                            avatarImageBitmap = bytes?.let { b -> runCatching { b.decodeToImageBitmap() }.getOrNull() }
                            BadgerLog.d(PROFILE_TAG, "[INIT] avatar auto-populated from platform ${chosen.key}")
                        } else {
                            BadgerLog.d(PROFILE_TAG, "[INIT] avatar race: skipped override (user won)")
                        }
                        if (scaled !== downloaded) scaled.close()
                        downloaded.close()
                    } else {
                        downloaded?.close()
                    }
                }
            }
        }
    }

    val pickAvatarLauncher = rememberImagePickerLauncher { bytes ->
        BadgerLog.d(PROFILE_TAG, "[AVATAR_PICKER] result bytes=${bytes?.size}")
        if (bytes != null) {
            scope.launch(BadgerDispatchers.io) {
                val image = loadOrientedImage(bytes)
                if (image != null) {
                    cropSourceImage = image
                }
            }
        }
    }

    SetupStepScaffold(
        onBack = onBack,
        onNext = {
            scope.launch {
                val existing = setupGuideViewModel.getUserProfileOnce()
                BadgerLog.d(
                    PROFILE_TAG,
                    "[NEXT] before save: userName=$userName, avatarPath=$avatarPath"
                )
                val updated = (existing ?: UserProfile(name = "", updateTime = nowMs())).copy(
                    name = userName.trim(),
                    avatarPath = avatarPath ?: existing?.avatarPath,
                    updateTime = nowMs(),
                )
                BadgerLog.d(PROFILE_TAG, "[NEXT] saving: name=${updated.name}, avatar=${updated.avatarPath}")
                setupGuideViewModel.saveUserProfile(updated)
                onNext()
            }
        },
        nextEnabled = userName.isNotBlank(),
        nextText = "继续",
        backText = "上一步",
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BadgerSpacing.xxl, vertical = BadgerSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StepHeader(
                title = "设置你的资料",
                subtitle = "昵称会显示在分享的名片上",
                icon = Lucide.User,
            )

            Spacer(modifier = Modifier.height(BadgerSpacing.xxl))

            ProfileAvatarPicker(
                avatarImageBitmap = avatarImageBitmap,
                userName = userName,
                onPickAvatar = { pickAvatarLauncher.launch() },
            )

            Spacer(modifier = Modifier.height(BadgerSpacing.lg))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(BadgerSpacing.lg)) {
                    Text(
                        text = "昵称",
                        style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(modifier = Modifier.height(BadgerSpacing.xs))
                    TextField(
                        value = userName,
                        onValueChange = { userName = it },
                        label = "你的名字或昵称",
                        useLabelAsPlaceholder = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        
        if (cropSourceImage != null) {
            Dialog(
                onDismissRequest = { cropSourceImage = null },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnClickOutside = false,
                ),
            ) {
                ImageCropDialog(
                    image = cropSourceImage!!,
                    onConfirm = { croppedBytes ->
                        scope.launch {
                            val savedPath = ImageFiles.saveAvatarImage(croppedBytes, "user_avatar.webp")
                            avatarPath = savedPath
                            avatarImageBitmap = runCatching { croppedBytes.decodeToImageBitmap() }.getOrNull()
                            BadgerLog.d(PROFILE_TAG, "[CROP_CONFIRM] avatar saved: path=$avatarPath")
                            cropSourceImage = null
                        }
                    },
                    onDismiss = { cropSourceImage = null },
                    cropConfig = CropConfig(mode = CropMode.AVATAR, outputWidth = 256, outputHeight = 256),
                )
            }
        }
    }
}

@Composable
private fun ProfileAvatarPicker(
    avatarImageBitmap: ImageBitmap?,
    userName: String,
    onPickAvatar: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clickable { onPickAvatar() },
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(
                    if (avatarImageBitmap != null) Color.Transparent
                    else MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarImageBitmap != null) {
                Image(
                    bitmap = avatarImageBitmap,
                    contentDescription = "头像",
                    modifier = Modifier.size(96.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = userName.take(1).ifBlank { "?" },
                    style = MiuixTheme.textStyles.title1,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        }
        
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(28.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Lucide.Camera,
                contentDescription = "更换头像",
                modifier = Modifier.size(16.dp),
                tint = Color.White,
            )
        }
    }
}
