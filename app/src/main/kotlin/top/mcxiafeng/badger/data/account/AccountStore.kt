package top.mcxiafeng.badger.data.account

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        return try {
            val saved = checkNotNull(prefs.getString(REGISTRY_PREFIX + key, null))
            val scope = gson.fromJson(saved, AccountScope::class.java)
            // Derive paths again instead of trusting serialized names.
            val restored = AccountScope.remote(
                checkNotNull(scope.serverId), checkNotNull(scope.userId), checkNotNull(scope.dataEpoch),
                scope.username, scope.baseUrl,
            )
            check(restored.key == key) { "Account registration identity mismatch" }
            Log.d(TAG, "Restored remote space")
            restored
        } catch (e: Exception) {
            Log.e(TAG, "Invalid active space registration; retaining data and opening local space", e)
            local
        }
    }

    companion object {
        private const val TAG = "AccountStoreTester"
        private const val PREFS_NAME = "account_spaces"
        private const val LOCAL_ID = "local_space_id"
        private const val ACTIVE_KEY = "active_space_key"
        private const val REGISTRY_PREFIX = "registered_"
    }
}
