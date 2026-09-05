# -*- coding: utf-8 -*-
"""[KMP K13c] iOS 清扫第六批"""
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

# 1) LogViewerPage packageLogs → CacheFiles（无 zip：存两份文本）
def logviewer(s):
    s = s.replace("import java.util.zip.ZipEntry\n", "")
    s = s.replace("import java.util.zip.ZipOutputStream\n", "")
    s = re.sub(r"^import java\.io\.File\n", "", s, flags=re.M)
    old = s[s.find("private fun packageLogs("):]
    old = old[:old.find("\n}\n") + 3]
    new = '''private fun packageLogs(appInfo: AppInfo): Pair<String, String>? {
    // [KMP K13c] iOS 无 java.util.zip——改为两份文本路径（device_info / logcat），
    // 分享文本由 SystemShare.shareText 承担；zip 打包回归 Android 专属（K17 评估 iOS 打包方案）。
    val dir = LogCollector.cacheDirPath()
    val info = CacheFiles.writeTextToCache("shared", "device_info.txt", getDeviceInfo(appInfo))
    val logs = CacheFiles.writeTextToCache("shared", "logcat.txt", LogCollector.collectRecentLogs())
    if (info == null || logs == null) return null
    return Pair(dir, "shared")
}

'''
    s = s.replace(old, new)
    # shareOrSaveZip → 分享文本
    old2 = s[s.find("private fun shareOrSaveZip("):]
    old2 = old2[:old2.find("\n}\n") + 3]
    new2 = '''private fun shareLogText(dirPair: Pair<String, String>) {
    val text = LogCollector.collectRecentLogs()
    try {
        SystemShare.shareText(if (text.isNotBlank()) text else "(空日志)")
    } catch (e: Exception) {
        BadgerLog.e(TAG, "shareLogText failed", e)
        showToast("分享失败: ${e.message}")
    }
}
'''
    s = s.replace(old2, new2)
    # 调用点
    s = s.replace("""                                val zipFile = withContext(BadgerDispatchers.io) {
                                    packageLogs(appInfo)
                                }
                                if (zipFile != null) {
                                    shareOrSaveZip(zipFile)""",
"""                                val dirPair = withContext(BadgerDispatchers.io) {
                                    packageLogs(appInfo)
                                }
                                if (dirPair != null) {
                                    shareLogText(dirPair)""")
    # File( cacheDir) 残余（259/257 行）删掉
    s = re.sub(r"\n.*File\(LogCollector\.cacheDirPath\(\).*\n", "\n", s)
    s = re.sub(r"^import java\.io\..*\n", "", s, flags=re.M)
    return s
edit("pages/settings/LogViewerPage.kt", logviewer, must=True)

# 2) PlatformDetailDialog：LocalContext 死变量 + context(...) 调用点
def pdd(s):
    s = s.replace("import androidx.compose.ui.platform.LocalContext\n", "")
    s = s.replace("import android.content.Context\n", "")
    s = re.sub(r"[ \t]*val context = LocalContext\.current\n", "", s)
    # Methods.copyToClipboard(context,...) 形态已被 batch 处理；这里是 Xxx(context, ...) 其他调用
    return s
edit("pages/person/contact/dialogs/PlatformDetailDialog.kt", pdd, must=True)

# 3) BasicInfoDialogs：String.format 残留 + datetime 引用
def basic(s):
    s = s.replace('"%04d-%02d-%02d".format(year, month, safeDay)', 'formatBirthday(year, month, safeDay)')
    s = s.replace("String.format(Locale.US, \"%04d-%02d-%02d\", year, month, safeDay)",
                  "formatBirthday(year, month, safeDay)")
    s = s.replace("kotlin.datetime.Clock.System.now()", "kotlinx.datetime.Clock.System.now()")
    s = s.replace(".toLocalDateTime(kotlin.datetime.TimeZone.currentSystemDefault())",
                  ".toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())")
    if "private fun formatBirthday" not in s:
        s = s.rstrip() + '''

private fun formatBirthday(year: Int, month: Int, day: Int): String {
    fun p2(v: Int) = v.toString().padStart(2, '0')
    return "$year-${p2(month)}-${p2(day)}"
}
'''
    return s
edit("pages/person/contact/dialogs/BasicInfoDialogs.kt", basic, must=True)

# 4) ContactDetailComponents：SimpleDateFormat
def cdc(s):
    s = re.sub(r"^import java\..*\n", "", s, flags=re.M)
    s = re.sub(r"SimpleDateFormat\([^)]*\)\.format\(([^)]+)\)", r"formatEpochDateTime(\1)", s)
    if "formatEpochDateTime(" in s and "import top.mcxiafeng.badger.utils.formatEpochDateTime" not in s:
        lines = s.split("\n")
        idxs = [i for i, l in enumerate(lines) if l.startswith("import ")]
        lines.insert(idxs[-1]+1, "import top.mcxiafeng.badger.utils.formatEpochDateTime")
        s = "\n".join(lines)
    return s
edit("pages/person/contact/detail/ContactDetailComponents.kt", cdc, must=True)

# 5) AttachFieldDialog：File → CacheFiles
def attach(s):
    s = re.sub(r"^import java\.io\.File\n", "", s, flags=re.M)
    s = re.sub(r"File\(([^,)]+),\s*([^)]+)\)\.apply \{ mkdirs\(\) \}", r"CacheFiles.dirPath(\1)", s)
    return s
edit("pages/person/contact/dialogs/AttachFieldDialog.kt", attach)

# 6) ScannerPage：removeIf → filter
def scanner(s):
    s = re.sub(r"\.removeIf\s*\{", ".let { list -> list.filterNot { it.@@ }.also { list.clear(); list.addAll(it) }.@@ {", s)
    return s
edit("pages/scanner/ScannerPage.kt", scanner)

# 7) SyncStatusPage Build
def syncpage(s):
    s = re.sub(r"Build\.VERSION\.SDK_INT", "PlatformInfo.apiLevel", s)
    return s
edit("pages/settings/sync/SyncStatusPage.kt", syncpage)

# 8) AboutPage：android import + res
def about(s):
    s = re.sub(r"^import android\..*\n", "", s, flags=re.M)
    return s
edit("pages/settings/AboutPage.kt", about)

# 9) AccountSettingsDialogs / UiSettingsPage 168 / TagManagerSuccessBody 54 / FieldDetailDialog 56 / ImportFromPlatformDialog 3
def generic_localctx(s):
    s = s.replace("import androidx.compose.ui.platform.LocalContext\n", "")
    s = re.sub(r"[ \t]*val context = LocalContext\.current\n", "", s)
    return s
edit("pages/settings/account/AccountSettingsDialogs.kt", generic_localctx)

print("batch 6 done")
