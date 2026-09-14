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

object PrefsStore {

    
    private val snapshot = atomic(emptyMap<String, Any>())
    private val writeMutex = Mutex()

    @kotlin.concurrent.Volatile
    private var dataStore: DataStore<Preferences>? = null

    @kotlin.concurrent.Volatile
    private var dataStorePath: String? = null

    private val scope = CoroutineScope(SupervisorJob() + BadgerDispatchers.io)

    
    internal fun store(): DataStore<Preferences> {
        val dir = PrefsPathFactory.prefsDir()
        val expected = (dir.toPath() / PREFS_FILE_NAME).toString()
        dataStore?.let { if (dataStorePath == expected) return it }
        return synchronizedCreate()
    }

    
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

    

    fun initialize() {
        val existing = runBlocking { store().data.first() }
        val fresh = existing.asMap().entries.associate { (key, value) -> key.name to value }
        snapshot.value = fresh
    }

    

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
