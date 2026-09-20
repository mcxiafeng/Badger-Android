package top.mcxiafeng.badger.platform

import android.content.Context
import android.os.Build
import android.provider.Settings

private var appContext: Context? = null

/** 应用入口注入 context（读 ANDROID_ID 需要）；模式同 CacheDbHolder.init。 */
fun initDeviceIdentity(context: Context) {
    appContext = context.applicationContext
}

/** deviceId = ANDROID_ID（签名作用域内稳定，恢复出厂才变）；deviceName = 机型名。 */
actual fun deviceIdentity(): DeviceIdentity {
    val context = appContext ?: error("DeviceIdentity 未初始化：请先在应用入口调用 initDeviceIdentity()")
    val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        ?: error("ANDROID_ID 不可用")
    return DeviceIdentity(deviceId = deviceId, deviceName = Build.MODEL)
}
