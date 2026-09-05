package top.mcxiafeng.badger.platform

import android.os.Build

/** [KMP K13c] Android actual：Build.VERSION / Build.MODEL。 */
actual object PlatformInfo {
    actual val apiLevel: Int
        get() = Build.VERSION.SDK_INT

    actual val deviceModel: String
        get() = Build.MODEL
}
