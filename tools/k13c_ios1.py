# -*- coding: utf-8 -*-
"""[KMP K13c] iOS 清扫第一批：LocalContext 死变量 / decorFitsSystemWindows / 杂项单点"""
from pathlib import Path
import re

S = Path(r"F:\Java\Android Project\Badger\shared\src\commonMain\kotlin\top\mcxiafeng\badger")

def edit(rel, *transforms, must=False):
    p = S / rel
    if not p.exists():
        print("MISSING:", rel); return
    s = p.read_text(encoding="utf-8")
    orig = s
    for t in transforms:
        s = t(s)
    if s != orig:
        p.write_text(s, encoding="utf-8", newline="")
        print("fixed:", rel)
    elif must:
        print("NO-OP!:", rel)

# 1) LocalContext 死变量（import + val 声明两行整体删）
FILES_LOCALCONTEXT = [
    "pages/settings/AboutPage.kt", "pages/settings/ContactUsPage.kt",
    "pages/settings/NfcSettingsPage.kt", "pages/settings/UiSettingsPage.kt",
    "pages/settings/PlatformListPage.kt", "pages/settings/OpenSourceLicensePage.kt",
    "pages/settings/account/AccountProfilePage.kt", "pages/setupguide/SetupGuidePage.kt",
    "pages/setupguide/SetupStepPlatforms.kt", "pages/scanner/ScannerComponents.kt",
    "pages/person/contact/dialogs/PlatformDetailDialog.kt", "pages/person/contact/dialogs/ImportFromPlatformDialog.kt",
    "pages/person/contact/dialogs/FieldDetailDialog.kt", "pages/person/contact/dialogs/AddPlatformDialog.kt",
    "pages/person/contact/dialogs/AddContactFieldDialog.kt", "pages/person/contact/detail/ContactDetailDialogHost.kt",
    "pages/social/QrCodeCard.kt", "pages/card/CardPage.kt",
    "ui/components/FirstTimeHint.kt", "pages/settings/settings_dir_dummy.kt",
]
def strip_localcontext(s):
    s = s.replace("import androidx.compose.ui.platform.LocalContext\n", "")
    s = re.sub(r"[ \t]*val context = LocalContext\.current\n", "", s)
    return s
for rel in FILES_LOCALCONTEXT:
    edit(rel, strip_localcontext)

# 2) DialogProperties.decorFitsSystemWindows（CMP 无此参数）
def strip_decor(s):
    s = re.sub(r",?\s*\n\s*decorFitsSystemWindows = false,?", "", s)
    s = re.sub(r"decorFitsSystemWindows = false,\s*", "", s)
    return s
for rel in ["pages/social/SocialPage.kt", "pages/setupguide/SetupStepProfile.kt",
            "pages/person/contact/UserProfileDetailPage.kt", "pages/person/contact/detail/ContactDetailPage.kt",
            "pages/person/contact/detail/ContactDetailDialogs.kt", "pages/card/CardPage.kt",
            "pages/card/CollectionDetailPage.kt", "pages/person/contact/CreateContactPage.kt"]:
    edit(rel, strip_decor)

# 3) PlatformInfo.ios：majorVersion Long → Int
p = S / "platform/PlatformInfo.ios.kt"
s = p.read_text(encoding="utf-8")
s = s.replace("get() = NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion }",
              "get() = NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion.toInt() }")
p.write_text(s, encoding="utf-8", newline="")
print("PlatformInfo.ios Int fixed")

# 4) QrCodeCard：AndroidColor → Int 字面量
def qrcard(s):
    s = s.replace("import android.graphics.Color as AndroidColor\n", "")
    # AndroidColor.WHITE / BLACK → 0xFFFFFFFF / 0xFF000000
    s = s.replace("AndroidColor.WHITE", "0xFFFFFFFF").replace("AndroidColor.BLACK", "0xFF000000")
    return s
edit("pages/social/QrCodeCard.kt", qrcard)

# 5) ThemeConfig / LiquidGlassNavBar：android.os.Build → PlatformInfo
def themecfg(s):
    s = re.sub(r"Build\.VERSION\.SDK_INT", "PlatformInfo.apiLevel", s)
    s = re.sub(r"Build\.MODEL\b", "PlatformInfo.deviceModel", s)
    s = re.sub(r"import android\.os\.Build\n", "", s)
    if "import top.mcxiafeng.badger.platform.PlatformInfo" not in s and "PlatformInfo." in s:
        s = s.replace("import androidx.compose.runtime.Composable\n",
                      "import androidx.compose.runtime.Composable\nimport top.mcxiafeng.badger.platform.PlatformInfo\n", 1)
    return s
edit("ui/navigation/ThemeConfig.kt", themecfg)
edit("ui/LiquidGlassNavBar.kt", themecfg)

print("batch 1 done")
