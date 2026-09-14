package top.mcxiafeng.badger.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runBlocking
import top.mcxiafeng.badger.data.prefs.PrefsStore
import top.mcxiafeng.badger.shared.util.randomUuid
import top.mcxiafeng.badger.utils.BadgerLog
import kotlin.concurrent.Volatile

class DeviceIdProvider {

    private val mutex = Mutex()

    
    fun deviceId(): String {
        cachedId?.let { return it }
        
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
