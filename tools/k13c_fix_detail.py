# -*- coding: utf-8 -*-
"""[KMP K13c] ContactDetail 家族（Page/Dialogs/DialogHost/Components/Avatar）Bitmap 管线 common 化"""
from pathlib import Path

BASE = Path(r"F:\Java\Android Project\Badger\shared\src\commonMain\kotlin\top\mcxiafeng\badger\pages\person\contact\detail")

def fix(path, *transforms):
    p = BASE / path
    s = p.read_text(encoding="utf-8")
    orig = s
    for t in transforms:
        s = t(s)
    if s != orig:
        p.write_text(s, encoding="utf-8", newline="")
        print("fixed:", path)
    else:
        print("NO-OP:", path)

# ============ ContactDetailPage.kt ============
def page_imports(s):
    for imp in ["import android.graphics.Bitmap\n", "import android.net.Uri\n",
                "import androidx.activity.compose.rememberLauncherForActivityResult\n",
                "import androidx.activity.result.PickVisualMediaRequest\n",
                "import androidx.activity.result.contract.ActivityResultContracts\n",
                "import androidx.compose.ui.platform.LocalContext\n",
                "import top.mcxiafeng.badger.utils.HttpUtil\n"]:
        s = s.replace(imp, "")
    s = s.replace("import androidx.lifecycle.compose.collectAsStateWithLifecycle\n",
"""import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
""")
    s = s.replace("import top.mcxiafeng.badger.utils.Methods\n",
"""import top.mcxiafeng.badger.platform.ImageFiles
import top.mcxiafeng.badger.platform.PlatformImage
import top.mcxiafeng.badger.platform.downloadImageAsPng
import top.mcxiafeng.badger.platform.loadOrientedImage
import top.mcxiafeng.badger.platform.rememberImagePickerLauncher
import top.mcxiafeng.badger.utils.Methods
""")
    return s

def page_picker(s):
    return s.replace("""    val pickAvatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) { cropSourceUri = uri; showCropDialog = true }
    }""", """    val pickAvatarLauncher = rememberImagePickerLauncher { bytes ->
        if (bytes != null) {
            scope.launch(BadgerDispatchers.io) {
                val image = loadOrientedImage(bytes)
                if (image != null) { cropSourceImage = image; showCropDialog = true }
            }
        }
    }""")

def page_oncrop(s):
    return s.replace("""    val onCropConfirm: (Bitmap) -> Unit = { croppedBitmap ->
        scope.launch {
            try {
                val avatarFile = Methods.saveBitmapAsAvatar(context, croppedBitmap, "contact_${contactId}_avatar.webp")
                viewModel.applyAvatarUpdate(contactId, avatarFile.absolutePath)
                avatarVersion++
                BadgerLog.d("ContactDetailPage", "Avatar cropped and saved: ${avatarFile.absolutePath}")
            } catch (e: Exception) {
                BadgerLog.e("ContactDetailPage", "设置头像失败", e)
                showToast("设置头像失败")
            }
        }
    }""", """    val onCropConfirm: (ByteArray) -> Unit = { croppedBytes ->
        scope.launch {
            try {
                val savedPath = ImageFiles.saveAvatarImage(croppedBytes, "contact_${contactId}_avatar.webp")
                viewModel.applyAvatarUpdate(contactId, savedPath ?: "")
                if (savedPath != null) avatarVersion++
                BadgerLog.d("ContactDetailPage", "Avatar cropped and saved: $savedPath")
            } catch (e: Exception) {
                BadgerLog.e("ContactDetailPage", "设置头像失败", e)
                showToast("设置头像失败")
            }
        }
    }""")

def page_avatar_state(s):
    return s.replace("""    // 头像位图（异步加载）：本地 avatarPath 优先，其次远程 avatarUrl
    var avatarBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val localAvatarPath = contact?.avatarPath
    val remoteAvatarUrl = contact?.avatarUrl
    LaunchedEffect(localAvatarPath, remoteAvatarUrl, avatarVersion) {
        val old = avatarBitmap
        avatarBitmap = if (!localAvatarPath.isNullOrBlank()) {
            Methods.loadAvatarBitmap(localAvatarPath)
        } else if (!remoteAvatarUrl.isNullOrBlank()) {
            HttpUtil.downloadBitmap(remoteAvatarUrl, timeoutMs = 5000)
        } else null
        if (old != null && old !== avatarBitmap) old.recycle()
    }""", """    // 头像位图（异步加载）：本地 avatarPath 优先，其次远程 avatarUrl（[KMP K13c] ImageBitmap）
    var avatarImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val localAvatarPath = contact?.avatarPath
    val remoteAvatarUrl = contact?.avatarUrl
    LaunchedEffect(localAvatarPath, remoteAvatarUrl, avatarVersion) {
        avatarImageBitmap = if (!localAvatarPath.isNullOrBlank()) {
            ImageFiles.loadImageBytes(localAvatarPath)?.let { bytes ->
                runCatching { decodeToImageBitmap(bytes) }.getOrNull()
            }
        } else if (!remoteAvatarUrl.isNullOrBlank()) {
            downloadImageAsPng(remoteAvatarUrl)?.let { bytes ->
                runCatching { decodeToImageBitmap(bytes) }.getOrNull()
            }
        } else null
    }""")

def page_clipboard(s):
    s = s.replace("Methods.copyToClipboard(context, f.fieldName, f.value)", "Methods.copyToClipboard(f.fieldName, f.value)")
    s = s.replace("Methods.copyToClipboard(context, pDisplayName, copyText)", "Methods.copyToClipboard(pDisplayName, copyText)")
    return s

def page_downloads(s):
    s = s.replace("""                                val headers = if (resolved.avatarUrl.contains("hdslb.com") || resolved.avatarUrl.contains("bilibili.com"))
                                    BILIBILI_HEADERS else null
                                avatarPath = withContext(BadgerDispatchers.io) {
                                    downloadAndSaveAvatar(resolved.avatarUrl!!, context, contactId, headers)
                                }""", """                                val headers = if (resolved.avatarUrl.contains("hdslb.com") || resolved.avatarUrl.contains("bilibili.com"))
                                    BILIBILI_HEADERS else emptyMap()
                                avatarPath = downloadAndSaveAvatar(resolved.avatarUrl!!, contactId, headers)""")
    s = s.replace("""                                    val headers = if (fallbackUrl.contains("hdslb.com") || fallbackUrl.contains("bilibili.com"))
                                        BILIBILI_HEADERS else null
                                    val newAvatarPath = downloadAndSaveAvatar(fallbackUrl, context, contactId, headers)""", """                                    val headers = if (fallbackUrl.contains("hdslb.com") || fallbackUrl.contains("bilibili.com"))
                                        BILIBILI_HEADERS else emptyMap()
                                    val newAvatarPath = downloadAndSaveAvatar(fallbackUrl, contactId, headers)""")
    return s

def page_misc(s):
    s = s.replace("Methods.deleteAvatarFile(", "ImageFiles.deleteImageFile(")
    s = s.replace("avatarBitmap = avatarBitmap,", "avatarImageBitmap = avatarImageBitmap,")
    s = s.replace("if (avatarBitmap != null) showAvatarPreview = true", "if (avatarImageBitmap != null) showAvatarPreview = true")
    # 状态与参数改名（Uri → PlatformImage）
    s = s.replace("var cropSourceUri by remember { mutableStateOf<Uri?>(null) }",
                  "var cropSourceImage by remember { mutableStateOf<PlatformImage?>(null) }")
    s = s.replace("cropSourceUri = cropSourceUri,", "cropSourceImage = cropSourceImage,")
    s = s.replace("onCropSourceUriChange = { cropSourceUri = it },", "onCropSourceImageChange = { cropSourceImage = it },")
    return s

fix("ContactDetailPage.kt", page_imports, page_picker, page_oncrop, page_avatar_state,
    page_clipboard, page_downloads, page_misc)

# ============ ContactDetailDialogs.kt ============
def dialogs_imports(s):
    for imp in ["import android.graphics.Bitmap\n", "import android.content.Context\n", "import android.net.Uri\n",
                "import top.mcxiafeng.badger.utils.HttpUtil\n"]:
        s = s.replace(imp, "")
    s = s.replace("import top.mcxiafeng.badger.ui.components.ImageCropDialog\n",
"""import top.mcxiafeng.badger.platform.ImageCodec
import top.mcxiafeng.badger.platform.ImageFiles
import top.mcxiafeng.badger.platform.downloadImage
import top.mcxiafeng.badger.ui.components.ImageCropDialog
""")
    return s

def dialogs_downloader(s):
    return s.replace("""internal suspend fun downloadAndSaveAvatar(
    url: String,
    context: Context,
    contactId: Long,
    headers: Map<String, String>? = null
): String? {
    return try {
        val bitmap = HttpUtil.downloadBitmap(url, headers = headers)
        if (bitmap != null) {
            val avatarFile = Methods.saveBitmapAsAvatar(context, bitmap, "contact_${contactId}_avatar.webp")
            BadgerLog.d("ContactDetailPage", "Avatar downloaded and saved: ${avatarFile.absolutePath}")
            avatarFile.absolutePath
        } else {
            BadgerLog.w("ContactDetailPage", "Failed to download avatar from: $url")
            null
        }
    } catch (e: Exception) {
        BadgerLog.e("ContactDetailPage", "Avatar download/save failed", e)
        null
    }
}""", """internal suspend fun downloadAndSaveAvatar(
    url: String,
    contactId: Long,
    headers: Map<String, String> = emptyMap()
): String? {
    return try {
        val image = downloadImage(url, headers = headers)
        if (image != null) {
            val scaled = ImageCodec.scaleToMaxSide(image, ImageCodec.AVATAR_SIZE)
            val bytes = ImageCodec.encodeWebp(scaled, AVATAR_SAVE_QUALITY)
            val savedPath = bytes?.let { ImageFiles.saveAvatarImage(it, "contact_${contactId}_avatar.webp") }
            if (scaled !== image) scaled.close()
            image.close()
            BadgerLog.d("ContactDetailPage", "Avatar downloaded and saved: $savedPath")
            savedPath
        } else {
            BadgerLog.w("ContactDetailPage", "Failed to download avatar from: $url")
            null
        }
    } catch (e: Exception) {
        BadgerLog.e("ContactDetailPage", "Avatar download/save failed", e)
        null
    }
}

/** 头像落盘 WEBP 压缩质量（对齐原 Methods.AVATAR_QUALITY = 60）。 */
private const val AVATAR_SAVE_QUALITY = 60""")

def dialogs_crop(s):
    s = s.replace("    cropSourceUri: Uri?,", "    cropSourceImage: PlatformImage?,")
    s = s.replace("    if (!show || cropSourceUri == null) return", "    if (!show || cropSourceImage == null) return")
    s = s.replace("            imageUri = cropSourceUri,", "            image = cropSourceImage,")
    s = s.replace("        cropSourceUri = cropSourceUri,", "        cropSourceImage = cropSourceImage,")
    return s

fix("ContactDetailDialogs.kt", dialogs_imports, dialogs_downloader, dialogs_crop)

# ============ ContactDetailDialogHost.kt ============
def host_types(s):
    s = s.replace("    cropSourceUri: Uri?,", "    cropSourceImage: PlatformImage?,")
    s = s.replace("    avatarBitmap: Bitmap?,", "    avatarImageBitmap: ImageBitmap?,")
    s = s.replace("    onCropSourceUriChange: (Uri?) -> Unit,", "    onCropSourceImageChange: (PlatformImage?) -> Unit,")
    s = s.replace("        cropSourceUri = cropSourceUri,", "        cropSourceImage = cropSourceImage,")
    s = s.replace("        onDismissCrop = { onCropSourceUriChange(null) },", "        onDismissCrop = { onCropSourceImageChange(null) },")
    s = s.replace("            fallbackBitmap = avatarBitmap,", "            fallbackImageBitmap = avatarImageBitmap,")
    return s

def host_save_original(s):
    return s.replace("""                        val original = it
                            ?: run {
                                val url = c.avatarUrl?.takeIf { it.isNotBlank() }
                                    ?: return@run null
                                val hdUrl = upgradeAvatarUrlToHd(url)
                                val headers = if (hdUrl.contains("hdslb.com") || hdUrl.contains("bilibili.com"))
                                    BILIBILI_HEADERS else null
                                withContext(BadgerDispatchers.io) {
                                    HttpUtil.downloadBitmap(hdUrl, headers = headers, timeoutMs = 8000)
                                        ?: if (hdUrl != url) HttpUtil.downloadBitmap(url, headers = headers, timeoutMs = 8000) else null
                                }
                            }
                        if (original == null) {
                            showToast("无法获取原图,请检查网络")
                            return@launch
                        }
                        val ok = withContext(BadgerDispatchers.io) {
                            Methods.saveBitmapToGallery(
                                context,
                                original,
                                "badger_avatar_${c.id}_${nowMs()}.png"
                            )
                        }
                        val msg = if (ok) "原图已保存到相册" else "保存失败"
                        showToast(msg)""", """                        val original = it
                            ?: run {
                                val url = c.avatarUrl?.takeIf { it.isNotBlank() }
                                    ?: return@run null
                                val hdUrl = upgradeAvatarUrlToHd(url)
                                val headers = if (hdUrl.contains("hdslb.com") || hdUrl.contains("bilibili.com"))
                                    BILIBILI_HEADERS else emptyMap()
                                downloadImageAsPng(hdUrl, timeoutMs = 8000, headers = headers)
                                    ?: if (hdUrl != url) downloadImageAsPng(url, timeoutMs = 8000, headers = headers) else null
                            }
                        if (original == null) {
                            showToast("无法获取原图,请检查网络")
                            return@launch
                        }
                        val ok = GallerySaver.saveImagePng(
                            original,
                            "badger_avatar_${c.id}_${nowMs()}.png"
                        )
                        val msg = if (ok) "原图已保存到相册" else "保存失败"
                        showToast(msg)""")

def host_imports(s):
    for imp in ["import android.graphics.Bitmap\n", "import android.net.Uri\n",
                "import android.content.Context\n",
                "import top.mcxiafeng.badger.utils.HttpUtil\n"]:
        s = s.replace(imp, "")
    s = s.replace("import top.yukonga.miuix.kmp.theme.MiuixTheme\n",
"""import androidx.compose.ui.graphics.ImageBitmap
import top.mcxiafeng.badger.platform.GallerySaver
import top.mcxiafeng.badger.platform.PlatformImage
import top.mcxiafeng.badger.platform.downloadImageAsPng
import top.yukonga.miuix.kmp.theme.MiuixTheme
""", 1)
    return s

fix("ContactDetailDialogHost.kt", host_imports, host_types, host_save_original)

# ============ ContactDetailComponents.kt ============
def components_fix(s):
    s = s.replace("import android.graphics.Bitmap\n", "")
    s = s.replace("import androidx.compose.ui.graphics.asImageBitmap\n",
                  "import androidx.compose.ui.graphics.ImageBitmap\n")
    s = s.replace("    avatarBitmap: Bitmap?,", "    avatarImageBitmap: ImageBitmap?,")
    s = s.replace("                        if (avatarBitmap != null) {", "                        if (avatarImageBitmap != null) {")
    s = s.replace("                                bitmap = avatarBitmap.asImageBitmap(),", "                                bitmap = avatarImageBitmap,")
    return s

fix("ContactDetailComponents.kt", components_fix)
print("done")
