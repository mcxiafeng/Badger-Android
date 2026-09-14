package top.mcxiafeng.badger.platform

import android.os.Build

actual object PlatformInfo {
    actual val apiLevel: Int
        get() = Build.VERSION.SDK_INT

    actual val deviceModel: String
        get() = Build.MODEL
}
