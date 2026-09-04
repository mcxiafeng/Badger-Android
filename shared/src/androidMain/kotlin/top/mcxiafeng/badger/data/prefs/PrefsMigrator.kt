package top.mcxiafeng.badger.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import top.mcxiafeng.badger.shared.prefs.PrefsPathFactory

/**
 * [KMP K08-B] 旧 SharedPreferences → DataStore 一次性搬迁（androidMain：
 * getSharedPreferences 是 Android API）。每个旧文件搬完即在 DataStore 打
 * `migrated_<fileName>` 标记，之后不再读旧文件。
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
    fun migrateAll(context: Context) {
        PrefsPathFactory.inject(context)
        val store = PrefsStore.store()
        runBlocking {
            val existing = store.data.first()
            val migratedFlags = existing.asMap().keys.map { it.name }.toSet()
            for (prefsName in prefsNames) {
                if (MIGRATED_PREFIX + prefsName in migratedFlags) continue
                val sp = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
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
