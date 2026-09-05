# -*- coding: utf-8 -*-
"""[KMP K13c] round-2 修复：import 缺失/签名对齐/残点清扫"""
from pathlib import Path
import re

S = Path(r"F:\Java\Android Project\Badger\shared\src\commonMain\kotlin\top\mcxiafeng\badger")

def edit(rel, *transforms):
    p = S / rel
    if not p.exists():
        print("MISSING:", rel)
        return
    s = p.read_text(encoding="utf-8")
    orig = s
    for t in transforms:
        s = t(s)
    if s != orig:
        p.write_text(s, encoding="utf-8", newline="")
        print("fixed:", rel)

def add_imports(s, imports):
    for imp in imports:
        if imp not in s:
            lines = s.split("\n")
            idxs = [i for i, l in enumerate(lines) if l.startswith("import ")]
            pos = (idxs[-1] + 1) if idxs else 0
            lines.insert(pos, imp)
            s = "\n".join(lines)
    return s

# ============ SocialPage: BadgerDispatchers import ============
edit("pages/social/SocialPage.kt", lambda s: add_imports(s, ["import top.mcxiafeng.badger.shared.util.BadgerDispatchers"]))

# ============ QrCodeCard: decodeToImageBitmap import ============
edit("pages/social/QrCodeCard.kt", lambda s: add_imports(s, [
    "import androidx.compose.ui.graphics.ImageBitmap",
    "import androidx.compose.ui.graphics.decodeToImageBitmap",
]))

# ============ SetupStepProfile: decodeToImageBitmap 是扩展函数需顶层接收者对齐（重看 122/166/274） ============
def profile_check(s):
    # 这三行是 decodeToImageBitmap(bytes) 调用——它是 ImageBitmap.Companion 扩展；
    # 正确形态：bytes.decodeToImageBitmap() 不存在；实际 API 是 decodeToImageBitmap(bytes: ByteArray): ImageBitmap 顶层
    # 若报 receiver mismatch 说明 import 的是 asImageBitmap 系列。直接断言当前调用形态
    return s
edit("pages/setupguide/SetupStepProfile.kt", profile_check)

# ============ LogViewerPage: 重建尾部（脚本切割破坏） ============
def logviewer(s):
    # 找到 packageLogs 之前的最后完整内容；把 getDeviceInfo 后的残破部分重写
    marker = "private fun packageLogs("
    idx = s.find(marker)
    if idx < 0:
        return s
    head = s[:idx]
    # 检查 head 里 shareOrSaveZip 调用是否保留
    tail_new = '''private fun packageLogs(appInfo: AppInfo): File? {
    val cacheDir = File(LogCollector.cacheDirPath(), "shared").apply { mkdirs() }
    val zipFile = File(cacheDir, "Badger-log-${formatZipTimestamp()}.zip")

    ZipOutputStream(zipFile.outputStream().buffered()).use { zos ->
        zos.putNextEntry(ZipEntry("device_info.txt"))
        zos.write(getDeviceInfo(appInfo).toByteArray())
        zos.closeEntry()

        zos.putNextEntry(ZipEntry("logcat.txt"))
        zos.write(LogCollector.collectRecentLogs().toByteArray())
        zos.closeEntry()
    }

    BadgerLog.d(TAG, "packageLogs: zip created at ${zipFile.absolutePath}")
    return zipFile
}

private fun shareOrSaveZip(zipFile: File) {
    try {
        SystemShare.shareFile(zipFile.absolutePath, "application/zip", "分享日志文件")
    } catch (e: Exception) {
        BadgerLog.e(TAG, "shareOrSaveZip failed", e)
        showToast("分享失败: ${e.message}")
    }
}
'''
    return head + tail_new
edit("pages/settings/LogViewerPage.kt", logviewer)

# ============ AboutPage: appInfo 声明 ============
def about(s):
    if "val appInfo = remember" not in s:
        # 找到 Scaffold 前的位置——通常在 composable 函数体开头
        m = re.search(r"(@Composable\s*\n(?:internal |private )?fun AboutPage\([^)]*\)\s*\{)", s)
        if m:
            s = s.replace(m.group(1), m.group(1) + "\n    val appInfo = remember { KoinComponentBy.get<AppInfo>() }\n")
    return add_imports(s, [
        "import top.mcxiafeng.badger.platform.AppInfo",
        "import top.mcxiafeng.badger.di.KoinComponentBy",
        "import androidx.compose.runtime.remember",
    ])
edit("pages/settings/AboutPage.kt", about)

# ============ ScannerPage: photoPickerLauncher.launch(fromGallery=?) 残参 ============
def scanner_page(s):
    s = s.replace(".launch(false)", ".launch()")
    s = s.replace(".launch(true)", ".launch()")
    return s
edit("pages/scanner/ScannerPage.kt", scanner_page)

# ============ ImportFromPlatformDialog: previewBitmap 残余 ============
def import_dialog(s):
    s = re.sub(r"\bpreviewBitmap\b(?!ImageBitmap)", "previewImageBitmap", s)
    s = s.replace("previewImageBitmap.asImageBitmap()", "previewImageBitmap")
    # 签名收口：预览参数 Bitmap? → ImageBitmap?
    s = s.replace("previewImageBitmap: Bitmap?", "previewImageBitmap: ImageBitmap?")
    return add_imports(s, [
        "import androidx.compose.ui.graphics.ImageBitmap",
    ])
edit("pages/person/contact/dialogs/ImportFromPlatformDialog.kt", import_dialog)

# ============ ContactDetailPage: LocalContext 残用 + launcher 调用 ============
def detail_page(s):
    # 94 行 LocalContext 残用——找上下文
    s = s.replace("val context = LocalContext.current\n", "")
    # 602 行 pickAvatarLauncher.launch(PickVisualMediaRequest(...))
    s = re.sub(r"pickAvatarLauncher\.launch\(\s*PickVisualMediaRequest\(ActivityResultContracts\.PickVisualMedia\.ImageOnly\)\s*\)",
               "pickAvatarLauncher.launch()", s)
    return add_imports(s, [
        "import top.mcxiafeng.badger.platform.PlatformImage",
        "import top.mcxiafeng.badger.platform.ImageFiles",
    ])
edit("pages/person/contact/detail/ContactDetailPage.kt", detail_page)

# ============ ContactDetailDialogs: PlatformImage import + crop 签名 ============
def detail_dialogs(s):
    return add_imports(s, [
        "import top.mcxiafeng.badger.platform.PlatformImage",
    ])
edit("pages/person/contact/detail/ContactDetailDialogs.kt", detail_dialogs)

# ============ ContactDetailDialogHost: imports + downloadAndSaveAvatar 调用对齐 ============
def detail_host(s):
    s = add_imports(s, [
        "import top.mcxiafeng.badger.platform.PlatformImage",
        "import top.mcxiafeng.badger.platform.GallerySaver",
        "import top.mcxiafeng.badger.platform.downloadImageAsPng",
        "import androidx.compose.ui.graphics.ImageBitmap",
    ])
    # downloadAndSaveAvatar(url, context, contactId, headers) 旧 4 参 → (url, contactId, headers)
    s = re.sub(r"downloadAndSaveAvatar\(([^,]+),\s*context,\s*([^,]+),", r"downloadAndSaveAvatar(\1, \2,", s)
    # timeoutMs 位置参数错位：downloadAndSaveAvatar(url, contactId, 8000) → (url, contactId)
    s = re.sub(r"downloadAndSaveAvatar\(([^,]+),\s*(\w+),\s*8000\)", r"downloadAndSaveAvatar(\1, \2)", s)
    return s
edit("pages/person/contact/detail/ContactDetailDialogHost.kt", detail_host)

# ============ UserProfileDetailPage: launch(PickVisualMediaRequest...) 残参 ============
def userprofile(s):
    s = re.sub(r"pickAvatarLauncher\.launch\(\s*PickVisualMediaRequest\(ActivityResultContracts\.PickVisualMedia\.ImageOnly\)\s*\)",
               "pickAvatarLauncher.launch()", s)
    return s
edit("pages/person/contact/UserProfileDetailPage.kt", userprofile)

# ============ UserProfileDetailComponents: loadAvatarBitmap ============
def userprofile_components(s):
    s = s.replace("Methods.loadAvatarBitmap(localAvatarPath)",
                  "ImageFiles.loadImageBytes(localAvatarPath)?.let { bytes -> runCatching { decodeToImageBitmap(bytes) }.getOrNull() }")
    return add_imports(s, [
        "import top.mcxiafeng.badger.platform.ImageFiles",
        "import androidx.compose.ui.graphics.ImageBitmap",
        "import androidx.compose.ui.graphics.decodeToImageBitmap",
    ])
edit("pages/person/contact/UserProfileDetailComponents.kt", userprofile_components)

# ============ CreateContactViewModel / CreateContactPage: HttpUtil + saveBitmapAsAvatar ============
def create_vm(s):
    s = s.replace("import top.mcxiafeng.badger.utils.HttpUtil\n", "")
    return add_imports(s, [
        "import top.mcxiafeng.badger.platform.ImageCodec",
        "import top.mcxiafeng.badger.platform.ImageFiles",
        "import top.mcxiafeng.badger.platform.PlatformImage",
    ])
edit("pages/person/contact/CreateContactViewModel.kt", create_vm)

def create_page(s):
    s = s.replace("import top.mcxiafeng.badger.utils.HttpUtil\n", "")
    return add_imports(s, [
        "import top.mcxiafeng.badger.platform.downloadImage",
    ])
edit("pages/person/contact/CreateContactPage.kt", create_page)

# ============ CollectionDetailPage: deleteFileQuietly import ============
edit("pages/card/CollectionDetailPage.kt",
     lambda s: add_imports(s, ["import top.mcxiafeng.badger.shared.util.deleteFileQuietly"]))

# ============ CardDialogs / CardComponents ============
edit("pages/card/CardDialogs.kt",
     lambda s: s.replace("cropSourceUri", "cropSourceImage"))
edit("pages/card/CardComponents.kt",
     lambda s: s.replace("backgroundBitmap", "backgroundImageBitmap"))

# ============ AppRoutes: KoinComponentBy 双 import 冲突 + Log 残留 ============
def app_routes(s):
    # 双 import（top.mcxiafeng.badger.di.KoinComponentBy 与别的路径）
    lines = s.split("\n")
    seen = set()
    out = []
    for l in lines:
        if l.startswith("import "):
            key = l.strip()
            if key in seen:
                continue
            seen.add(key)
        out.append(l)
    s = "\n".join(out)
    s = s.replace("BadgerLog.w(TAG_APP", "BadgerLog.w(\"AppRoutes\"")
    s = re.sub(r"\bLog\.w\(", "BadgerLog.w(", s)
    return add_imports(s, ["import top.mcxiafeng.badger.utils.BadgerLog"])
edit("AppRoutes.kt", app_routes)

print("done")
