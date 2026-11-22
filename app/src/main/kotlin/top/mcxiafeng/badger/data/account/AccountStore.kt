package top.mcxiafeng.badger.data.account

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Persists space registration and selection, independently of authentication tokens. */
@Singleton
class AccountStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val local = loadLocal()
    private val active = MutableStateFlow(restoreActive())
    val scopeFlow: StateFlow<AccountScope> = active.asStateFlow()

    fun current(): AccountScope = active.value
    fun localScope(): AccountScope = local

    /** Local first, then a stable server/name/identity order. Corrupt entries remain on disk. */
    @Synchronized
    fun registeredScopes(): List<AccountScope> {
        val remote = prefs.all.keys.asSequence().filter { it.startsWith(REGISTRY_PREFIX) }
            .mapNotNull { restoreRegistration(it.removePrefix(REGISTRY_PREFIX)) }
            .sortedWith(compareBy<AccountScope>({ it.baseUrl }, { it.username }, { it.key }))
            .toList()
        Log.d(TAG, "Enumerated registered spaces; remote count=${remote.size}")
        return listOf(local) + remote
    }

    @Synchronized
    fun activateRemote(
        serverId: String, userId: String, dataEpoch: String, username: String, baseUrl: String,
    ): AccountScope {
        val scope = AccountScope.remote(serverId, userId, dataEpoch, username, baseUrl)
        check(prefs.edit().putString(REGISTRY_PREFIX + scope.key, gson.toJson(scope))
            .putString(ACTIVE_KEY, scope.key).commit()) { "Could not persist active account space" }
        active.value = scope
        Log.d(TAG, "Remote space activated; previous generations retained")
        return scope
    }

    @Synchronized
    fun switchToLocal(): AccountScope {
        check(prefs.edit().putString(ACTIVE_KEY, local.key).commit()) { "Could not persist local space selection" }
        active.value = local
        Log.d(TAG, "Local space activated")
        return local
    }

    private fun loadLocal(): AccountScope {
        val saved = prefs.getString(LOCAL_ID, null)
        val id = saved ?: UUID.randomUUID().toString().also {
            check(prefs.edit().putString(LOCAL_ID, it).commit()) { "Could not register local space" }
            Log.d(TAG, "Registered persistent local space")
        }
        return AccountScope.local(id)
    }

    private fun restoreActive(): AccountScope {
        val key = prefs.getString(ACTIVE_KEY, null)
        if (key == null || key == local.key) {
            Log.d(TAG, "Restored local space")
            return local
        }
        return restoreRegistration(key) ?: local
    }

    private fun restoreRegistration(key: String): AccountScope? = try {
        val saved = checkNotNull(prefs.getString(REGISTRY_PREFIX + key, null))
        val scope = checkNotNull(gson.fromJson(saved, AccountScope::class.java))
        // Rebuild both identity and storage paths; never trust serialized path names.
        val restored = AccountScope.remote(
            checkNotNull(scope.serverId), checkNotNull(scope.userId), checkNotNull(scope.dataEpoch),
            checkNotNull(scope.username), checkNotNull(scope.baseUrl),
        )
        check(restored.key == key && scope.key == key) { "Account registration identity mismatch" }
        val address = checkNotNull(restored.baseUrl.toHttpUrlOrNull())
        check(restored.username.isNotBlank() && address.username.isEmpty() && address.password.isEmpty() &&
            address.query == null && address.fragment == null) { "Invalid account registration metadata" }
        Log.d(TAG, "Restored remote space registration")
        restored
    } catch (e: Exception) {
        // Gson exceptions can include the saved account text; log only the failure category.
        Log.e(TAG, "Invalid space registration; retaining data and skipping (${e.javaClass.simpleName})")
        null
    }

    companion object {
        private const val TAG = "AccountStoreTester"
        private const val PREFS_NAME = "account_spaces"
        private const val LOCAL_ID = "local_space_id"
        private const val ACTIVE_KEY = "active_space_key"
        private const val REGISTRY_PREFIX = "registered_"
    }
}
