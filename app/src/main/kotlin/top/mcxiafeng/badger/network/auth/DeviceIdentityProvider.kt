package top.mcxiafeng.badger.network.auth

import android.content.Context
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 本机设备身份：登录请求的 deviceId/deviceName 唯一来源，不落库、不持久化。 */
data class DeviceIdentity(val deviceId: String, val deviceName: String)

/**
 * deviceId = ANDROID_ID（签名作用域内稳定，恢复出厂才变）；deviceName = 机型名。
 * 与 dev 分支 platform/DeviceIdentity.android 语义一致。
 */
@Singleton
class DeviceIdentityProvider @Inject constructor(@ApplicationContext private val context: Context) {

    fun identity(): DeviceIdentity {
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: error("ANDROID_ID 不可用")
        return DeviceIdentity(deviceId = deviceId, deviceName = Build.MODEL)
    }
}
