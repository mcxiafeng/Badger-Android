package top.mcxiafeng.badger.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.mcxiafeng.badger.shared.prefs.PrefsPathFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * [KMP K05] SharedPreferences → DataStore Preferences 迁移底座。
 *
 * 设计：**内存缓存 + DataStore 异步落盘**。
 * - 读：全部走内存缓存（同步语义与旧 SharedPreferences 完全一致，几十处调用点零改动）
 * - 写：先更新缓存（设置即时生效），再异步 edit DataStore 落盘
 * - 初始化：[initialize] 阻塞读一次 DataStore（first()），把已有值灌进缓存
 * - 旧值搬迁：见 [PrefsMigrator]——旧 SharedPreferences 文件一次性搬入后打标记
 *
 * DataStore 实例管理：[store] 是全 App 唯一入口（同一文件第二个实例会抛
 * "multiple DataStores active"）。Robolectric 每个测试类换 filesDir，此时旧实例
 * scope 已随测试结束泄漏但指向旧路径，不与新路径实例冲突。
 */
object PrefsStore {

    private val cache = ConcurrentHashMap<String, Any>()
    private val writeMutex = Mutex()

    private val initLock = Any()

    @Volatile
    private var dataStore: DataStore<Preferences>? = null

    @Volatile
    private var dataStorePath: String? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 全 App 唯一 DataStore 实例；目录变化（Robolectric 测试环境）时按新路径重建。 */
    internal fun store(): DataStore<Preferences> {
        val dir = PrefsPathFactory.prefsDir()
        val expected = File(dir, PREFS_FILE_NAME).absolutePath
        dataStore?.let { if (dataStorePath == expected) return it }
        return synchronized(this) {
            dataStore?.let { if (dataStorePath == expected) return it }
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { File(dir, PREFS_FILE_NAME) },
            ).also {
                dataStore = it
                dataStorePath = expected
            }
        }
    }

    /**
     * 启动期调用（BadgerApplication.onCreate）：阻塞灌缓存。
     * 迁移在 [PrefsMigrator] 中先于本调用执行。
     * 可重入：Robolectric 每个测试类都会触发 Application.onCreate。
     */
    fun initialize() {
        synchronized(initLock) {
            val existing = runBlocking { store().data.first() }
            cache.clear()
            existing.asMap().forEach { (key, value) -> cache[key.name] = value }
        }
    }

    // ---- 读写原语（各 prefs 文件全部经由这几个入口） ----

    fun readString(key: String): String? = cache[key] as? String

    fun readBoolean(key: String, default: Boolean): Boolean = cache[key] as? Boolean ?: default

    fun readInt(key: String, default: Int): Int = cache[key] as? Int ?: default

    fun readLong(key: String, default: Long): Long = cache[key] as? Long ?: default

    fun readFloat(key: String, default: Float): Float = cache[key] as? Float ?: default

    fun writeString(key: String, value: String?) = write(key, value, stringPreferencesKey(key))
    fun writeBoolean(key: String, value: Boolean) = write(key, value, booleanPreferencesKey(key))
    fun writeInt(key: String, value: Int) = write(key, value, intPreferencesKey(key))
    fun writeLong(key: String, value: Long) = write(key, value, longPreferencesKey(key))
    fun writeFloat(key: String, value: Float) = write(key, value, floatPreferencesKey(key))

    fun remove(key: String) {
        cache.remove(key)
        scope.launch { persistRemove(key) }
    }

    private fun <T : Any> write(key: String, value: T?, prefKey: Preferences.Key<T>) {
        if (value == null) {
            remove(key)
            return
        }
        cache[key] = value
        scope.launch { persist(prefKey, value) }
    }

    private suspend fun <T : Any> persist(key: Preferences.Key<T>, value: T) {
        try {
            writeMutex.withLock {
                store().edit { prefs -> prefs[key] = value }
            }
        } catch (e: Exception) {
            android.util.Log.e("PrefsStoreTester", "DataStore write failed key=$key", e)
        }
    }

    private suspend fun persistRemove(key: String) {
        try {
            writeMutex.withLock {
                store().edit { it.remove(stringPreferencesKey(key)) }
            }
        } catch (e: Exception) {
            android.util.Log.e("PrefsStoreTester", "DataStore remove failed key=$key", e)
        }
    }

    internal const val PREFS_FILE_NAME = "badger_prefs.preferences_pb"
}

/**
 * [KMP K05] 旧 SharedPreferences → DataStore 一次性搬迁。
 * 每个旧文件搬完即在 DataStore 打 `migrated_<fileName>` 标记，之后不再读旧文件。
 */
object PrefsMigrator {

    private const val TAG = "PrefsMigratorTester"
    private const val MIGRATED_PREFIX = "migrated_"

    private val prefsNames = listOf(
        "badger_auth",
        "badger_short_link",
        "badger_onboarding",
        "badger_hints",
        "badger_settings",
        "badger_gpu_compat",
        "badger_ai_ocr",
        "social_prefs",
        "badger_device",
    )

    /**
     * Application 启动期调用（先于 PrefsStore.initialize）。
     * 阻塞执行：9 个文件键少值小，总耗时可忽略（<10ms 量级）。
     * 复用 [PrefsStore.store] 单例，避免同文件双实例。
     */
    fun migrateAll(context: android.content.Context) {
        PrefsPathFactory.inject(context)
        val store = PrefsStore.store()
        runBlocking {
            val existing = store.data.first()
            val migratedFlags = existing.asMap().keys.map { it.name }.toSet()
            for (prefsName in prefsNames) {
                if (MIGRATED_PREFIX + prefsName in migratedFlags) continue
                val sp = context.getSharedPreferences(prefsName, android.content.Context.MODE_PRIVATE)
                val entries = sp.all
                store.edit { prefs ->
                    entries.forEach { (key, value) ->
                        when (value) {
                            is String -> prefs[stringPreferencesKey(key)] = value
                            is Boolean -> prefs[booleanPreferencesKey(key)] = value
                            is Int -> prefs[intPreferencesKey(key)] = value
                            is Long -> prefs[longPreferencesKey(key)] = value
                            is Float -> prefs[floatPreferencesKey(key)] = value
                            is Set<*> -> @Suppress("UNCHECKED_CAST") {
                                prefs[stringSetPreferencesKey(key)] = value as Set<String>
                            }
                            else -> android.util.Log.w(TAG, "skip unsupported type key=$prefsName.$key type=${value?.javaClass?.name}")
                        }
                    }
                    prefs[booleanPreferencesKey(MIGRATED_PREFIX + prefsName)] = true
                }
                android.util.Log.d(TAG, "migrated $prefsName (${entries.size} keys)")
            }
        }
    }
}
