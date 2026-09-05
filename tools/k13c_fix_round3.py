# -*- coding: utf-8 -*-
"""[KMP K13c] round-3：NavBarConfig 去 ctx、LogViewer 语法、SetupStepProfile/ImageBitmap 收尾"""
from pathlib import Path
import re

S = Path(r"F:\Java\Android Project\Badger\shared\src\commonMain\kotlin\top\mcxiafeng\badger")
APP = Path(r"F:\Java\Android Project\Badger\app\src\main\kotlin\top\mcxiafeng\badger")

def edit(path, *transforms):
    p = Path(path)
    s = p.read_text(encoding="utf-8")
    orig = s
    for t in transforms:
        s = t(s)
    if s != orig:
        p.write_text(s, encoding="utf-8", newline="")
        print("fixed:", p.name)

# ============ NavBarConfig：ctx 形参全部移除（PrefsStore 直存，ctx 本就被忽略） ============
def nav_config(s):
    s = s.replace("    fun initialize(@Suppress(\"UNUSED_PARAMETER\") context: android.content.Context) {", "    fun initialize() {")
    s = re.sub(r"fun (isFloatingEnabled|saveFloatingEnabled|isLiquidGlassEnabled|saveLiquidGlassEnabled|saveBlurIntensity|saveAdvancedBlurEnabled|saveEffectMode|isEffectMode|saveBlurRadiusDp|isAdvancedBlurEnabled|getBlurRadiusDp|blurRadiusDp)\(@Suppress\(\"UNUSED_PARAMETER\"\) context: android\.content\.Context([,)])", r"fun \1(\2", s)
    s = re.sub(r"fun (isFloatingEnabled|saveFloatingEnabled|isLiquidGlassEnabled|saveLiquidGlassEnabled|saveBlurIntensity|saveAdvancedBlurEnabled|saveEffectMode|isEffectMode|saveBlurRadiusDp|isAdvancedBlurEnabled|getBlurRadiusDp|blurRadiusDp)\(context: android\.content\.Context([,)])", r"fun \1(\2", s)
    return s
edit(S / "ui/navigation/NavBarConfig.kt", nav_config)

# 全部调用点去 ctx
CALLERS = list(S.rglob("*.kt")) + list(APP.rglob("*.kt"))
def strip_ctx(s):
    return re.sub(r"NavBarConfig\.(\w+)\(context[,\s]*", r"NavBarConfig.\1(", s).replace("NavBarConfig.initialize(this)", "NavBarConfig.initialize()")
for p in CALLERS:
    s = p.read_text(encoding="utf-8")
    if "NavBarConfig." in s:
        s2 = strip_ctx(s)
        if s2 != s:
            p.write_text(s2, encoding="utf-8", newline="")
            print("ctx stripped:", p.name)

# ============ LogViewerPage：import 插进文件中部（脚本切割事故）——挪回头部 ============
def logviewer(s):
    lines = s.split("\n")
    body_start = 0
    for i, l in enumerate(lines):
        if l.startswith("import "):
            body_start = i
    # 收集所有 import 行（全文件范围）
    imports = [l for l in lines if l.startswith("import ")]
    rest = [l for l in lines if not l.startswith("import ")]
    # rest 里第一个非空行之前是 package/注释区
    return "\n".join(rest[:1] + [""] + imports + [""] + rest[1:])
p = S / "pages/settings/LogViewerPage.kt"
s = p.read_text(encoding="utf-8")
s = logviewer(s)
# ZipEntry/ZipOutputStream/Zip import 修复
if "import java.util.zip.ZipEntry" not in s:
    s = s.replace("import java.util.zip.ZipOutputStream\n", "import java.util.zip.ZipEntry\nimport java.util.zip.ZipOutputStream\n")
if "import java.util.zip.ZipOutputStream" not in s:
    s = s.replace("import java.io.File\n", "import java.io.File\nimport java.util.zip.ZipEntry\nimport java.util.zip.ZipOutputStream\n")
# Int 参数错误（67 行）：formatZipTimestamp 内 p2 参数——已无问题；是 File(LogCollector.cacheDirPath(),...)?
p.write_text(s, encoding="utf-8", newline="")
print("LogViewerPage imports hoisted")

# ============ SetupStepProfile/QrCodeCard/ContactDetailAvatar/ContactDetailPage/ImportFromPlatformDialog：
# decodeToImageBitmap 是顶层扩展（androidx.compose.ui.graphics），必须 import 顶层函数而非扩展 receiver 形态。
# 实际 API：fun ByteArray.decodeToImageBitmap(): ImageBitmap（扩展）→ 需 bytes.decodeToImageBitmap()
def fix_decode(s):
    # decodeToImageBitmap(bytes) → bytes.decodeToImageBitmap()
    return re.sub(r"decodeToImageBitmap\((\w+)\)", r"\1.decodeToImageBitmap()", s)
for rel in ["pages/setupguide/SetupStepProfile.kt", "pages/social/QrCodeCard.kt",
            "pages/person/contact/UserProfileDetailComponents.kt",
            "pages/person/contact/UserProfileDetailPage.kt",
            "pages/person/contact/detail/ContactDetailPage.kt"]:
    p = S / rel
    s = p.read_text(encoding="utf-8")
    s2 = fix_decode(s)
    if s2 != s:
        p.write_text(s2, encoding="utf-8", newline="")
        print("decode fixed:", rel)

# ============ ScannerPage: launch("image/*") → launch() ============
p = S / "pages/scanner/ScannerPage.kt"
s = p.read_text(encoding="utf-8")
s = s.replace('photoPickerLauncher.launch("image/*")', "photoPickerLauncher.launch()")
p.write_text(s, encoding="utf-8", newline="")
print("ScannerPage launch arg fixed")

# ============ ImportFromPlatformDialog: .recycle() 残留 + 249 签名 ============
p = S / "pages/person/contact/dialogs/ImportFromPlatformDialog.kt"
s = p.read_text(encoding="utf-8")
s = re.sub(r"if \(old != null && old !== \w+\) old\.recycle\(\)\n", "", s)
s = s.replace("previewImageBitmap: Bitmap?", "previewImageBitmap: ImageBitmap?")
s = s.replace(".asImageBitmap()", "")
# 107 行 receiver mismatch：downloadImage 返回 PlatformImage，读取 .width 等——无需处理；
# 但 bytes 解码链修正
s = s.replace("downloadImageAsPng(url)?.let { bytes -> runCatching { decodeToImageBitmap(bytes) }.getOrNull() }",
              "downloadImageAsPng(url)?.let { bytes -> runCatching { bytes.decodeToImageBitmap() }.getOrNull() }")
p.write_text(s, encoding="utf-8", newline="")
print("ImportFromPlatformDialog cleaned")

# ============ ContactDetailDialogs: Bitmap 残留类型 ============
p = S / "pages/person/contact/detail/ContactDetailDialogs.kt"
s = p.read_text(encoding="utf-8")
s = s.replace("onCropConfirm: (Bitmap) -> Unit", "onCropConfirm: (ByteArray) -> Unit")
s = s.replace("Bitmap?", "PlatformImage?")
p.write_text(s, encoding="utf-8", newline="")
print("ContactDetailDialogs types fixed")

# ============ ContactDetailDialogHost: headers 可空 ============
p = S / "pages/person/contact/detail/ContactDetailDialogHost.kt"
s = p.read_text(encoding="utf-8")
s = re.sub(r"downloadAndSaveAvatar\(([^,]+),\s*(\w+),\s*headers\)", r"downloadAndSaveAvatar(\1, \2, headers ?: emptyMap())", s)
s = s.replace("Bitmap?", "ImageBitmap?")
p.write_text(s, encoding="utf-8", newline="")
print("ContactDetailDialogHost headers fixed")

# ============ CreateContactViewModel/Page: 下载落盘边界 ============
p = S / "pages/person/contact/CreateContactViewModel.kt"
s = p.read_text(encoding="utf-8")
s = s.replace("import top.mcxiafeng.badger.utils.HttpUtil\n", "")
# 72-74: HttpUtil.downloadBitmap + Methods.saveBitmapAsAvatar
s = s.replace("""                val bmp = HttpUtil.downloadBitmap(url)
                if (bmp != null) {
                    avatarPath = Methods.saveBitmapAsAvatar(context, bmp, "contact_avatar_${now}.webp").absolutePath
                }""", """                val saved = downloadAndStoreAvatar(url, "contact_avatar_${now}.webp")
                if (saved != null) {
                    avatarPath = saved
                }""")
p.write_text(s, encoding="utf-8", newline="")
print("CreateContactViewModel rewritten flow")

p = S / "pages/person/contact/CreateContactPage.kt"
s = p.read_text(encoding="utf-8")
s = s.replace("import top.mcxiafeng.badger.utils.HttpUtil\n",
              "import top.mcxiafeng.badger.platform.downloadAndStoreAvatar\n")
s = s.replace("import top.mcxiafeng.badger.utils.HttpUtil\n", "")
p.write_text(s, encoding="utf-8", newline="")
print("CreateContactPage import fixed")

# ============ CollectionDetailPage: deleteFileQuietly ============
p = S / "pages/card/CollectionDetailPage.kt"
s = p.read_text(encoding="utf-8")
if "import top.mcxiafeng.badger.shared.util.deleteFileQuietly" not in s:
    s = s.replace("import top.mcxiafeng.badger.shared.util.nowMs\n",
                  "import top.mcxiafeng.badger.shared.util.deleteFileQuietly\nimport top.mcxiafeng.badger.shared.util.nowMs\n")
p.write_text(s, encoding="utf-8", newline="")

# ============ CardComponents: backgroundImageBitmap 未定义 ============
p = S / "pages/card/CardComponents.kt"
s = p.read_text(encoding="utf-8")
print("CardComponents bg refs:", re.findall(r"\w*backgroundImageBitmap\w*", s)[:6])
print("done")
