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
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.mcxiafeng.badger.shared.prefs.PrefsPathFactory
import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.mcxiafeng.badger.utils.BadgerLog
import okio.Path.Companion.toPath

/**
 * [KMP K05/K08-B] SharedPreferences → DataStore Preferences 迁移底座（commonMain）。
 *
 * 设计：**不可变快照缓存（copy-on-write）+ DataStore 异步落盘**。
 * - 读：无锁查快照（同步语义与旧 SharedPreferences 完全一致，几十处调用点零改动）
 * - 写：先替换快照（设置即时生效），再异步 edit DataStore 落盘
 * - 初始化：[initialize] 阻塞读一次 DataStore（first()），把已有值灌进快照
 * - 旧值搬迁：androidMain 的 PrefsMigrator（SharedPreferences 是 Android API）
 *
 * [KMP K08-B] 平台替换：ConcurrentHashMap→AtomicReference 快照、@Volatile→kotlin.concurrent.Volatile、
 * synchronized→Mutex/AtomicReference CAS（common 无 java.util.concurrent）。
 *
 * DataStore 实例管理：[store] 是全 App 唯一入口（同一文件第二个实例会抛
 * "multiple DataStores active"）。Robolectric 每个测试类换 filesDir，此时旧实例
 * scope 已随测试结束泄漏但指向旧路径，不与新路径实例冲突。
 */
object PrefsStore {

    /** 不可变键值快照；读无锁，写 copy-on-write。 */
    private val snapshot = atomic(emptyMap<String, Any>())
    private val writeMutex = Mutex()

    @kotlin.concurrent.Volatile
    private var dataStore: DataStore<Preferences>? = null

    @kotlin.concurrent.Volatile
    private var dataStorePath: String? = null

    private val scope = CoroutineScope(SupervisorJob() + BadgerDispatchers.io)

    /** 全 App 唯一 DataStore 实例；目录变化（Robolectric 测试环境）时按新路径重建。 */
    internal fun store(): DataStore<Preferences> {
        val dir = PrefsPathFactory.prefsDir()
        val expected = (dir.toPath() / PREFS_FILE_NAME).toString()
        dataStore?.let { if (dataStorePath == expected) return it }
        return synchronizedCreate()
    }

    /** CAS 建单例（common 无 synchronized；比双检锁简单且正确）。 */
    private fun synchronizedCreate(): DataStore<Preferences> {
        val dir = PrefsPathFactory.prefsDir()
        val expected = (dir.toPath() / PREFS_FILE_NAME).toString()
        val created = PreferenceDataStoreFactory.createWithPath(
            scope = scope,
            produceFile = { dir.toPath() / PREFS_FILE_NAME },
        )
        val existing = dataStore
        if (existing != null && dataStorePath == expected) return existing
        dataStore = created
        dataStorePath = expected
        return created
    }

    /**
     * 启动期调用（BadgerApplication.onCreate）：阻塞灌快照。
     * 迁移在 androidMain PrefsMigrator 中先于本调用执行。
     * 可重入：Robolectric 每个测试类都会触发 Application.onCreate。
     */
    fun initialize() {
        val existing = runBlocking { store().data.first() }
        val fresh = existing.asMap().entries.associate { (key, value) -> key.name to value }
        snapshot.value = fresh
    }

    // ---- 读写原语（各 prefs 文件全部经由这几个入口） ----

    fun readString(key: String): String? = snapshot.value[key] as? String

    fun readBoolean(key: String, default: Boolean): Boolean = snapshot.value[key] as? Boolean ?: default

    fun readInt(key: String, default: Int): Int = snapshot.value[key] as? Int ?: default

    fun readLong(key: String, default: Long): Long = snapshot.value[key] as? Long ?: default

    fun readFloat(key: String, default: Float): Float = snapshot.value[key] as? Float ?: default

    fun writeString(key: String, value: String?) = write(key, value, stringPreferencesKey(key))
    fun writeBoolean(key: String, value: Boolean) = write(key, value, booleanPreferencesKey(key))
    fun writeInt(key: String, value: Int) = write(key, value, intPreferencesKey(key))
    fun writeLong(key: String, value: Long) = write(key, value, longPreferencesKey(key))
    fun writeFloat(key: String, value: Float) = write(key, value, floatPreferencesKey(key))

    fun remove(key: String) {
        while (true) {
            val current = snapshot.value
            if (snapshot.compareAndSet(current, current - key)) break
        }
        scope.launch { persistRemove(key) }
    }

    private fun <T : Any> write(key: String, value: T?, prefKey: Preferences.Key<T>) {
        if (value == null) {
            remove(key)
            return
        }
        while (true) {
            val current = snapshot.value
            if (snapshot.compareAndSet(current, current + (key to value))) break
        }
        scope.launch { persist(prefKey, value) }
    }

    private suspend fun <T : Any> persist(key: Preferences.Key<T>, value: T) {
        try {
            writeMutex.withLock {
                store().edit { prefs -> prefs[key] = value }
            }
        } catch (e: Exception) {
            BadgerLog.e("PrefsStoreTester", "DataStore write failed key=$key", e)
        }
    }

    private suspend fun persistRemove(key: String) {
        try {
            writeMutex.withLock {
                store().edit { it.remove(stringPreferencesKey(key)) }
            }
        } catch (e: Exception) {
            BadgerLog.e("PrefsStoreTester", "DataStore remove failed key=$key", e)
        }
    }

    internal const val PREFS_FILE_NAME = "badger_prefs.preferences_pb"
}
