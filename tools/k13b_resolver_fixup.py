# -*- coding: utf-8 -*-
"""
[KMP K13b] ContactNetworkResolver 静态门面退役后的调用点修复：
`ContactNetworkResolver.xxx(...)` → `KoinComponentBy.get<ContactNetworkResolver>().identify*(...)`
（旧静态 getResultInfo 只是 identify 的透传，type/existing 形参本就被忽略）
"""
import re
from pathlib import Path

ROOT = Path(r"F:\Java\Android Project\Badger\app\src\main\kotlin")
KCB = "import top.mcxiafeng.badger.di.KoinComponentBy"

REPLACEMENTS = [
    # AppRoutes.kt
    ("ContactNetworkResolver.getResultInfo(jumpLink, mutableMapOf())",
     "KoinComponentBy.get<ContactNetworkResolver>().identify(jumpLink)"),
    # CreateContactPage.kt / ImportFromPlatformDialog.kt
    ("ContactNetworkResolver.identify(input)",
     "KoinComponentBy.get<ContactNetworkResolver>().identify(input)"),
    # UserProfileDetailPage.kt / ContactDetailDialogHost.kt (type 形参本就被忽略)
    ("ContactNetworkResolver.getResultInfo(content, mutableMapOf(), type = contactType)",
     "KoinComponentBy.get<ContactNetworkResolver>().identify(content)"),
    ("ContactNetworkResolver.getResultInfo(content, mutableMapOf(), type = ct)",
     "KoinComponentBy.get<ContactNetworkResolver>().identify(content)"),
    # ContactDetailViewModel.kt
    ("ContactNetworkResolver.getResultInfo(link, emptyMap(), contactType)",
     "KoinComponentBy.get<ContactNetworkResolver>().identify(link)"),
    ("ContactNetworkResolver.identifyBatch(urls)",
     "KoinComponentBy.get<ContactNetworkResolver>().identifyBatch(urls)"),
    # SetupGuideViewModel.kt（全限定形态）
    ("top.mcxiafeng.badger.network.ContactNetworkResolver.getResultInfo(\n                    resolveContent, mutableMapOf(), contactType,\n                )",
     "KoinComponentBy.get<ContactNetworkResolver>().identify(resolveContent)"),
    # 文档注释里的旧 API 名
    ("[ContactNetworkResolver.getResultInfo]", "[ContactNetworkResolver.identify]"),
    ("`ContactNetworkResolver.getResultInfo` 内部走 OkHttp 同步调用，",
     "`ContactNetworkResolver.identify` 内部走网络同步调用，"),
]

FILES = [
    "top/mcxiafeng/badger/AppRoutes.kt",
    "top/mcxiafeng/badger/pages/person/contact/CreateContactPage.kt",
    "top/mcxiafeng/badger/pages/person/contact/UserProfileDetailPage.kt",
    "top/mcxiafeng/badger/pages/person/contact/detail/ContactDetailViewModel.kt",
    "top/mcxiafeng/badger/pages/person/contact/detail/ContactDetailDialogHost.kt",
    "top/mcxiafeng/badger/pages/person/contact/dialogs/ImportFromPlatformDialog.kt",
    "top/mcxiafeng/badger/pages/scanner/ScannerDialogs.kt",
    "top/mcxiafeng/badger/pages/setupguide/SetupGuideViewModel.kt",
]

def ensure_import(src, imp):
    if imp in src:
        return src
    lines = src.split("\n")
    idxs = [i for i, l in enumerate(lines) if l.startswith("import ")]
    pos = (idxs[-1] + 1) if idxs else 0
    lines.insert(pos, imp)
    return "\n".join(lines)

for rel in FILES:
    p = ROOT / rel
    src = p.read_text(encoding="utf-8")
    orig = src
    for old, new in REPLACEMENTS:
        src = src.replace(old, new)
    if src != orig:
        if "KoinComponentBy.get<ContactNetworkResolver>()" in src:
            src = ensure_import(src, KCB)
        p.write_text(src, encoding="utf-8", newline="")
        print("fixed:", rel)
    else:
        print("no-op:", rel)

# 残留校验
import subprocess
