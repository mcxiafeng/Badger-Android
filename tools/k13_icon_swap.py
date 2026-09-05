# -*- coding: utf-8 -*-
"""
[KMP K13] 图标体系换血脚本：material-icons-extended → Lucide (com.composables:icons-lucide-cmp)
映射表 = docs/icon-selection.md §3「70 种 Material 图标 → Lucide 等义图标」的落地版。
用法: python tools/k13_icon_swap.py
"""
import re
from pathlib import Path

# 一次性迁移脚本：仅处理本仓库 app 主源集
ROOT = Path(r"F:\Java\Android Project\Badger\app\src\main\kotlin")

# usage token (Icons.Filled.X) -> lucide usage name
MAPPING = {
    "Icons.Filled.Add": "Plus",
    "Icons.Filled.ArrowBack": "ArrowLeft",
    "Icons.Filled.ArrowDownward": "ArrowDown",
    "Icons.Filled.ArrowUpward": "ArrowUp",
    "Icons.Filled.AutoAwesome": "Sparkles",
    "Icons.Filled.BatteryAlert": "BatteryWarning",
    "Icons.Filled.BatteryFull": "BatteryFull",
    "Icons.Filled.Cake": "Cake",
    "Icons.Filled.Check": "Check",
    "Icons.Filled.CheckCircle": "CircleCheck",
    "Icons.Filled.ChevronRight": "ChevronRight",
    "Icons.Filled.Close": "X",
    "Icons.Filled.CloudSync": "RefreshCw",
    "Icons.Filled.ColorLens": "Palette",
    "Icons.Filled.ContentCopy": "Copy",
    "Icons.Filled.Dashboard": "LayoutDashboard",
    "Icons.Filled.Delete": "Trash2",
    "Icons.Filled.Devices": "MonitorSmartphone",
    "Icons.Filled.Edit": "Pencil",
    "Icons.Filled.Flag": "Flag",
    "Icons.Filled.FlashOff": "ZapOff",
    "Icons.Filled.FlashOn": "Zap",
    "Icons.Filled.Folder": "Folder",
    "Icons.Filled.History": "History",
    "Icons.Filled.Info": "Info",
    "Icons.Filled.Inventory": "Package",
    "Icons.Filled.Link": "Link",
    "Icons.Filled.LocationOn": "MapPin",
    "Icons.Filled.LockReset": "KeyRound",
    "Icons.Filled.MoreVert": "EllipsisVertical",
    "Icons.Filled.Nfc": "Nfc",
    "Icons.Filled.Palette": "Palette",
    "Icons.Filled.Person": "User",
    "Icons.Filled.PersonAdd": "UserPlus",
    "Icons.Filled.QrCodeScanner": "ScanLine",
    "Icons.Filled.RadioButtonUnchecked": "Circle",
    "Icons.Filled.Refresh": "RefreshCw",
    "Icons.Filled.Search": "Search",
    "Icons.Filled.Settings": "Settings",
    "Icons.Filled.Share": "Share2",
    "Icons.Filled.Star": "Star",
    "Icons.Filled.Transgender": "Transgender",
    "Icons.Filled.Tune": "SlidersHorizontal",
    "Icons.Filled.Visibility": "Eye",
    "Icons.Filled.VisibilityOff": "EyeOff",
    "Icons.Filled.Warning": "TriangleAlert",
    "Icons.Outlined.CameraAlt": "Camera",
    "Icons.Outlined.CheckCircleOutline": "CircleCheck",
    "Icons.Outlined.Close": "X",
    "Icons.Outlined.Cloud": "Cloud",
    "Icons.Outlined.Dashboard": "LayoutDashboard",
    "Icons.Outlined.Devices": "MonitorSmartphone",
    "Icons.Outlined.Edit": "Pencil",
    "Icons.Outlined.Folder": "Folder",
    "Icons.Outlined.Group": "Users",
    "Icons.Outlined.Link": "Link",
    "Icons.Outlined.Notifications": "Bell",
    "Icons.Outlined.Palette": "Palette",
    "Icons.Outlined.PersonOutline": "User",
    "Icons.Outlined.PhotoLibrary": "Images",
    "Icons.Outlined.QrCodeScanner": "ScanLine",
    "Icons.Outlined.TextFields": "Type",
    "Icons.Outlined.Tune": "SlidersHorizontal",
    "Icons.Outlined.Visibility": "Eye",
    "Icons.Outlined.VisibilityOff": "EyeOff",
    "Icons.AutoMirrored.Filled.ArrowBack": "ArrowLeft",
    "Icons.AutoMirrored.Filled.KeyboardArrowRight": "ChevronRight",
    "Icons.AutoMirrored.Filled.Label": "Tag",
    "Icons.AutoMirrored.Filled.OpenInNew": "ExternalLink",
    "Icons.AutoMirrored.Filled.Sort": "ArrowUpDown",
}

IMPORT_RE = re.compile(r"^import androidx\.compose\.material\.icons\.(\S+)\s*$", re.M)

def lucide_imports_for(imports):
    """map material icon import paths -> set of lucide names to import"""
    out = set()
    for imp in imports:
        parts = imp.split(".")
        if parts[0] == "automirrored":
            token = "Icons.AutoMirrored.Filled." + parts[-1]
        elif parts[0] == "filled":
            token = "Icons.Filled." + parts[-1]
        elif parts[0] == "outlined":
            token = "Icons.Outlined." + parts[-1]
        else:
            continue  # Icons object import handled separately
        if token in MAPPING:
            out.add(MAPPING[token])
        else:
            print("  !! UNMAPPED import:", imp, "token:", token)
    return out

def process(path):
    src = path.read_text(encoding="utf-8")
    orig = src
    imports = IMPORT_RE.findall(src)
    has_icons_obj = "import androidx.compose.material.icons.Icons" in src
    if not imports and not has_icons_obj:
        return False
    lucide_names = lucide_imports_for(imports)

    # replace usage tokens, longest first (AutoMirrored before Filled)
    for token in sorted(MAPPING.keys(), key=len, reverse=True):
        src = src.replace(token, "Lucide." + MAPPING[token])

    # drop material icon import lines (incl. the Icons object import)
    src = re.sub(r"^import androidx\.compose\.material\.icons\.\S+\s*\r?\n", "", src, flags=re.M)

    # insert lucide imports after the last import line
    lines = src.split("\n")
    last_import = max(i for i, l in enumerate(lines) if l.startswith("import "))
    ins = ["import com.composables.icons.lucide.Lucide"] if has_icons_obj else []
    ins += sorted("import com.composables.icons.lucide." + n for n in lucide_names)
    existing = set(l.strip() for l in lines)
    ins = [i for i in ins if i not in existing]
    lines[last_import + 1:last_import + 1] = ins
    src = "\n".join(lines)

    if src != orig:
        path.write_text(src, encoding="utf-8", newline="")
        return True
    return False

def main():
    changed = 0
    for p in ROOT.rglob("*.kt"):
        if process(p):
            changed += 1
            print("swapped:", p.relative_to(ROOT))
    print("total changed:", changed)

if __name__ == "__main__":
    main()
