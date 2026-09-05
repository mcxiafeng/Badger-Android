# -*- coding: utf-8 -*-
"""[KMP K13c] SetupStepProfile 头像管线 common 化修复"""
from pathlib import Path

p = Path(r"F:\Java\Android Project\Badger\shared\src\commonMain\kotlin\top\mcxiafeng\badger\pages\setupguide\SetupStepProfile.kt")
s = p.read_text(encoding="utf-8")

# 1) imports
for imp in ["import android.graphics.Bitmap\n", "import android.net.Uri\n",
            "import androidx.activity.compose.rememberLauncherForActivityResult\n",
            "import androidx.activity.result.PickVisualMediaRequest\n",
            "import androidx.activity.result.contract.ActivityResultContracts\n",
            "import java.io.File\n",
            "import top.mcxiafeng.badger.utils.HttpUtil\n"]:
    s = s.replace(imp, "")
s = s.replace("import androidx.compose.foundation.Image\n",
"""import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
""")
s = s.replace("import top.mcxiafeng.badger.utils.BadgerLog\n",
"""import top.mcxiafeng.badger.platform.ImageCodec
import top.mcxiafeng.badger.platform.ImageFiles
import top.mcxiafeng.badger.platform.PlatformImage
import top.mcxiafeng.badger.platform.downloadImage
import top.mcxiafeng.badger.platform.loadOrientedImage
import top.mcxiafeng.badger.platform.rememberImagePickerLauncher
import top.mcxiafeng.badger.utils.BadgerLog
""")

# 2) 状态
s = s.replace("""    var avatarBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var cropSourceUri by remember { mutableStateOf<Uri?>(null) }""", """    var avatarImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    var cropSourceImage by remember { mutableStateOf<PlatformImage?>(null) }""")

# 3) avatarFileExists
s = s.replace("""    fun avatarFileExists(): Boolean {
        val f = File(context.filesDir, "user_avatar.webp")
        return f.exists() && f.length() > 0
    }""", """    fun avatarFileExists(): Boolean = ImageFiles.imageFileExists("user_avatar.webp")""")

# 4) INIT 加载
s = s.replace("""                if (avatarPath != null) {
                    avatarBitmap = Methods.loadAvatarBitmap(avatarPath)
                    BadgerLog.d(
                        PROFILE_TAG,
                        "[INIT] avatarBitmap loaded: ${avatarBitmap != null}, size=${avatarBitmap?.width}x${avatarBitmap?.height}"
                    )
                }""", """                if (avatarPath != null) {
                    avatarImageBitmap = ImageFiles.loadImageBytes(avatarPath)?.let { bytes ->
                        runCatching { decodeToImageBitmap(bytes) }.getOrNull()
                    }
                    BadgerLog.d(
                        PROFILE_TAG,
                        "[INIT] avatarImageBitmap loaded: ${avatarImageBitmap != null}, size=${avatarImageBitmap?.width}x${avatarImageBitmap?.height}"
                    )
                }""")

# 5) 自动同步下载
s = s.replace("""                if (chosen != null) {
                    val downloaded = withContext(BadgerDispatchers.io) {
                        runCatching {
                            val url = chosen.value.avatarUrl!!
                            val headers = if (url.contains("hdslb.com") || url.contains("bilibili.com"))
                                BILIBILI_HEADERS else null
                            HttpUtil.downloadBitmap(url, headers = headers)
                        }
                    }.getOrNull()""", """                if (chosen != null) {
                    val downloaded = runCatching {
                        val url = chosen.value.avatarUrl!!
                        val headers = if (url.contains("hdslb.com") || url.contains("bilibili.com"))
                            BILIBILI_HEADERS else emptyMap()
                        downloadImage(url, headers = headers)
                    }.getOrNull()""")

s = s.replace("""                    if (downloaded != null && avatarPath.isNullOrBlank() && !avatarFileExists()) {
                        val avatarFile = withContext(BadgerDispatchers.io) {
                            Methods.saveBitmapAsAvatar(context, downloaded, "user_avatar.webp")
                        }
                        // [修复防御 #B1]: 落盘完成后再做第三次校验 —— 极端情况下裁剪 onConfirm
                        // 可能在 saveBitmapAsAvatar 内部 BitmapFactory 阻塞时也尝试写盘。
                        // 走最后写者检查:谁后写谁赢,但此处我们故意保留裁剪者 (early-return)。
                        if (avatarPath.isNullOrBlank() && !avatarFile.exists()) {
                            avatarPath = avatarFile.absolutePath
                            avatarBitmap = downloaded
                            BadgerLog.d(PROFILE_TAG, "[INIT] avatar auto-populated from platform ${chosen.key}")
                        } else {
                            downloaded.recycle()
                            BadgerLog.d(PROFILE_TAG, "[INIT] avatar race: skipped override (user won)")
                        }
                    } else {
                        downloaded?.recycle()
                    }""", """                    if (downloaded != null && avatarPath.isNullOrBlank() && !avatarFileExists()) {
                        val scaled = ImageCodec.scaleToMaxSide(downloaded, ImageCodec.AVATAR_SIZE)
                        val bytes = ImageCodec.encodeWebp(scaled, AVATAR_WEBP_QUALITY)
                        val savedPath = bytes?.let { ImageFiles.saveAvatarImage(it, "user_avatar.webp") }
                        // [修复防御 #B1]: 落盘完成后再做第三次校验 —— 极端情况下裁剪 onConfirm
                        // 可能在落盘阻塞时也尝试写盘。
                        // 走最后写者检查:谁后写谁赢,但此处我们故意保留裁剪者 (early-return)。
                        if (avatarPath.isNullOrBlank() && savedPath != null) {
                            avatarPath = savedPath
                            avatarImageBitmap = bytes?.let { b -> runCatching { decodeToImageBitmap(b) }.getOrNull() }
                            BadgerLog.d(PROFILE_TAG, "[INIT] avatar auto-populated from platform ${chosen.key}")
                        } else {
                            BadgerLog.d(PROFILE_TAG, "[INIT] avatar race: skipped override (user won)")
                        }
                        if (scaled !== downloaded) scaled.close()
                        downloaded.close()
                    } else {
                        downloaded?.close()
                    }""")

# 6) picker
s = s.replace("""    val pickAvatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        BadgerLog.d(PROFILE_TAG, "[AVATAR_PICKER] result uri=$uri")
        if (uri != null) {
            cropSourceUri = uri
        }
    }""", """    val pickAvatarLauncher = rememberImagePickerLauncher { bytes ->
        BadgerLog.d(PROFILE_TAG, "[AVATAR_PICKER] result bytes=${bytes?.size}")
        if (bytes != null) {
            scope.launch(BadgerDispatchers.io) {
                val image = loadOrientedImage(bytes)
                if (image != null) {
                    cropSourceImage = image
                }
            }
        }
    }""")

# 7) 预览组件调用
s = s.replace("""            ProfileAvatarPicker(
                avatarBitmap = avatarBitmap,
                userName = userName,
                onPickAvatar = {
                    pickAvatarLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            )""", """            ProfileAvatarPicker(
                avatarImageBitmap = avatarImageBitmap,
                userName = userName,
                onPickAvatar = { pickAvatarLauncher.launch() },
            )""")

# 8) 裁剪对话框
s = s.replace("""        if (cropSourceUri != null) {
            Dialog(
                onDismissRequest = { cropSourceUri = null },""", """        if (cropSourceImage != null) {
            Dialog(
                onDismissRequest = { cropSourceImage = null },""")
s = s.replace("""                ImageCropDialog(
                    imageUri = cropSourceUri!!,
                    onConfirm = { croppedBitmap ->
                        scope.launch {
                            val avatarFile = withContext(BadgerDispatchers.io) {
                                Methods.saveBitmapAsAvatar(context, croppedBitmap, "user_avatar.webp")
                            }
                            avatarPath = avatarFile.absolutePath
                            avatarBitmap = croppedBitmap
                            BadgerLog.d(PROFILE_TAG, "[CROP_CONFIRM] avatar saved: path=$avatarPath")
                            cropSourceUri = null
                        }
                    },
                    onDismiss = { cropSourceUri = null },""", """                ImageCropDialog(
                    image = cropSourceImage!!,
                    onConfirm = { croppedBytes ->
                        scope.launch {
                            val savedPath = ImageFiles.saveAvatarImage(croppedBytes, "user_avatar.webp")
                            avatarPath = savedPath
                            avatarImageBitmap = runCatching { decodeToImageBitmap(croppedBytes) }.getOrNull()
                            BadgerLog.d(PROFILE_TAG, "[CROP_CONFIRM] avatar saved: path=$avatarPath")
                            cropSourceImage = null
                        }
                    },
                    onDismiss = { cropSourceImage = null },""")

# 9) ProfileAvatarPicker 组件
s = s.replace("""private fun ProfileAvatarPicker(
    avatarBitmap: Bitmap?,
    userName: String,
    onPickAvatar: () -> Unit,
) {""", """private fun ProfileAvatarPicker(
    avatarImageBitmap: ImageBitmap?,
    userName: String,
    onPickAvatar: () -> Unit,
) {""")
s = s.replace("if (avatarBitmap != null) Color.Transparent", "if (avatarImageBitmap != null) Color.Transparent")
s = s.replace("""            if (avatarBitmap != null) {
                Image(
                    bitmap = avatarBitmap.asImageBitmap(),""", """            if (avatarImageBitmap != null) {
                Image(
                    bitmap = avatarImageBitmap,""")

# 10) 常量
if "AVATAR_WEBP_QUALITY" not in s.split("private const val PROFILE_TAG")[0]:
    s = s.replace('private const val PROFILE_TAG = "SetupStepProfile"',
                  'private const val PROFILE_TAG = "SetupStepProfile"\n\n/** 裁剪落盘 WEBP 压缩质量（对齐原 Methods.AVATAR_QUALITY = 60）。 */\nprivate const val AVATAR_WEBP_QUALITY = 60')

p.write_text(s, encoding="utf-8", newline="")
residual = {k: s.count(k) for k in ["HttpUtil", "Bitmap", "cropSourceUri", "saveBitmapAsAvatar", "LocalContext", "asImageBitmap"]}
print("SetupStepProfile fixed; residual:", residual)
