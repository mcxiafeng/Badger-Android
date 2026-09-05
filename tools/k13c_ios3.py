# -*- coding: utf-8 -*-
"""[KMP K13c] iOS 清扫第三批：BasicInfoDialogs 日期、SetupGuide/SocialViewModel HTTP、CardPage 导入导出、File 残留"""
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

# 1) BasicInfoDialogs：Calendar/SimpleDateFormat → kotlinx-datetime + 纯 Kotlin
def basic_info(s):
    s = s.replace("import java.text.SimpleDateFormat\nimport java.util.Calendar\nimport java.util.Locale\n",
                  "import kotlinx.datetime.LocalDate\n")
    s = s.replace("    val thisYear = remember { Calendar.getInstance().get(Calendar.YEAR) }",
                  "    val thisYear = remember { currentYear() }")
    s = s.replace('String.format(Locale.US, "%04d-%02d-%02d", year, month, safeDay)',
                  '"%04d-%02d-%02d".format(year, month, safeDay)')
    old_parse = s[s.find("private fun parseBirthday"):]
    old_parse = old_parse[:old_parse.find("\n}\n") + 3]
    new_parse = '''private fun parseBirthday(input: String?): Triple<Int, Int, Int> {
    val fallback = Triple(currentYear(), 1, 1)
    if (input.isNullOrBlank()) return fallback
    return try {
        val d = LocalDate.parse(input)
        Triple(d.year, d.monthNumber, d.dayOfMonth)
    } catch (_: Exception) {
        fallback
    }
}

/** 当前年份（kotlinx-datetime，跨端）。 */
private fun currentYear(): Int =
    kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlin.datetime.TimeZone.currentSystemDefault()).year
'''
    s = s.replace(old_parse, new_parse)
    return s
edit("pages/person/contact/dialogs/BasicInfoDialogs.kt", basic_info, must=True)

# 2) SetupGuideViewModel：okhttp3 HEAD 测试连接 → KtorHttpCore
def setup_vm(s):
    s = s.replace("import okhttp3.OkHttpClient\nimport okhttp3.Request\n", "")
    s = s.replace("import top.mcxiafeng.badger.utils.HttpUtil\n", "")
    if "import top.mcxiafeng.badger.utils.KtorHttpCore" not in s:
        s = s.replace("import kotlinx.coroutines.launch\n",
                      "import kotlinx.coroutines.launch\nimport top.mcxiafeng.badger.utils.KtorHttpCore\nimport top.mcxiafeng.badger.utils.HttpResult\n")
    return s
edit("pages/setupguide/SetupGuideViewModel.kt", setup_vm)

# 3) SocialViewModel：HttpUtil → downloadImage 边界
def social_vm(s):
    s = re.sub(r"import top\.mcxiafeng\.badger\.utils\.HttpUtil\n", "", s)
    s = s.replace("import top.mcxiafeng.badger.utils.BadgerLog\n",
                  "import top.mcxiafeng.badger.platform.downloadImage\nimport top.mcxiafeng.badger.utils.BadgerLog\n")
    return s
edit("pages/social/SocialViewModel.kt", social_vm)

# 4) UserProfileDetailComponents：activity launcher 残留
def updc(s):
    for imp in ["import androidx.activity.compose.rememberLauncherForActivityResult\n",
                "import androidx.activity.result.PickVisualMediaRequest\n",
                "import androidx.activity.result.contract.ActivityResultContracts\n"]:
        s = s.replace(imp, "")
    return s
edit("pages/person/contact/UserProfileDetailComponents.kt", updc)

print("batch 3 done")
