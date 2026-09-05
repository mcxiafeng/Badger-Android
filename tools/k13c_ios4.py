# -*- coding: utf-8 -*-
"""[KMP K13c] iOS 清扫第四批：ViewModel Context 字段、电池优化、File 残留、Uri 桥"""
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

# 1) SyncStatusViewModel：电池优化走边界
def sync_vm(s):
    for imp in ["import android.content.Context\n", "import android.os.Build\n", "import android.os.PowerManager\n"]:
        s = s.replace(imp, "")
    s = s.replace("    private val context: Context = top.mcxiafeng.badger.di.KoinComponentBy.get()\n", "")
    if "import top.mcxiafeng.badger.platform.BatteryOptimization" not in s:
        s = s.replace("import top.mcxiafeng.badger.utils.BadgerLog\n",
                      "import top.mcxiafeng.badger.platform.BatteryOptimization\nimport top.mcxiafeng.badger.utils.BadgerLog\n")
    # 替换读电池优化的块
    s = re.sub(
        r"""(?s)        if \(Build\.VERSION\.SDK_INT < Build\.VERSION_CODES\.M\) \{.*?pm\?\.isIgnoringBatteryOptimizations\(context\.packageName\) \?: false\n?(\s*\} catch.*)""",
        r"""        BatteryOptimization.isIgnoring()\n\1""",
        s)
    return s
edit("pages/settings/sync/SyncStatusViewModel.kt", sync_vm, must=True)

# 2) SettingsHomeViewModel / AccountSettingsViewModel：删死 context 字段
for rel in ["pages/settings/SettingsHomeViewModel.kt", "pages/settings/account/AccountSettingsViewModel.kt"]:
    def vm(s):
        uses = len(re.findall(r"\bcontext\b", s)) - 1
        print(rel, "context uses:", uses)
        s = s.replace("    private val context: Context = top.mcxiafeng.badger.di.KoinComponentBy.get()\n", "")
        s = re.sub(r"import android\.content\.Context\n", "", s)
        return s
    edit(rel, vm)

# 3) SettingsComponents / SetupGuideModels：ctx 形参删除
def settings_components(s):
    s = re.sub(r"(\w+)\(\s*ctx: android\.content\.Context, ", r"\1(", s)
    s = re.sub(r"fun (\w+)\(ctx: android\.content\.Context\)", r"fun \1()", s)
    return s
edit("pages/settings/SettingsComponents.kt", settings_components)

def guide_models(s):
    s = s.replace("fun setSetupGuideCompleted(@Suppress(\"UNUSED_PARAMETER\") context: android.content.Context) {",
                  "fun setSetupGuideCompleted() {")
    return s
edit("pages/setupguide/SetupGuideModels.kt", guide_models)

# 4) ImportConflictDialog / CardPage：LocalContext 死变量
def strip_ctx(s):
    s = s.replace("import androidx.compose.ui.platform.LocalContext\n", "")
    s = re.sub(r"[ \t]*val context = LocalContext\.current\n", "", s)
    return s
edit("pages/card/ImportConflictDialog.kt", strip_ctx)

print("batch 4 done")
