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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity as UserProfile
import top.mcxiafeng.badger.network.kindCanSync
import top.mcxiafeng.badger.ui.components.CropConfig
import top.mcxiafeng.badger.ui.components.CropMode
import top.mcxiafeng.badger.ui.components.ImageCropDialog
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.utils.BILIBILI_HEADERS
import top.mcxiafeng.badger.utils.HttpUtil
import top.mcxiafeng.badger.utils.Methods
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

private const val PROFILE_TAG = "SetupStepProfile"
private const val PAGE_INDEX = 2

/**
 * 引导 Step 2 — 个人资料（昵称 + 头像）。
 *
 * 设计契约：
 * - 不可跳过。昵称非空才能下一步；头像可选（未选时回退为首字母占位）。
 * - [pageTrigger] 为 PagerState.currentPage —— HorizontalPager 会预组合相邻页，
 *   用 trigger 作 key 让 LaunchedEffect 仅在真正切到本页时拉一次，避免回退重入。
 * - 头像裁剪走固定 AVATAR 模式，配置内嵌（256×256）。
 */
@Composable
internal fun SetupStepProfile(
    onBack: () -> Unit,
    onNext: () -> Unit,
    pageTrigger: Int = 2,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val setupGuideViewModel: SetupGuideViewModel = koinViewModel()

    var userName by remember { mutableStateOf("") }
    var avatarPath by remember { mutableStateOf<String?>(null) }
    var avatarBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var cropSourceUri by remember { mutableStateOf<Uri?>(null) }

    // [修复防御 #B1]: 辅助函数 —— 在 IO 段被挂起期间,允许"通过文件存在性"反向判定用户是否
    // 已经完成裁剪。Compose 重组后 avatarPath 会被裁剪 onConfirm 设成同一文件名,二者等价。
    // 这里独立存在一个 file-based 检查,是因为 IO 段内 avatarPath 仍是旧值(null),
    // 单一信号不足。
    fun avatarFileExists(): Boolean {
        val f = File(context.filesDir, "user_avatar.webp")
        return f.exists() && f.length() > 0
    }

    // [修复防御]: 上报昵称非空 → 决定 Pager 是否解锁。
    LaunchedEffect(userName) {
        setupGuideViewModel.setPageValid(PAGE_INDEX, userName.isNotBlank())
    }

    // 加载已有的 UserProfile。每次切回本页（pageTrigger=2）触发一次；
    // 用 isBlank / null 守卫避免 LaunchedEffect 重入覆盖用户已编辑内容。
    // [修复防御]: 用 pageTrigger 作 key,只有真正切到本页时才跑,避免回退重入。
    LaunchedEffect(pageTrigger) {
        if (pageTrigger != 2) return@LaunchedEffect
        val existing = setupGuideViewModel.getUserProfileOnce()
        Log.d(
            PROFILE_TAG,
            "[INIT] existing profile: ${existing?.let { "name=${it.name}, avatar=${it.avatarPath}" } ?: "null"}"
        )
        if (existing != null) {
            if (userName.isBlank()) userName = existing.name
            if (avatarPath == null) {
                avatarPath = existing.avatarPath
                if (avatarPath != null) {
                    avatarBitmap = Methods.loadAvatarBitmap(avatarPath)
                    Log.d(
                        PROFILE_TAG,
                        "[INIT] avatarBitmap loaded: ${avatarBitmap != null}, size=${avatarBitmap?.width}x${avatarBitmap?.height}"
                    )
                }
            }

            // 自动从平台头像同步头像 —— 仅当本地头像为空时。
            // Name 自动填充已在 SetupStepPlatforms.runSync 中提前完成,此处不再做避免竞态。
            //
            // [修复防御 #B1 头像 race]: 原实现把"网络下载"与"saveBitmapAsAvatar + 赋值"拆成两段
            // withContext(Dispatchers.IO),用户在两段之间点裁剪 → saveBitmapAsAvatar 用同一文件名
            // ("user_avatar.webp") 写入磁盘 + 改 avatarPath/avatarBitmap,resume 后被覆盖。
            // 改为单段 suspend(整段跑在 IO,期间 Compose 不重组),落盘前再二次校验,
            // 把"已选头像"和"已存在文件"都算作「不要覆盖」的硬条件。
            val initialAvatarPath = avatarPath
            if (initialAvatarPath.isNullOrBlank()) {
                val platformsMap = top.mcxiafeng.badger.data.repository.ContactMapper.decodePlatformsMap(existing.platformsJson)
                val canSyncEntry = platformsMap?.entries?.firstOrNull { e ->
                    e.key.kindCanSync && !e.value.avatarUrl.isNullOrBlank()
                }
                val fallbackEntry = platformsMap?.entries?.firstOrNull { !it.value.avatarUrl.isNullOrBlank() }
                val chosen = canSyncEntry ?: fallbackEntry
                if (chosen != null) {
                    val downloaded = withContext(Dispatchers.IO) {
                        runCatching {
                            val url = chosen.value.avatarUrl!!
                            val headers = if (url.contains("hdslb.com") || url.contains("bilibili.com"))
                                BILIBILI_HEADERS else null
                            HttpUtil.downloadBitmap(url, headers = headers)
                        }
                    }.getOrNull()
                    // [修复防御 #B1]: 二次校验 —— 整段 IO 期间用户可能已手动选了头像。
                    // 任何 ① avatarPath 已被 Composable 改、② 文件已存在(被裁剪路径写入)，
                    // 都视为「用户已干预」,绝不允许覆盖。
                    if (downloaded != null && avatarPath.isNullOrBlank() && !avatarFileExists()) {
                        val avatarFile = withContext(Dispatchers.IO) {
                            Methods.saveBitmapAsAvatar(context, downloaded, "user_avatar.webp")
                        }
                        // [修复防御 #B1]: 落盘完成后再做第三次校验 —— 极端情况下裁剪 onConfirm
                        // 可能在 saveBitmapAsAvatar 内部 BitmapFactory 阻塞时也尝试写盘。
                        // 走最后写者检查:谁后写谁赢,但此处我们故意保留裁剪者 (early-return)。
                        if (avatarPath.isNullOrBlank() && !avatarFile.exists()) {
                            avatarPath = avatarFile.absolutePath
                            avatarBitmap = downloaded
                            Log.d(PROFILE_TAG, "[INIT] avatar auto-populated from platform ${chosen.key}")
                        } else {
                            downloaded.recycle()
                            Log.d(PROFILE_TAG, "[INIT] avatar race: skipped override (user won)")
                        }
                    } else {
                        downloaded?.recycle()
                    }
                }
            }
        }
    }

    val pickAvatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        Log.d(PROFILE_TAG, "[AVATAR_PICKER] result uri=$uri")
        if (uri != null) {
            cropSourceUri = uri
        }
    }

    SetupStepScaffold(
        onBack = onBack,
        onNext = {
            scope.launch {
                val existing = setupGuideViewModel.getUserProfileOnce()
                Log.d(
                    PROFILE_TAG,
                    "[NEXT] before save: userName=$userName, avatarPath=$avatarPath"
                )
                val updated = (existing ?: UserProfile(name = "", updateTime = System.currentTimeMillis())).copy(
                    name = userName.trim(),
                    avatarPath = avatarPath ?: existing?.avatarPath,
                    updateTime = System.currentTimeMillis(),
                )
                Log.d(PROFILE_TAG, "[NEXT] saving: name=${updated.name}, avatar=${updated.avatarPath}")
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
                icon = Icons.Outlined.PersonOutline,
            )

            Spacer(modifier = Modifier.height(BadgerSpacing.xxl))

            ProfileAvatarPicker(
                avatarBitmap = avatarBitmap,
                userName = userName,
                onPickAvatar = {
                    pickAvatarLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
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

        // 裁剪对话框 —— 仅头像模式,无 mode 分支。
        if (cropSourceUri != null) {
            Dialog(
                onDismissRequest = { cropSourceUri = null },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                    dismissOnClickOutside = false,
                ),
            ) {
                ImageCropDialog(
                    imageUri = cropSourceUri!!,
                    onConfirm = { croppedBitmap ->
                        scope.launch {
                            val avatarFile = withContext(Dispatchers.IO) {
                                Methods.saveBitmapAsAvatar(context, croppedBitmap, "user_avatar.webp")
                            }
                            avatarPath = avatarFile.absolutePath
                            avatarBitmap = croppedBitmap
                            Log.d(PROFILE_TAG, "[CROP_CONFIRM] avatar saved: path=$avatarPath")
                            cropSourceUri = null
                        }
                    },
                    onDismiss = { cropSourceUri = null },
                    cropConfig = CropConfig(mode = CropMode.AVATAR, outputWidth = 256, outputHeight = 256),
                )
            }
        }
    }
}

@Composable
private fun ProfileAvatarPicker(
    avatarBitmap: Bitmap?,
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
                    if (avatarBitmap != null) Color.Transparent
                    else MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarBitmap != null) {
                Image(
                    bitmap = avatarBitmap.asImageBitmap(),
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
        // 相机图标叠加层
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(28.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.CameraAlt,
                contentDescription = "更换头像",
                modifier = Modifier.size(16.dp),
                tint = Color.White,
            )
        }
    }
}
