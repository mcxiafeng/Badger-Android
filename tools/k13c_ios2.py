# -*- coding: utf-8 -*-
"""[KMP K13c] iOS 清扫第二批：java.text/java.util 日期 → kotlinx-datetime"""
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

# 1) OperationHistoryOpFormatter：SimpleDateFormat → kotlinx-datetime
def opfmt(s):
    s = s.replace("import java.text.SimpleDateFormat\nimport java.util.Date\nimport java.util.Locale\n",
                  "import kotlinx.datetime.TimeZone\nimport kotlinx.datetime.Instant\nimport kotlinx.datetime.toLocalDateTime\n")
    s = re.sub(r'(?s)val (fmt\w*) = SimpleDateFormat\("([^"]+)", Locale\.(?:US|getDefault)\)\n', lambda m: "", s)
    # 找 format 调用形态再替换——通用做法：SimpleDateFormat(...).format(Date(x)) → formatEpoch(x)
    s = re.sub(r"(\w+)\.format\(Date\(([^)]+)\)\)", r"formatEpoch(\2)", s)
    s = re.sub(r"SimpleDateFormat\(\"[^\"]+\", Locale\.\w+\)\.format\(Date\(([^)]+)\)\)", r"formatEpoch(\1)", s)
    if "fun formatEpoch" not in s and "formatEpoch(" in s:
        s = s.rstrip() + '''

/** [KMP K13c] epoch millis → `yyyy-MM-dd HH:mm`（kotlinx-datetime 跨端）。 */
internal fun formatEpoch(epoch: Long): String {
    val local = Instant.fromEpochMilliseconds(epoch).toLocalDateTime(TimeZone.currentSystemDefault())
    fun p2(v: Int) = v.toString().padStart(2, '0')
    return "${local.year}-${p2(local.monthNumber)}-${p2(local.dayOfMonth)} ${p2(local.hour)}:${p2(local.minute)}"
}
'''
    return s
edit("pages/settings/history/OperationHistoryOpFormatter.kt", opfmt, must=True)

# 2) TagManager 三件套：SimpleDateFormat/Date/Locale 同款替换
def tags_fix(s):
    s = s.replace("import java.text.SimpleDateFormat\nimport java.util.Date\nimport java.util.Locale\n", "")
    s = re.sub(r"SimpleDateFormat\(\"[^\"]+\", Locale\.\w+\)\.format\(Date\(([^)]+)\)\)", r"formatEpoch(\1)", s)
    s = re.sub(r"(\w+)\.format\(Date\(([^)]+)\)\)", r"formatEpoch(\2)", s)
    s = s.replace("import java.util.Date\n", "")
    if "formatEpoch(" in s and "import top.mcxiafeng.badger.utils.formatEpoch" not in s and "fun formatEpoch" not in s:
        s = s.replace("import kotlinx.datetime.Instant\n", "")
        # 引用公共 helper（放 utils）
        s = s.replace("import top.mcxiafeng.badger.utils.BadgerLog\n",
                      "import top.mcxiafeng.badger.utils.BadgerLog\nimport top.mcxiafeng.badger.utils.formatEpochDate\n")
        s = re.sub(r"\bformatEpoch\(", "formatEpochDate(", s)
    return s
edit("pages/settings/tags/TagManagerSuccessBody.kt", tags_fix)
edit("pages/settings/tags/TagManagerSettingsPage.kt", tags_fix)
edit("pages/settings/tags/TagManagerComponents.kt", tags_fix)

print("batch 2a done")
