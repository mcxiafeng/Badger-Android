package top.mcxiafeng.badger.platform

import android.os.Build
import top.mcxiafeng.badger.shared.db.SpikeContextHolder
import top.mcxiafeng.badger.utils.BadgerLog
import java.io.BufferedReader
import java.io.InputStreamReader

private const val TAG = "LogCollector.android"
private const val LOGCAT_LINES = 2000

/** [KMP K13c] Android actual：logcat -d -v time（原 LogViewerPage 实现平移）。 */
actual object LogCollector {

    actual fun collectRecentLogs(): String {
        return try {
            val process = ProcessBuilder("logcat", "-d", "-v", "time", "-t", LOGCAT_LINES.toString())
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().use(BufferedReader::readText)
        } catch (e: Exception) {
            BadgerLog.e(TAG, "collectRecentLogs failed", e)
            ""
        }
    }

    actual fun cacheDirPath(): String =
        SpikeContextHolder.appContext?.cacheDir?.absolutePath ?: error("appContext 未初始化")

    actual fun deviceAbiLine(): String =
        "ABI: ${Build.SUPPORTED_ABIS.joinToString()}"
}
