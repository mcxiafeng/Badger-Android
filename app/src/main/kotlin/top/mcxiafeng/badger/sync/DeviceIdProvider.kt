package top.mcxiafeng.badger.sync

import android.util.Log
import top.mcxiafeng.badger.data.prefs.PrefsStore
import java.util.UUID

/**
 * 设备 UUID 提供器，单设备稳定，用于多设备冲突排查。
 * [KMP K05] 落 DataStore（经 PrefsStore），登出不清空。
 */
class DeviceIdProvider(
    @Suppress("UNUSED_PARAMETER") private val context: android.content.Context,
) {

    /** 取设备 ID，首次调用时自动生成并落盘。 */
    fun deviceId(): String {
        cachedId?.let { return it }
        return synchronized(this) {
            // double-check：另一个线程可能刚生成完
            cachedId?.let { return it }
            val stored = PrefsStore.readString(KEY_DEVICE_ID)
            val resolved = if (stored.isNullOrBlank()) {
                val fresh = UUID.randomUUID().toString()
                PrefsStore.writeString(KEY_DEVICE_ID, fresh)
                Log.i(TAG, "DeviceIdProvider: generated new deviceId=${takePrefix(fresh)}")
                fresh
            } else stored
            cachedId = resolved
            resolved
        }
    }

    /** 重置设备 ID，仅供测试和开发者设置。 */
    fun resetForTesting() {
        PrefsStore.remove(KEY_DEVICE_ID)
        cachedId = null
        Log.w(TAG, "DeviceIdProvider: resetForTesting — 后续 deviceId() 将生成新 UUID")
    }

    private fun takePrefix(uuid: String): String =
        if (uuid.length >= 8) uuid.substring(0, 8) else uuid

    @Volatile
    private var cachedId: String? = null

    companion object {
        private const val TAG = "DeviceIdProvider"
        private const val KEY_DEVICE_ID = "device_id"
    }
}
