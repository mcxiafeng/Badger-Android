# -*- coding: utf-8 -*-
"""[KMP K13] 图标换血补丁：Icons.Default.* 别名形态 + 补缺失 Lucide import"""
import re
from pathlib import Path

ROOT = Path(r"F:\Java\Android Project\Badger\app\src\main\kotlin")

MAPPING = {
    "Add": "Plus", "ArrowBack": "ArrowLeft", "ArrowDownward": "ArrowDown", "ArrowUpward": "ArrowUp",
    "AutoAwesome": "Sparkles", "BatteryAlert": "BatteryWarning", "BatteryFull": "BatteryFull",
    "Cake": "Cake", "Check": "Check", "CheckCircle": "CircleCheck", "ChevronRight": "ChevronRight",
    "Close": "X", "CloudSync": "RefreshCw", "ColorLens": "Palette", "ContentCopy": "Copy",
    "Dashboard": "LayoutDashboard", "Delete": "Trash2", "Devices": "MonitorSmartphone",
    "Edit": "Pencil", "Flag": "Flag", "FlashOff": "ZapOff", "FlashOn": "Zap", "Folder": "Folder",
    "History": "History", "Info": "Info", "Inventory": "Package", "Link": "Link",
    "LocationOn": "MapPin", "LockReset": "KeyRound", "MoreVert": "EllipsisVertical", "Nfc": "Nfc",
    "Palette": "Palette", "Person": "User", "PersonAdd": "UserPlus", "QrCodeScanner": "ScanLine",
    "RadioButtonUnchecked": "Circle", "Refresh": "RefreshCw", "Search": "Search",
    "Settings": "Settings", "Share": "Share2", "Star": "Star", "Transgender": "Transgender",
    "Tune": "SlidersHorizontal", "Visibility": "Eye", "VisibilityOff": "EyeOff",
    "Warning": "TriangleAlert", "CameraAlt": "Camera", "CheckCircleOutline": "CircleCheck",
    "Cloud": "Cloud", "Group": "Users", "Notifications": "Bell", "PersonOutline": "User",
    "PhotoLibrary": "Images", "TextFields": "Type",
}

def fix(path):
    src = path.read_text(encoding="utf-8")
    orig = src
    # Icons.Default.X -> Lucide.Y
    def repl(m):
        return "Lucide." + MAPPING.get(m.group(1), m.group(1))
    src = re.sub(r"Icons\.Default\.(\w+)", repl, src)
    if src == orig:
        return False
    # collect used lucide names
    used = set(re.findall(r"\bLucide\.(\w+)", src))
    lines = src.split("\n")
    last_import = max(i for i, l in enumerate(lines) if l.startswith("import "))
    ins = []
    if any(l.strip().startswith("Lucide") or re.search(r"\bLucide\.", l) for l in lines):
        pass
    # ensure object import
    if not any(l == "import com.composables.icons.lucide.Lucide" for l in lines):
        ins.append("import com.composables.icons.lucide.Lucide")
    for n in sorted(used):
        imp = f"import com.composables.icons.lucide.{n}"
        if not any(l == imp for l in lines):
            ins.append(imp)
    if ins:
        lines[last_import + 1:last_import + 1] = ins
    path.write_text("\n".join(lines), encoding="utf-8", newline="")
    return True

changed = 0
for p in ROOT.rglob("*.kt"):
    if fix(p):
        changed += 1
        print("patched:", p.relative_to(ROOT))
print("total:", changed)
