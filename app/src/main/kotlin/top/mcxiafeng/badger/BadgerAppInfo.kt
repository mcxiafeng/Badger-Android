package top.mcxiafeng.badger

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import top.mcxiafeng.badger.platform.AppInfo

class BadgerAppInfo(private val context: Context) : AppInfo {

    override val versionName: String by lazy {
        try {
            val pm = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.versionName ?: ""
            } else {
                @Suppress("DEPRECATION")
                pm.versionName ?: ""
            }
        } catch (e: Exception) {
            android.util.Log.w("BadgerAppInfo", "versionName 读取失败", e)
            ""
        }
    }

    override val versionCode: Int by lazy {
        try {
            val pm = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pm.versionCode
            }
        } catch (e: Exception) {
            android.util.Log.w("BadgerAppInfo", "versionCode 读取失败", e)
            0
        }
    }

    override val buildDate: String = BuildConfig.BUILD_DATE
}
