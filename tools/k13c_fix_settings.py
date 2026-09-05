# -*- coding: utf-8 -*-
"""[KMP K13c] LogViewerPage + AboutPage 平台残留修复"""
from pathlib import Path

BASE = Path(r"F:\Java\Android Project\Badger\shared\src\commonMain\kotlin\top\mcxiafeng\badger\pages\settings")

# ============ LogViewerPage.kt ============
p = BASE / "LogViewerPage.kt"
s = p.read_text(encoding="utf-8")

for imp in [
    "import android.os.Build\n",
    "import androidx.compose.ui.platform.LocalContext\n",
    "import top.mcxiafeng.badger.BuildConfig\n",
    "import java.io.BufferedReader\n",
    "import java.io.InputStreamReader\n",
    "import java.text.SimpleDateFormat\n",
    "import java.util.Date\n",
    "import java.util.Locale\n",
]:
    s = s.replace(imp, "")
s = s.replace("import java.io.BufferedReader\nimport java.io.File\nimport java.io.InputStreamReader\n",
              "import java.io.File\n")
s = s.replace("import top.mcxiafeng.badger.platform.showToast\n",
              "import top.mcxiafeng.badger.platform.LogCollector\nimport top.mcxiafeng.badger.platform.PlatformInfo\nimport top.mcxiafeng.badger.platform.showToast\n")
s = s.replace("import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding\n",
              "import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding\nimport top.mcxiafeng.badger.platform.AppInfo\nimport top.mcxiafeng.badger.di.KoinComponentBy\n")

# 调用点：collectLogcat → LogCollector
s = s.replace("val text = withContext(BadgerDispatchers.io) { collectLogcat() }",
              "val text = withContext(BadgerDispatchers.io) { LogCollector.collectRecentLogs() }")

# getDeviceInfo → common 版（AppInfo 经 Koin 注入）
old_info = s[s.find("private fun getDeviceInfo(): String {"):]
old_info = old_info[:old_info.find("\n}\n") + 3]
new_info = """private fun getDeviceInfo(appInfo: AppInfo): String {
    return buildString {
        appendLine("=== 设备信息 ===")
        appendLine("型号: ${PlatformInfo.deviceModel}")
        appendLine("系统版本: API/OS ${PlatformInfo.apiLevel}")
        appendLine(LogCollector.deviceAbiLine())
        appendLine()
        appendLine("=== 应用信息 ===")
        appendLine("版本: ${appInfo.versionName}")
        appendLine("构建日期: ${appInfo.buildDate}")
        appendLine()
"""
s = s.replace(old_info, new_info)

# packageLogs：context → cacheDir 边界
s = s.replace("""private fun packageLogs(context: android.content.Context): File? {
    val cacheDir = File(context.cacheDir, "shared").apply { mkdirs() }""",
"""private fun packageLogs(appInfo: AppInfo): File? {
    val cacheDir = File(LogCollector.cacheDirPath(), "shared").apply { mkdirs() }""")
s = s.replace("zos.write(getDeviceInfo().toByteArray())", "zos.write(getDeviceInfo(appInfo).toByteArray())")
# tombstones/ANR 是 Android 专属诊断路径，保留 File 逻辑（File 是 kotlin stdlib 跨端）
s = s.replace("""private fun shareOrSaveZip(context: android.content.Context, zipFile: File) {""",
              """private fun shareOrSaveZip(zipFile: File) {""")

# 调用点
s = s.replace("""                                val zipFile = withContext(BadgerDispatchers.io) {
                                    packageLogs(context)
                                }
                                if (zipFile != null) {
                                    shareOrSaveZip(context, zipFile)""",
"""                                val zipFile = withContext(BadgerDispatchers.io) {
                                    packageLogs(appInfo)
                                }
                                if (zipFile != null) {
                                    shareOrSaveZip(zipFile)""")

# 剪贴板
s = s.replace('Methods.copyToClipboard(context, "软件日志", logText)', 'Methods.copyToClipboard("软件日志", logText)')

# context / appInfo 状态
s = s.replace("    val context = LocalContext.current\n",
              "    val appInfo = remember { KoinComponentBy.get<AppInfo>() }\n")

p.write_text(s, encoding="utf-8", newline="")
residual = {k: s.count(k) for k in ["BuildConfig", "LocalContext", "collectLogcat", "SimpleDateFormat", "Build.", "context"]}
print("LogViewerPage:", residual)

# ============ AboutPage.kt ============
p2 = BASE / "AboutPage.kt"
s2 = p2.read_text(encoding="utf-8")
s2 = s2.replace("import top.mcxiafeng.badger.BuildConfig\n", "")
if "import top.mcxiafeng.badger.R" in s2:
    # R 引用（开源许可页可能引用 app 图标等）先探查
    pass
s2 = s2.replace("BuildConfig.VERSION_NAME", "appInfo.versionName")
s2 = s2.replace("BuildConfig.BUILD_DATE", "appInfo.buildDate")
s2 = s2.replace("BuildConfig.VERSION_CODE", "appInfo.versionCode")
s2 = s2.replace('BadgerLog.d(TAG, "AboutPage loaded, version=${BuildConfig.VERSION_NAME}")',
                'BadgerLog.d(TAG, "AboutPage loaded, version=${appInfo.versionName}")')
p2.write_text(s2, encoding="utf-8", newline="")
print("AboutPage BuildConfig refs:", s2.count("BuildConfig"), "| R refs:", s2.count("badger.R"))
