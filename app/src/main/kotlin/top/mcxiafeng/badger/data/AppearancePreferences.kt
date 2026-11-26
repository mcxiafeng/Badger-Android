package top.mcxiafeng.badger.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.Immutable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** 外观属于设备，不随账号或资料空间切换。 */
enum class AppearanceMode(val label: String) {
    System("跟随系统"), Light("浅色"), Dark("深色")
}

/**
 * Accent colours are specified in HSV so lightness and saturation can be tuned independently.
 * Both were raised for the current palette: the previous values read as muddy on neutral surfaces.
 */
enum class AppearanceAccent(
    val label: String,
    val lightArgb: Long,
    val darkArgb: Long,
) {
    Badger("鸢尾蓝", 0xFF6A7BD6L, 0xFFC6CEFFL),
    Forest("森林绿", 0xFF3E8A66L, 0xFFAEE3C4L),
    Ocean("海洋蓝", 0xFF2F86A8L, 0xFFACE0F5L),
    Rose("玫瑰粉", 0xFFC0506FL, 0xFFFFC2D4L),
    Amber("琥珀橙", 0xFFAD6B1EL, 0xFFFFD39AL),
}

@Immutable
data class AppearanceState(
    val mode: AppearanceMode = AppearanceMode.System,
    val accent: AppearanceAccent = AppearanceAccent.Badger,
)

@Singleton
class AppearancePreferences @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val writeMutex = Mutex()

    fun read(): AppearanceState = AppearanceState(
        mode = readOption(KEY_MODE, AppearanceMode.System),
        accent = readOption(KEY_ACCENT, AppearanceAccent.Badger),
    )

    /** 先注册再读取，避免首次订阅丢更新；停止订阅时释放监听器。 */
    val changes: Flow<AppearanceState> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == KEY_MODE || key == KEY_ACCENT) {
                val state = read()
                Log.d(TAG, "外观偏好更新：${state.mode}/${state.accent}")
                trySend(state)
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        Log.d(TAG, "开始观察外观偏好")
        trySend(read())
        awaitClose {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
            Log.d(TAG, "停止观察外观偏好")
        }
    }.conflate().distinctUntilChanged()

    suspend fun setMode(mode: AppearanceMode) {
        save(KEY_MODE, mode.name)
    }

    suspend fun setAccent(accent: AppearanceAccent) {
        save(KEY_ACCENT, accent.name)
    }

    private suspend fun save(key: String, value: String) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (!preferences.edit().putString(key, value).commit()) {
                    throw IOException("无法持久化外观偏好：$key")
                }
                Log.d(TAG, "已保存外观偏好：$key=$value")
            } catch (e: Exception) {
                Log.e(TAG, "保存外观偏好失败：$key", e)
                throw e
            }
        }
    }

    private inline fun <reified T : Enum<T>> readOption(key: String, fallback: T): T {
        val value = try {
            preferences.getString(key, null)
        } catch (e: ClassCastException) {
            Log.e(TAG, "外观偏好类型错误，使用默认值：$key", e)
            return fallback
        }
        if (value == null) return fallback
        return enumValues<T>().firstOrNull { it.name == value } ?: fallback.also {
            Log.w(TAG, "未知外观选项，使用默认值：$key=$value")
        }
    }

    private companion object {
        const val TAG = "AppearancePreferencesTester"
        const val PREFS_NAME = "badger_appearance"
        const val KEY_MODE = "color_mode"
        const val KEY_ACCENT = "accent"
    }
}
