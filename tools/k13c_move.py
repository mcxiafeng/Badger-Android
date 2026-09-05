# -*- coding: utf-8 -*-
"""
[KMP K13c/K15] UI 层大迁移脚本：app → shared commonMain（pages/ + ui/ + 根骨架）。

机械变换（全部文件）：
M1 android.util.Log → BadgerLog
M2 Toast.makeText(ctx, msg, LENGTH_X).show() → showToast(msg)（单行形态；多行留待编译器报出）
M3 androidx.activity.compose.BackHandler → platform.BackHandler（同签名）
M4 Dispatchers.IO → BadgerDispatchers.io
M5 System.currentTimeMillis() → nowMs()
M6 X.javaClass.simpleName → X::class.simpleName（可空处由编译器报出后手工补 ?: ）
M7 org.koin.androidx.compose.koinViewModel → org.koin.compose.viewmodel.koinViewModel

不移动（手工处理）：PlatformIcon / ImageCropDialog / CollectionTheme / GpuCompat / Methods /
MainActivity / BadgerApplication / KoinModules / NetworkModule / AppDatabaseHost / AvatarStorage /
AvatarFetcher / ColorExtractor / LegacyTagFixup(已迁) 
"""
import re
from pathlib import Path

ROOT = Path(r"F:\Java\Android Project\Badger")
S = ROOT / "app" / "src" / "main" / "kotlin" / "top" / "mcxiafeng" / "badger"
CM = ROOT / "shared" / "src" / "commonMain" / "kotlin" / "top" / "mcxiafeng" / "badger"

BADGERLOG = "import top.mcxiafeng.badger.utils.BadgerLog"
NOWMS = "import top.mcxiafeng.badger.shared.util.nowMs"
DISPATCHERS = "import top.mcxiafeng.badger.shared.util.BadgerDispatchers"

def ensure_import(src, imp):
    if imp in src:
        return src
    lines = src.split("\n")
    idxs = [i for i, l in enumerate(lines) if l.startswith("import ")]
    pos = (idxs[-1] + 1) if idxs else 0
    lines.insert(pos, imp)
    return "\n".join(lines)

def m_log(src):
    if "import android.util.Log" not in src:
        return src
    src = re.sub(r"^import android\.util\.Log\r?\n", "", src, flags=re.M)
    src = re.sub(r"\bLog\.(d|i|w|e)\(", r"BadgerLog.\1(", src)
    return ensure_import(src, BADGERLOG)

def m_toast(src):
    if "android.widget.Toast" not in src:
        return src
    src = re.sub(r"^import android\.widget\.Toast\r?\n", "", src, flags=re.M)
    # 单行形态：Toast.makeText(ctx, msg, Toast.LENGTH_X).show()
    src = re.sub(
        r"Toast\.makeText\(\s*[\w.()]+,\s*(.+?),\s*Toast\.LENGTH_(?:SHORT|LONG)\s*\)\s*\.show\(\)",
        r"showToast(\1)",
        src,
    )
    # withContext(Dispatchers.Main) 包裹的纯 toast 段保留（无副作用），调用方签名不变
    return ensure_import(src, "import top.mcxiafeng.badger.platform.showToast")

def m_backhandler(src):
    if "androidx.activity.compose.BackHandler" not in src:
        return src
    src = re.sub(r"^import androidx\.activity\.compose\.BackHandler\r?\n", "", src, flags=re.M)
    return ensure_import(src, "import top.mcxiafeng.badger.platform.BackHandler")

def m_dispatchers(src):
    if "Dispatchers.IO" not in src:
        return src
    src = src.replace("Dispatchers.IO", "BadgerDispatchers.io")
    return ensure_import(src, DISPATCHERS)

def m_nowms(src):
    if "System.currentTimeMillis()" not in src:
        return src
    src = src.replace("System.currentTimeMillis()", "nowMs()")
    return ensure_import(src, NOWMS)

def m_javaclass(src):
    return re.sub(r"(\w+)\.javaClass\.simpleName", r"\1::class.simpleName", src)

def m_koinvm(src):
    return src.replace(
        "import org.koin.androidx.compose.koinViewModel",
        "import org.koin.compose.viewmodel.koinViewModel",
    )

TRANSFORMS = [m_log, m_toast, m_backhandler, m_dispatchers, m_nowms, m_javaclass, m_koinvm]

# 手工处理清单（脚本跳过并打印）
MANUAL = {
    "App.kt",
    "AppRoutes.kt",
    "MainActivity.kt",
    "BadgerApplication.kt",
    "di/KoinModules.kt",
    "di/NetworkModule.kt",
    "data/AppDatabaseHost.kt",
    "utils/ColorExtractor.kt",
    "utils/MiuixShape.kt",
    "utils/NetworkConstants.kt",
}

UI_REL = [
    "App.kt", "AppMainTabs.kt", "AppRoutes.kt", "AppViewModel.kt", "AppTheme.kt",
    "ui/LiquidGlassNavBar.kt", "ui/UnreadBadge.kt",
    "ui/blur/BlurHelper.kt", "ui/blur/Lens.kt", "ui/blur/SphereSurface.kt",
    "ui/blur/animation/DampedDragAnimation.kt", "ui/blur/animation/InteractiveHighlight.kt",
    "ui/blur/animation/LiquidWobble.kt",
    "ui/components/AvatarComponents.kt", "ui/components/BadgerDialog.kt",
    "ui/components/BadgerEmptyState.kt", "ui/components/DialogComponents.kt",
    "ui/components/FirstTimeHint.kt", "ui/components/TagDialogs.kt",
    "ui/designsystem/BadgerDesignTokens.kt", "ui/designsystem/BadgerSemanticColors.kt",
    "ui/navigation/AppNavigator.kt", "ui/navigation/NavBarConfig.kt",
    "ui/navigation/NavTransitionEasing.kt", "ui/navigation/NavTransitions.kt",
    "ui/navigation/Route.kt", "ui/navigation/ThemeConfig.kt",
]

def collect_page_rels():
    pages = S / "pages"
    out = []
    for p in sorted(pages.rglob("*.kt")):
        rel = p.relative_to(S).as_posix()
        out.append(rel)
    return out

def transform(src):
    for t in TRANSFORMS:
        src = t(src)
    return src

def warn_platform(rel, src):
    probs = []
    for i, l in enumerate(src.split("\n")):
        if re.match(r"^import (android\.|androidx\.activity|androidx\.compose\.material\.icons)", l):
            probs.append(f"  {rel}:{i+1}: {l.strip()}")
        elif "Toast.makeText" in l or "LocalContext" in l or "LocalConfiguration" in l:
            probs.append(f"  {rel}:{i+1} [manual]: {l.strip()}")
    return probs

def main():
    rels = UI_REL + collect_page_rels()
    problems = []
    moved = skipped = 0
    for rel in rels:
        if rel in MANUAL:
            print("SKIP(manual):", rel)
            skipped += 1
            continue
        src_path = S / rel
        if not src_path.exists():
            print("MISSING:", rel)
            continue
        src = src_path.read_text(encoding="utf-8")
        src = transform(src)
        probs = warn_platform(rel, src)
        problems += probs
        dst = CM / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        dst.write_text(src, encoding="utf-8", newline="")
        src_path.unlink()
        moved += 1
    print(f"\nmoved={moved} skipped={skipped}")
    if problems:
        print("\n!! 需手工清理的残留（文件已在 shared，直接编辑）：")
        for p in problems:
            print(p)

if __name__ == "__main__":
    main()
