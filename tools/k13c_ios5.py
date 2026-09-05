# -*- coding: utf-8 -*-
"""[KMP K13c] iOS 清扫第五批：LogViewer zip→CacheFiles、saveAdvanced、Build 残留等"""
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

# 1) NfcSettingsPage：saveAdvanced → saveAdvancedSettings
def nfc(s):
    s = re.sub(r"\bsaveAdvanced\(", "ShortLinkService.saveAdvancedSettings(", s)
    s = re.sub(r"ShortLinkService\.ShortLinkService\.", "ShortLinkService.", s)
    return s
edit("pages/settings/NfcSettingsPage.kt", nfc, must=True)

# 2) SyncStatusPage：Build 残留
def syncpage(s):
    s = re.sub(r"Build\.VERSION\.SDK_INT", "PlatformInfo.apiLevel", s)
    s = re.sub(r"import android\.os\.Build\n", "", s)
    if "PlatformInfo." in s and "import top.mcxiafeng.badger.platform.PlatformInfo" not in s:
        s = s.replace("import top.mcxiafeng.badger.platform.BatteryOptimization\n",
                      "import top.mcxiafeng.badger.platform.BatteryOptimization\nimport top.mcxiafeng.badger.platform.PlatformInfo\n")
    return s
edit("pages/settings/sync/SyncStatusPage.kt", syncpage, must=True)

# 3) TagManagerSuccessBody：SimpleDateFormat 残留 + formatEpochDate import
def tagsb(s):
    s = re.sub(r"[ \t]*private val \w+Format = SimpleDateFormat\([^)]*\)\n", "", s)
    s = re.sub(r"SimpleDateFormat\([^)]*\)\.format\(([^)]+)\)", r"formatEpochDate(\1.toLong())", s)
    s = s.replace("import java.text.SimpleDateFormat\n", "")
    s = s.replace("import java.util.Locale\n", "")
    if "formatEpochDate(" in s and "import top.mcxiafeng.badger.utils.formatEpochDate" not in s:
        lines = s.split("\n")
        idxs = [i for i, l in enumerate(lines) if l.startswith("import ")]
        lines.insert(idxs[-1]+1, "import top.mcxiafeng.badger.utils.formatEpochDate")
        s = "\n".join(lines)
    return s
edit("pages/settings/tags/TagManagerSuccessBody.kt", tagsb, must=True)

# 4) SetupGuidePage：setSetupGuideCompleted(context) → 无参
def sgpage(s):
    s = re.sub(r"setSetupGuideCompleted\(\s*[^)]*?\)", "setSetupGuideCompleted()", s)
    return s
edit("pages/setupguide/SetupGuidePage.kt", sgpage, must=True)

# 5) SetupGuideViewModel 95：HttpResult 残 receiver（诊断）
p = S / "pages/setupguide/SetupGuideViewModel.kt"
s = p.read_text(encoding="utf-8")
print("line95:", s.split("\n")[94])

# 6) SocialPage 3: android import
p = S / "pages/social/SocialPage.kt"
s = p.read_text(encoding="utf-8")
print("SocialPage head:", [s.split("\n")[i] for i in range(0, 6)])

# 7) ThemeConfig 54
p = S / "ui/navigation/ThemeConfig.kt"
s = p.read_text(encoding="utf-8")
print("ThemeConfig 48/54:", [s.split("\n")[i] for i in (47, 53)])

# 8) LiquidGlassNavBar 3/300
p = S / "ui/LiquidGlassNavBar.kt"
s = p.read_text(encoding="utf-8")
print("LGNB 3/300:", [s.split("\n")[i] for i in (2, 299)])

# 9) AvatarComponents File
p = S / "ui/components/AvatarComponents.kt"
s = p.read_text(encoding="utf-8")
print("Avatar 20/69:", [s.split("\n")[i] for i in (19, 68)])
