package top.mcxiafeng.badger.data.repository

import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.mcxiafeng.badger.utils.BadgerLog
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.prefs.AuthPrefs
import top.mcxiafeng.badger.data.SessionDataCleaner
import top.mcxiafeng.badger.network.ApiException
import top.mcxiafeng.badger.network.AuthUser
import top.mcxiafeng.badger.network.RegisterPolicy
import top.mcxiafeng.badger.network.CaptchaResult
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.network.TokenHolder
import top.mcxiafeng.badger.network.VerificationCodeResult
import top.mcxiafeng.badger.sync.DeviceIdProvider
import top.mcxiafeng.badger.utils.SafeLog
import kotlin.concurrent.Volatile

private const val TAG = "UserAuthRepository"

sealed class AuthState {
    data object Unknown : AuthState()
    data object SignedOut : AuthState()
    data object SignedIn : AuthState()
    data class Error(val message: String) : AuthState()
}

class UserAuthRepository(
    private val tokenHolder: TokenHolder,
    private val serverApiFactory: ServerApiFactory,
    private val deviceIdProvider: DeviceIdProvider,
    private val sessionDataCleaner: SessionDataCleaner,
) {

    private val _state = MutableStateFlow<AuthState>(AuthState.Unknown)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    

    suspend fun bootstrap() {
        val existing = AuthPrefs.readRefreshToken()
        if (existing.isNullOrBlank()) {
            BadgerLog.d(TAG, "bootstrap: no cached refresh token, state=SignedOut")
            tokenHolder.set(null)
            AuthPrefs.clearAuth()
            _state.value = AuthState.SignedOut
            return
        }
        BadgerLog.d(TAG, "bootstrap: cached refresh token found, len=${existing.length}, probing /me")
        tokenHolder.set(existing)
        try {
            val me = withContext(BadgerDispatchers.io) { serverApiFactory.get().me() }
            if (me != null) {
                
                
                val authUser = AuthUser.from(me)
                handleUserSwitch(authUser)
                persistUser(authUser)
                BadgerLog.d(TAG, "bootstrap: /me OK, state=SignedIn")
                _state.value = AuthState.SignedIn
            } else {
                BadgerLog.w(TAG, "bootstrap: /me returned null, clearing auth, state=SignedOut")
                tokenHolder.set(null)
                AuthPrefs.clearAuth()
                _state.value = AuthState.SignedOut
            }
        } catch (e: ApiException) {
            
            
            BadgerLog.w(TAG, "bootstrap: /me rejected status=${e.status}, clearing auth (data preserved)")
            tokenHolder.set(null)
            AuthPrefs.clearAuth()
            _state.value = AuthState.SignedOut
        } catch (e: Exception) {
            
            
            
            BadgerLog.w(TAG, "bootstrap: /me network unavailable (${e::class.simpleName}): ${e.message}, keeping auth")
            _state.value = AuthState.SignedIn
        }
    }

    
    suspend fun register(
        username: String,
        email: String,
        password: String,
        passwordAgain: String,
        captchaId: String?,
        captchaCode: String?,
        emailCaptchaId: String?,
        emailCode: String?,
    ) {
        BadgerLog.d(TAG, "register: enter user=${SafeLog.user(username)} email=${SafeLog.email(email)}")
        try {
            withContext(BadgerDispatchers.io) {
                serverApiFactory.get().register(
                    username, email, password, passwordAgain,
                    captchaId, captchaCode, emailCaptchaId, emailCode,
                )
            }
            BadgerLog.d(TAG, "register: register OK (no token), auto-login")
            val lr = withContext(BadgerDispatchers.io) {
                serverApiFactory.get().login(
                    username, password,
                    deviceId = deviceIdProvider.deviceId(),
                    deviceName = deviceName(),
                )
            }
            onNewAccessToken(lr.token)
            handleUserSwitch(lr.user)
            persistUser(lr.user)
            _state.value = AuthState.SignedIn
            BadgerLog.d(TAG, "register: success (auto-login), state=SignedIn, isAdmin=${lr.user?.isAdmin}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val status = (e as? ApiException)?.status
            BadgerLog.w(TAG, "register: failed status=${status ?: "<n/a>"} type=${e::class.simpleName} msg=${e.message}")
            _state.value = AuthState.Error(e.message ?: "register failed")
            throw e
        }
    }

    
    suspend fun login(username: String, password: String) {
        BadgerLog.d(TAG, "login: enter user=${SafeLog.user(username)} passwordLen=${password.length}")
        try {
            val r = withContext(BadgerDispatchers.io) {
                serverApiFactory.get().login(
                    username, password,
                    deviceId = deviceIdProvider.deviceId(),
                    deviceName = deviceName(),
                )
            }
            onNewAccessToken(r.token)
            handleUserSwitch(r.user)
            persistUser(r.user)
            _state.value = AuthState.SignedIn
            BadgerLog.d(TAG, "login: success, state=SignedIn, isAdmin=${r.user?.isAdmin}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val status = (e as? ApiException)?.status
            BadgerLog.w(TAG, "login: failed status=${status ?: "<n/a>"} type=${e::class.simpleName} msg=${e.message}")
            _state.value = AuthState.Error(e.message ?: "login failed")
            throw e
        }
    }

    
    suspend fun fetchRegisterPolicy(): RegisterPolicy = withContext(BadgerDispatchers.io) {
        serverApiFactory.get().registerPolicy()
    }

    
    suspend fun fetchCaptcha(): CaptchaResult = withContext(BadgerDispatchers.io) {
        serverApiFactory.get().getCaptcha()
    }

    
    suspend fun sendVerificationCode(email: String, purpose: String): VerificationCodeResult =
        withContext(BadgerDispatchers.io) {
            serverApiFactory.get().sendVerificationCode(email, purpose)
        }

    
    suspend fun forgotPassword(
        email: String,
        captchaId: String,
        captchaCode: String,
        newPassword: String,
        newPasswordAgain: String,
    ) {
        withContext(BadgerDispatchers.io) {
            serverApiFactory.get().forgotPassword(email, captchaId, captchaCode, newPassword, newPasswordAgain)
        }
        BadgerLog.d(TAG, "forgotPassword: success for email=${SafeLog.email(email)}")
    }

    suspend fun fetchMe(): JsonObject? = runCatching {
        withContext(BadgerDispatchers.io) { serverApiFactory.get().me() }
    }.getOrElse { e ->
        BadgerLog.w(TAG, "fetchMe: failed ${e::class.simpleName}: ${e.message}")
        null
    }

    suspend fun logout() {
        BadgerLog.d(TAG, "logout: enter")
        runCatching { withContext(BadgerDispatchers.io) { serverApiFactory.get().logout() } }
            .onSuccess { BadgerLog.d(TAG, "logout: server revoke OK") }
            .onFailure { e ->
                BadgerLog.w(TAG, "logout: server revoke failed: ${e::class.simpleName}: ${e.message}")
            }
        tokenHolder.set(null)
        AuthPrefs.clearAuth()
        _state.value = AuthState.SignedOut
        BadgerLog.d(TAG, "logout: cleared local auth, state=SignedOut (data preserved for next login)")
    }

    
    fun currentToken(): String? = tokenHolder.get()

    private fun onNewAccessToken(t: String) {
        tokenHolder.set(t)
        AuthPrefs.writeRefreshToken(t)
        BadgerLog.d(TAG, "onNewAccessToken: tokenHolder updated, len=${t.length}; refresh token persisted")
    }

    
    private fun persistUser(user: AuthUser?) {
        if (user == null) return
        if (user.uuid.isNotBlank()) AuthPrefs.writeUserId(user.uuid)
        if (user.name.isNotBlank()) AuthPrefs.writeUsername(user.name)
        user.displayName?.takeIf { it.isNotBlank() }?.let { AuthPrefs.writeDisplayName(it) }
        user.email?.takeIf { it.isNotBlank() }?.let { AuthPrefs.writeEmail(it) }
        AuthPrefs.writeIsAdmin(user.isAdmin)
        
        user.selfPersonId?.takeIf { it.isNotBlank() }?.let { AuthPrefs.writeSelfPersonId(it) }
        BadgerLog.d(
            TAG,
            "persistUser: uuid=${user.uuid.take(8)}... name=${SafeLog.user(user.name)} isAdmin=${user.isAdmin}"
                + " selfPerson=${user.selfPersonId?.take(8) ?: "<n/a>"}",
        )
    }

    
    private fun deviceName(): String = top.mcxiafeng.badger.shared.util.deviceDisplayName()

    

    private suspend fun handleUserSwitch(user: AuthUser?) {
        if (user == null || user.uuid.isBlank()) return
        val oldUserId = AuthPrefs.readLastUserId()
        if (oldUserId != null && oldUserId != user.uuid) {
            BadgerLog.d(TAG, "handleUserSwitch: account changed old=${oldUserId.take(8)}... new=${user.uuid.take(8)}..., clearing local data")
            sessionDataCleaner.clearAllUserData()
        } else {
            BadgerLog.d(TAG, "handleUserSwitch: same user or first login, preserving local data")
        }
        AuthPrefs.writeLastUserId(user.uuid)
    }
}

class ServerApiFactory {
    @Volatile private var serverApi: ServerApi? = null
    @Volatile private var currentBaseUrl: String = ""

    fun install(api: ServerApi, initialBaseUrl: String) {
        this.serverApi = api
        this.currentBaseUrl = initialBaseUrl
    }

    fun get(): ServerApi =
        serverApi ?: error("ServerApi not yet installed; NetworkModule must initialize first")

    
    fun updateBaseUrl(newUrl: String) {
        val api = serverApi ?: error("ServerApi not yet installed")
        val normalized = newUrl.trim().trimEnd('/')
        if (normalized == currentBaseUrl) return
        currentBaseUrl = normalized
        api.setBaseUrl(normalized)
    }
}