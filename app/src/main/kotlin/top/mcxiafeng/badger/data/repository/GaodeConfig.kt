package top.mcxiafeng.badger.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import top.mcxiafeng.badger.utils.EncryptedPrefs

/** 高德配置和请求可安全向用户显示的错误。 */
internal class GaodeException(message: String) : IllegalStateException(message)

@Singleton
class GaodeConfig internal constructor(openPrefs: () -> SharedPreferences) {
    @Inject constructor(@ApplicationContext context: Context) : this({ openEncryptedPrefs(context) })

    private val prefs by lazy(openPrefs)
    private val keyMutex = Mutex()
    private var savedKey: String? = null

    suspend fun getApiKey(): String = withContext(Dispatchers.IO) {
        keyMutex.withLock {
            storageOperation("读取", ::readSavedKey)
        }
    }

    suspend fun setApiKey(apiKey: String) = withContext(Dispatchers.IO) {
        keyMutex.withLock {
            storageOperation("保存") {
                // SharedPreferences 即使写盘失败，也可能已经更新进程内的值。
                // 写入前保留已确认的值，只有写盘成功后才提供新 Key。
                readSavedKey()
                val editor = prefs.edit()
                if (apiKey.isEmpty()) editor.remove(KEY_API_KEY) else editor.putString(KEY_API_KEY, apiKey)
                check(editor.commit())
                savedKey = apiKey
                Log.d(TAG, "高德 Key 配置已保存")
            }
        }
    }

    private fun readSavedKey(): String = savedKey ?: prefs.getString(KEY_API_KEY, "").orEmpty().also {
        savedKey = it
    }

    private inline fun <T> storageOperation(operation: String, block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "高德 Key $operation 失败，类型=${e.javaClass.simpleName}")
        throw GaodeException("无法${operation}高德 Key，请检查设备的安全存储后重试")
    }

    companion object {
        private const val ENCRYPTED_PREFS_NAME = "gaode_credentials"
        private const val KEY_API_KEY = "api_key"
        private const val TAG = "GaodeConfigTester"

        private fun openEncryptedPrefs(context: Context): SharedPreferences =
            EncryptedPrefs.open(context, ENCRYPTED_PREFS_NAME)
    }
}
