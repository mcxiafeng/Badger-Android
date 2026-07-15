package top.mcxiafeng.badger.data.repository

import android.content.Context
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.mcxiafeng.badger.data.AuthPrefs
import top.mcxiafeng.badger.di.NetworkModule
import top.mcxiafeng.badger.network.ServerApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Front-of-app auth state machine + JWT holder. The access token is held
 * in the process-singleton [NetworkModule.TokenHolder] which the OkHttp
 * interceptor reads on every request. The refresh token lives in
 * [AuthPrefs] SharedPreferences only.
 */
sealed class AuthState {
    data object Unknown : AuthState()
    data object SignedOut : AuthState()
    data object SignedIn : AuthState()
    data class Error(val message: String) : AuthState()
}

@Singleton
class UserAuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenHolder: NetworkModule.TokenHolder,
    private val serverApiFactory: ServerApiFactory,
) {

    private val _state = MutableStateFlow<AuthState>(AuthState.Unknown)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /**
     * Reads the (possibly-stale) access token from prefs and tries to
     * mint a fresh one via /api/auth/refresh. Safe to call multiple times.
     */
    suspend fun bootstrap() {
        val existing = AuthPrefs.readRefreshToken(context)
        if (existing.isNullOrBlank()) {
            tokenHolder.set(null)
            _state.value = AuthState.SignedOut
            return
        }
        // Try with the cached token first — the OkHttp interceptor handles
        // 401-driven refresh on its own.
        tokenHolder.set(existing)
        try {
            val me = serverApiFactory.get().me()
            if (me != null) {
                _state.value = AuthState.SignedIn
            } else {
                tokenHolder.set(null)
                AuthPrefs.clearAuth(context)
                _state.value = AuthState.SignedOut
            }
        } catch (e: Throwable) {
            tokenHolder.set(null)
            AuthPrefs.clearAuth(context)
            _state.value = AuthState.SignedOut
        }
    }

    suspend fun register(username: String, password: String, email: String?, displayName: String?): Result<Unit> {
        return runCatching {
            val r = serverApiFactory.get().register(username, password, email, displayName)
            onNewAccessToken(r.token)
            if (!r.username.isNullOrBlank()) AuthPrefs.writeUsername(context, r.username)
            if (!r.role.isNullOrBlank()) AuthPrefs.writeRole(context, r.role)
            _state.value = AuthState.SignedIn
        }.onFailure { e ->
            _state.value = AuthState.Error(e.message ?: "register failed")
        }
    }

    suspend fun login(username: String, password: String): Result<Unit> {
        return runCatching {
            val r = serverApiFactory.get().login(username, password)
            onNewAccessToken(r.token)
            AuthPrefs.writeUsername(context, username)
            if (!r.role.isNullOrBlank()) AuthPrefs.writeRole(context, r.role)
            _state.value = AuthState.SignedIn
        }.onFailure { e ->
            _state.value = AuthState.Error(e.message ?: "login failed")
        }
    }

    suspend fun fetchMe(): JsonObject? = runCatching {
        serverApiFactory.get().me()
    }.getOrNull()

    suspend fun logout() {
        runCatching { serverApiFactory.get().logout() }
        tokenHolder.set(null)
        AuthPrefs.clearAuth(context)
        _state.value = AuthState.SignedOut
    }

    /** Current access JWT, used by ServerApi directly (not via interceptor). */
    fun currentToken(): String? = tokenHolder.get()

    private fun onNewAccessToken(t: String) {
        tokenHolder.set(t)
        AuthPrefs.writeRefreshToken(context, t)
    }
}

/**
 * Hilt indirection: ServerApi needs the tokenProvider lambda which in turn
 * needs the [NetworkModule.TokenHolder] injected into the repo. The factory
 * pattern lets NetworkModule fill in the OkHttp client + base URL after
 * Hilt has constructed everything.
 */
@Singleton
class ServerApiFactory @Inject constructor() {
    @Volatile private var provider: (() -> ServerApi)? = null

    fun install(provider: () -> ServerApi) {
        this.provider = provider
    }

    fun get(): ServerApi = provider?.invoke() ?: error("ServerApi not yet installed")
}