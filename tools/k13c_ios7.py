# -*- coding: utf-8 -*-
"""[KMP K13c] iOS 清扫第七批：ViewModel Context 收口、asImageBitmap 残留等"""
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

# 通用：LocalContext 死变量 + asImageBitmap 残留
def generic_ctx(s):
    s = s.replace("import androidx.compose.ui.platform.LocalContext\n", "")
    s = re.sub(r"[ \t]*val context = LocalContext\.current\n", "", s)
    s = re.sub(r"^import android\..*\n", "", s, flags=re.M)
    s = s.replace(".asImageBitmap()", "")
    return s
for rel in [
    "pages/person/contact/UserProfileDetailPage.kt",
    "pages/person/contact/UserProfileDetailComponents.kt",
    "pages/person/contact/CreateContactPage.kt",
    "pages/person/contact/CreateContactViewModel.kt",
    "pages/person/PersonViewModel.kt",
    "pages/person/contact/dialogs/FieldDetailDialog.kt",
    "pages/person/contact/dialogs/PlatformDetailDialog.kt",
    "pages/settings/account/AccountSettingsDialogs.kt",
    "pages/settings/UiSettingsPage.kt",
    "pages/settings/AboutPage.kt",
    "pages/person/contact/dialogs/ImportFromPlatformDialog.kt",
]:
    edit(rel, generic_ctx)

# 1) PersonViewModel 65：Context Koin 字段 + 241-249 contentResolver（QAuxv 导入）
def person_vm(s):
    s = re.sub(r"    private val context: Context = [^\n]*\n", "", s)
    s = s.replace("import android.net.Uri\n", "")
    return s
edit("pages/person/PersonViewModel.kt", person_vm, must=True)

# 2) PlatformDetailDialog 55-67 / FieldDetailDialog 56：context(...) 调用点（看内容）
p = S / "pages/person/contact/dialogs/PlatformDetailDialog.kt"
s = p.read_text(encoding="utf-8")
for i, l in enumerate(s.split("\n")):
    if i in (54, 59, 62, 66, 96):
        print(f"PDD {i+1}:", l.strip()[:100])

# 3) FieldDetailDialog 56
p = S / "pages/person/contact/dialogs/FieldDetailDialog.kt"
s = p.read_text(encoding="utf-8")
print("FDD 56:", s.split("\n")[55].strip()[:100])

# 4) AttachFieldDialog 258-260
p = S / "pages/person/contact/dialogs/AttachFieldDialog.kt"
s = p.read_text(encoding="utf-8")
print("AFD 258-260:", [s.split("\n")[i].strip()[:90] for i in (257, 258, 259)])

# 5) TagManagerSuccessBody 54
p = S / "pages/settings/tags/TagManagerSuccessBody.kt"
s = p.read_text(encoding="utf-8")
print("TMSB 54:", s.split("\n")[53].strip()[:110])

# 6) SyncStatusPage 279/295/303
p = S / "pages/settings/sync/SyncStatusPage.kt"
s = p.read_text(encoding="utf-8")
print("SSP:", [s.split("\n")[i].strip()[:80] for i in (278, 294, 302)])

# 7) UiSettingsPage 168
p = S / "pages/settings/UiSettingsPage.kt"
s = p.read_text(encoding="utf-8")
print("USP 168:", s.split("\n")[167].strip()[:100])

# 8) ContactDetailComponents 222
p = S / "pages/person/contact/detail/ContactDetailComponents.kt"
s = p.read_text(encoding="utf-8")
print("CDC 222:", s.split("\n")[221].strip()[:100])
print("done")
