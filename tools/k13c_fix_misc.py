# -*- coding: utf-8 -*-
"""[KMP K13c] settings/setupguide/social/scanner 对话框残点批量修复"""
from pathlib import Path

S = Path(r"F:\Java\Android Project\Badger\shared\src\commonMain\kotlin\top\mcxiafeng\badger")

def edit(rel, *transforms):
    p = S / rel
    s = p.read_text(encoding="utf-8")
    orig = s
    for t in transforms:
        s = t(s)
    p.write_text(s, encoding="utf-8", newline="")
    print(("fixed " if s != orig else "NO-OP ") + rel)

# ============ AboutPage ============
def about(s):
    # R.mipmap.ic_launcher → AppIcon 边界（新 expect）
    s = s.replace("import top.mcxiafeng.badger.R\n", "import top.mcxiafeng.badger.platform.AppIcon\n")
    s = s.replace("import android.net.Uri\n", "")
    s = s.replace("Image(painter = painterResource(R.mipmap.ic_launcher), contentDescription = \"Badger\", modifier = Modifier.size(48.dp).clip(CircleShape))",
                  "AppIcon(modifier = Modifier.size(48.dp).clip(CircleShape))")
    return s
edit("pages/settings/AboutPage.kt", about)

# ============ UiSettingsPage / SetupStepNavBarEffect：GpuCompat 去参 + koinViewModel ============
def uisettings(s):
    s = s.replace("GpuCompat.isAdvancedBlurSupported(context)", "GpuCompat.isAdvancedBlurSupported()")
    return s
edit("pages/settings/UiSettingsPage.kt", uisettings)

def navbar_effect(s):
    s = s.replace("import androidx.compose.ui.platform.LocalContext\n", "")
    s = s.replace("    val context = LocalContext.current\n", "")
    s = s.replace("viewModel: SetupGuideViewModel = org.koin.androidx.compose.koinViewModel(),",
                  "viewModel: SetupGuideViewModel = org.koin.compose.viewmodel.koinViewModel(),")
    s = s.replace("GpuCompat.isAdvancedBlurSupported(context)", "GpuCompat.isAdvancedBlurSupported()")
    return s
edit("pages/setupguide/SetupStepNavBarEffect.kt", navbar_effect)

# ============ QrCodeCard：generateQRCode → QrCodeGenerator ============
def qrcard(s):
    s = s.replace("import top.mcxiafeng.badger.utils.Methods\n",
                  "import top.mcxiafeng.badger.platform.ImageCodec\nimport top.mcxiafeng.badger.platform.PlatformImage\nimport top.mcxiafeng.badger.platform.QrCodeGenerator\nimport top.mcxiafeng.badger.utils.Methods\n")
    # 原: val qrBitmap = remember(...) { Methods.generateQRCode(content, 512, androidFgColor, qrBackgroundColor) }
    # 改为 bytes → ImageBitmap（渲染侧一致）
    s = s.replace("import androidx.compose.ui.graphics.asImageBitmap\n",
                  "import androidx.compose.ui.graphics.ImageBitmap\nimport androidx.compose.ui.graphics.decodeToImageBitmap\n")
    import re
    m = re.search(r"val qrBitmap = remember\(([^)]*)\) \{ Methods\.generateQRCode\(([^)]*)\) \}", s)
    if m:
        keys, args = m.group(1), m.group(2)
        s = s.replace(m.group(0),
                      f"val qrImageBitmap = remember({keys}) {{\n        QrCodeGenerator.generate({args})?.let {{ img ->\n            try {{ ImageCodec.encodePng(img)?.let {{ decodeToImageBitmap(it) }} }} finally {{ img.close() }}\n        }}\n    }}")
    # 渲染侧 qrBitmap → qrImageBitmap（asImageBitmap 调用一并替换）
    s = s.replace("qrBitmap.asImageBitmap()", "qrImageBitmap")
    s = re.sub(r"\bqrBitmap\b(?!ImageBitmap)", "qrImageBitmap", s)
    return s
edit("pages/social/QrCodeCard.kt", qrcard)

# ============ PlatformDetailDialog / ContactDetailPage 残余 copyToClipboard(context, label, value) ============
def clipboard3(s):
    import re
    s = re.sub(r"Methods\.copyToClipboard\(context,\s*([^,]+),\s*([^)]+)\)",
               r"Methods.copyToClipboard(\1, \2)", s)
    return s
edit("pages/person/contact/dialogs/PlatformDetailDialog.kt", clipboard3)
edit("pages/person/contact/UserProfileDetailComponents.kt", clipboard3)
edit("pages/person/PersonPage.kt", clipboard3)
edit("pages/person/PersonListComponents.kt", clipboard3)
edit("pages/card/CardComponents.kt", clipboard3)
edit("pages/card/CollectionDetailList.kt", clipboard3)
edit("pages/card/CollectionDetailHero.kt", clipboard3)
edit("pages/card/CollectionDetailPage.kt", clipboard3)
edit("pages/card/CollectionDetailDialogs.kt", clipboard3)
edit("pages/scanner/ScannerDialogs.kt", clipboard3)
edit("pages/scanner/ScannerSubDialogs.kt", clipboard3)
edit("pages/settings/ContactUsPage.kt", clipboard3)
edit("pages/settings/OpenSourceLicensePage.kt", clipboard3)
edit("pages/dashboard/DashboardPage.kt", clipboard3)
edit("pages/social/SocialPage.kt", clipboard3)
edit("pages/social/SocialPageComponents.kt", clipboard3)
edit("pages/settings/sync/ServerShortLinkPage.kt", clipboard3)
edit("pages/settings/PlatformListPage.kt", clipboard3)
edit("pages/settings/account/AccountProfilePage.kt", clipboard3)
edit("pages/settings/notification/NotificationPage.kt", clipboard3)
edit("pages/setupguide/SetupGuidePage.kt", clipboard3)
edit("pages/setupguide/SetupStepServerUrl.kt", clipboard3)
edit("pages/setupguide/SetupStepPlatforms.kt", clipboard3)
edit("pages/setupguide/SetupStepAccount.kt", clipboard3)
edit("pages/setupguide/SetupStepProfile.kt", clipboard3)
edit("pages/person/contact/UserProfileDetailPage.kt", clipboard3)
edit("pages/person/contact/CreateContactPage.kt", clipboard3)
edit("pages/person/contact/detail/ContactDetailPage.kt", clipboard3)
edit("pages/person/contact/detail/ContactDetailDialogs.kt", clipboard3)
edit("pages/person/contact/detail/ContactDetailDialogHost.kt", clipboard3)
edit("pages/person/contact/detail/ContactFieldComponents.kt", clipboard3)
edit("pages/person/PersonQAuxvImportDialog.kt", clipboard3)
edit("pages/auth/AuthScreens.kt", clipboard3)
edit("pages/auth/AuthForgotContent.kt", clipboard3)
edit("pages/settings/SettingsComponents.kt", clipboard3)
edit("pages/settings/NfcSettingsPage.kt", clipboard3)
edit("ui/components/TagDialogs.kt", clipboard3)
edit("ui/components/DialogComponents.kt", clipboard3)
edit("ui/components/BadgerDialog.kt", clipboard3)
edit("AppRoutes.kt", clipboard3)

print("done")
