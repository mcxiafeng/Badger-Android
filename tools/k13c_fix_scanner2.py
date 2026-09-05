# -*- coding: utf-8 -*-
"""[KMP K13c] ImportFromPlatformDialog + ScannerDialogs 修复"""
from pathlib import Path

S = Path(r"F:\Java\Android Project\Badger\shared\src\commonMain\kotlin\top\mcxiafeng\badger\pages")

# ============ ImportFromPlatformDialog.kt ============
p = S / "person" / "contact" / "dialogs" / "ImportFromPlatformDialog.kt"
s = p.read_text(encoding="utf-8")
s = s.replace("import top.mcxiafeng.badger.utils.HttpUtil\n", "")
s = s.replace("import top.mcxiafeng.badger.utils.Methods\n",
              "import top.mcxiafeng.badger.platform.downloadAndStoreAvatar\nimport top.mcxiafeng.badger.platform.downloadImageAsPng\nimport top.mcxiafeng.badger.utils.Methods\n")
s = s.replace("import androidx.compose.ui.graphics.asImageBitmap\n",
              "import androidx.compose.ui.graphics.ImageBitmap\nimport androidx.compose.ui.graphics.decodeToImageBitmap\n")

# 预览下载
s = s.replace("""        val old = previewBitmap
        previewBitmap = if (url != null) {
            withContext(BadgerDispatchers.io) { HttpUtil.downloadBitmap(url) }
        } else null
        if (old != null && old !== previewBitmap) old.recycle()""",
"""        val old = previewImageBitmap
        previewImageBitmap = if (url != null) {
            downloadImageAsPng(url)?.let { bytes -> runCatching { decodeToImageBitmap(bytes) }.getOrNull() }
        } else null""")
s = s.replace("var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }",
              "var previewImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }")
s = s.replace("previewBitmap.asImageBitmap()", "previewImageBitmap")
s = s.replace("previewBitmap,", "previewImageBitmap,")

# 落盘
s = s.replace("""                                    if (avatarUrl != null) {
                                        val bmp = HttpUtil.downloadBitmap(avatarUrl)
                                        if (bmp != null) {
                                            avatarPath = Methods.saveBitmapAsAvatar(context, bmp, "user_avatar.webp")?.absolutePath
                                        } else {
                                            BadgerLog.w(TAG, "头像下载失败，仅写入 name/bio")
                                        }
                                    } else {""",
"""                                    if (avatarUrl != null) {
                                        val saved = downloadAndStoreAvatar(avatarUrl, "user_avatar.webp")
                                        if (saved != null) {
                                            avatarPath = saved
                                        } else {
                                            BadgerLog.w(TAG, "头像下载失败，仅写入 name/bio")
                                        }
                                    } else {""")
p.write_text(s, encoding="utf-8", newline="")
print("ImportFromPlatformDialog:", {k: s.count(k) for k in ["HttpUtil", "saveBitmapAsAvatar", "previewBitmap", "Bitmap"]})

# ============ ScannerDialogs.kt ============
p2 = S / "scanner" / "ScannerDialogs.kt"
s2 = p2.read_text(encoding="utf-8")
s2 = s2.replace("""    val responses = try {
        ContactNetworkResolver.identifyBatch(jobs.map { it.second })
    } catch (e: Throwable) {""",
"""    val responses = try {
        KoinComponentBy.get<ContactNetworkResolver>().identifyBatch(jobs.map { it.second })
    } catch (e: Throwable) {""")
if "import top.mcxiafeng.badger.di.KoinComponentBy" not in s2:
    s2 = s2.replace("import top.mcxiafeng.badger.network.ContactNetworkResolver\n",
                    "import top.mcxiafeng.badger.di.KoinComponentBy\nimport top.mcxiafeng.badger.network.ContactNetworkResolver\n")
p2.write_text(s2, encoding="utf-8", newline="")
print("ScannerDialogs identifyBatch:", s2.count("KoinComponentBy.get<ContactNetworkResolver>"))
