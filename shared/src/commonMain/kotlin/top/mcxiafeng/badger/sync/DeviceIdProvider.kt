package top.mcxiafeng.badger.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runBlocking
import top.mcxiafeng.badger.data.prefs.PrefsStore
import top.mcxiafeng.badger.shared.util.randomUuid
import top.mcxiafeng.badger.utils.BadgerLog
import kotlin.concurrent.Volatile

/**
 * 设备 UUID 提供器，单设备稳定，用于多设备冲突排查。
 * [KMP K05/K08-B] 落 DataStore（经 PrefsStore），登出不清空。已迁 shared commonMain。
 */
class DeviceIdProvider {

    private val mutex = Mutex()

    /** 取设备 ID，首次调用时自动生成并落盘。 */
    fun deviceId(): String {
        cachedId?.let { return it }
        // 保持旧同步语义（Koin 单例初始化路径为阻塞调用）
        return runBlocking {
            mutex.withLock {
                cachedId?.let { return@runBlocking it }
                val stored = PrefsStore.readString(KEY_DEVICE_ID)
                val resolved = if (stored.isNullOrBlank()) {
                    val fresh = randomUuid()
                    PrefsStore.writeString(KEY_DEVICE_ID, fresh)
                    BadgerLog.i(TAG, "DeviceIdProvider: generated new deviceId=${takePrefix(fresh)}")
                    fresh
                } else stored
                cachedId = resolved
                resolved
            }
        }
    }

    /** 重置设备 ID，仅供测试和开发者设置。 */
    fun resetForTesting() {
        PrefsStore.remove(KEY_DEVICE_ID)
        cachedId = null
        BadgerLog.w(TAG, "DeviceIdProvider: resetForTesting — 后续 deviceId() 将生成新 UUID")
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
