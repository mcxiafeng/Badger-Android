package top.mcxiafeng.badger.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import top.mcxiafeng.badger.shared.db.SpikeContextHolder

private const val TAG = "BatteryOptimization.android"

/** [KMP K13c] Android actual：PowerManager（语义与原 SyncStatusViewModel 一致）。 */
actual object BatteryOptimization {

    actual fun isIgnoring(): Boolean {
        val context = SpikeContextHolder.appContext ?: return false
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } catch (e: Exception) {
            top.mcxiafeng.badger.utils.BadgerLog.w(TAG, "isIgnoring 失败,fallback false", e)
            false
        }
    }

    actual fun openRequestSettings() {
        val context = SpikeContextHolder.appContext ?: return
        try {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            top.mcxiafeng.badger.utils.BadgerLog.w(TAG, "openRequestSettings 失败", e)
            runCatching {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
    }
}
