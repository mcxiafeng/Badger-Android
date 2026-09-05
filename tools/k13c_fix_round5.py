# -*- coding: utf-8 -*-
"""[KMP K13c] round-5：17 个残点清零"""
from pathlib import Path
import re

S = Path(r"F:\Java\Android Project\Badger\shared\src\commonMain\kotlin\top\mcxiafeng\badger")

def edit(rel, *transforms):
    p = S / rel
    s = p.read_text(encoding="utf-8")
    orig = s
    for t in transforms:
        s = t(s)
    if s != orig:
        p.write_text(s, encoding="utf-8", newline="")
        print("fixed:", rel)
    else:
        print("NO-OP:", rel)

# 1) LogViewerPage formatZipTimestamp：days/daySeconds 是 Long→Int 运算错位
def logviewer_ts(s):
    s = s.replace("    val total = nowMs() / 1000\n    val days = total / 86400\n    val daySeconds = (total % 86400).toInt()",
                  "    val total = (nowMs() / 1000).toInt()\n    val days = total / 86400\n    val daySeconds = total % 86400")
    return s
edit("pages/settings/LogViewerPage.kt", logviewer_ts)

# 2) UserProfileDetailComponents：状态类型 Bitmap? → ImageBitmap?（残留声明）
def upc(s):
    s = s.replace("""                    var avatarBitmap by remember(profile?.avatarPath, avatarVersion) {
                        mutableStateOf<Bitmap?>(null)
                    }""", """                    var avatarImageBitmap by remember(profile?.avatarPath, avatarVersion) {
                        mutableStateOf<ImageBitmap?>(null)
                    }""")
    s = s.replace("avatarBitmap = ImageFiles.loadImageBytes(localAvatarPath)", "avatarImageBitmap = ImageFiles.loadImageBytes(localAvatarPath)")
    return s
edit("pages/person/contact/UserProfileDetailComponents.kt", upc)

# 3) CreateContactViewModel：整块换 downloadAndStoreAvatar
def cvm(s):
    s = s.replace("""        var avatarPath: String? = null
        if (!avatarUrl.isNullOrBlank()) {
            try {
                val bmp = HttpUtil.downloadBitmap(avatarUrl)
                if (bmp != null) {
                    avatarPath = Methods.saveBitmapAsAvatar(context, bmp, "contact_avatar_${now}.webp").absolutePath
                } else {
                    BadgerLog.w(TAG, "createContactFromResolve: 头像下载失败,保留 avatarUrl")
                }
            } catch (e: Exception) {
                BadgerLog.w(TAG, "createContactFromResolve: 头像下载异常", e)
            }""", """        var avatarPath: String? = null
        if (!avatarUrl.isNullOrBlank()) {
            try {
                avatarPath = downloadAndStoreAvatar(avatarUrl, "contact_avatar_${now}.webp")
                if (avatarPath == null) {
                    BadgerLog.w(TAG, "createContactFromResolve: 头像下载失败,保留 avatarUrl")
                }
            } catch (e: Exception) {
                BadgerLog.w(TAG, "createContactFromResolve: 头像下载异常", e)
            }""")
    return s
edit("pages/person/contact/CreateContactViewModel.kt", cvm)

# 4) CreateContactPage：previewBitmap（另一处独立声明，同 ImportFromPlatformDialog 模式）
def ccp(s):
    s = s.replace("""        val old = previewBitmap
        previewBitmap = if (url != null) {
            withContext(BadgerDispatchers.io) { HttpUtil.downloadBitmap(url) }
        } else null
        if (old != null && old !== previewBitmap) old.recycle()""",
"""        previewImageBitmap = if (url != null) {
            downloadImageAsPng(url)?.let { bytes -> runCatching { bytes.decodeToImageBitmap() }.getOrNull() }
        } else null""")
    s = s.replace("var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }",
                  "var previewImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }")
    s = s.replace("previewBitmap.asImageBitmap()", "previewImageBitmap")
    s = re.sub(r"\bpreviewBitmap\b(?!ImageBitmap)", "previewImageBitmap", s)
    return s
edit("pages/person/contact/CreateContactPage.kt", ccp)

# 5) CardComponents：backgroundImageBitmap 声明侧
def card_comp(s):
    s = s.replace("backgroundImageBitmap == null", "backgroundBitmap == null")
    return s
edit("pages/card/CardComponents.kt", card_comp)

# 6) CollectionDetailPage：deleteFileQuietly import（round-3 没打上）
def cdp(s):
    if "import top.mcxiafeng.badger.shared.util.deleteFileQuietly" not in s:
        lines = s.split("\n")
        idxs = [i for i, l in enumerate(lines) if l.startswith("import ")]
        pos = idxs[-1] + 1
        lines.insert(pos, "import top.mcxiafeng.badger.shared.util.deleteFileQuietly")
        s = "\n".join(lines)
    return s
edit("pages/card/CollectionDetailPage.kt", cdp)

# 7) ContactDetailDialogHost：ImageImageBitmap（坏 replace 产物）
def cdh(s):
    s = s.replace("ImageImageBitmap", "ImageBitmap")
    s = s.replace("avatarBitmap: Bitmap?", "avatarImageBitmap: ImageBitmap?")
    s = s.replace("var avatarBitmap by remember { mutableStateOf<Bitmap?>(null) }", "")
    return s
edit("pages/person/contact/detail/ContactDetailDialogHost.kt", cdh)

# 8) ContactDetailAvatar 75：upgradeAvatarUrlToHd 调用 receiver mismatch——查行
p = S / "pages/person/contact/detail/ContactDetailAvatar.kt"
s = p.read_text(encoding="utf-8")
print("Avatar line 75:", s.split("\n")[74])

# 9) CameraPreviewSlot.android 127：getSurfaceSize
p2 = Path(r"F:\Java\Android Project\Badger\shared\src\androidMain\kotlin\top\mcxiafeng\badger\platform\CameraPreviewSlot.android.kt")
s2 = p2.read_text(encoding="utf-8")
print("CameraPreviewSlot 127:", s2.split("\n")[126])

# 10) ImportFromPlatformDialog：recycle 残留（133/143/158）+ 249/337 类型
p3 = S / "pages/person/contact/dialogs/ImportFromPlatformDialog.kt"
s3 = p3.read_text(encoding="utf-8")
s3 = re.sub(r"\n\s*if \(old != null && old !== \w+\) old\.recycle\(\)", "", s3)
s3 = re.sub(r"\n\s*old\.recycle\(\)", "", s3)
s3 = s3.replace(".recycle()", "")
s3 = s3.replace("previewImageBitmap: Bitmap?", "previewImageBitmap: ImageBitmap?")
s3 = s3.replace("previewImageBitmap: ImageBitmap?,", "previewImageBitmap: ImageBitmap?,")
s3 = s3.replace("Bitmap, but 'ImageBitmap", "ImageBitmap, but 'ImageBitmap")
s3 = s3.replace(": Bitmap,", ": ImageBitmap,")
s3 = s3.replace("(Bitmap)", "(ImageBitmap)")
p3.write_text(s3, encoding="utf-8", newline="")
print("ImportFromPlatformDialog deep cleaned")
print("done")
