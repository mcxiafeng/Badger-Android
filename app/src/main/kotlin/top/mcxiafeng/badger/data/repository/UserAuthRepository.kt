package top.mcxiafeng.badger.data.repository

import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import android.content.Context
import android.os.Build
import top.mcxiafeng.badger.utils.BadgerLog
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.prefs.AuthPrefs
import top.mcxiafeng.badger.di.NetworkModule
import top.mcxiafeng.badger.network.AuthUser
import top.mcxiafeng.badger.network.RegisterPolicy
import top.mcxiafeng.badger.network.CaptchaResult
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.network.VerificationCodeResult
import top.mcxiafeng.badger.sync.DeviceIdProvider
import top.mcxiafeng.badger.utils.SafeLog

private const val TAG = "UserAuthRepository"

/** Auth 状态机。access token 在 TokenHolder（内存），refresh token 在 AuthPrefs。 */
sealed class AuthState {
    data object Unknown : AuthState()
    data object SignedOut : AuthState()
    data object SignedIn : AuthState()
    data class Error(val message: String) : AuthState()
}

/** 认证状态机 + JWT 持有者，登录/注册/重置密码。 */
class UserAuthRepository(
    private val context: Context,
    private val tokenHolder: NetworkModule.TokenHolder,
    private val serverApiFactory: ServerApiFactory,
    private val deviceIdProvider: DeviceIdProvider,
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
            BadgerLog.d(TAG, "bootstrap: no cached refresh token, state=SignedOut")
            tokenHolder.set(null)
            _state.value = AuthState.SignedOut
            return
        }
        BadgerLog.d(TAG, "bootstrap: cached refresh token found, len=${existing.length}, probing /me")
        tokenHolder.set(existing)
        try {
            val me = withContext(BadgerDispatchers.io) { serverApiFactory.get().me() }
            if (me != null) {
                // [Phase 2]: /me 返回新契约 data（uuid/name/displayName/email/isAdmin），
                // 顺带刷新本地 user 缓存，避免重启后 prefs 里是旧契约字段。
                persistUser(AuthUser.from(me))
                BadgerLog.d(TAG, "bootstrap: /me OK, state=SignedIn")
                _state.value = AuthState.SignedIn
            } else {
                BadgerLog.w(TAG, "bootstrap: /me returned null, clearing auth, state=SignedOut")
                tokenHolder.set(null)
                AuthPrefs.clearAuth(context)
                _state.value = AuthState.SignedOut
            }
        } catch (e: java.net.ConnectException) {
            // 网络不可达不清凭证
            BadgerLog.w(TAG, "bootstrap: /me network unavailable (connect): ${e.message}, keeping auth")
            _state.value = AuthState.SignedIn
        } catch (e: java.net.SocketTimeoutException) {
            BadgerLog.w(TAG, "bootstrap: /me network unavailable (timeout): ${e.message}, keeping auth")
            _state.value = AuthState.SignedIn
        } catch (e: java.net.UnknownHostException) {
            BadgerLog.w(TAG, "bootstrap: /me network unavailable (DNS): ${e.message}, keeping auth")
            _state.value = AuthState.SignedIn
        } catch (e: Exception) {
            BadgerLog.w(TAG, "bootstrap: /me threw ${e.javaClass.simpleName}: ${e.message}, clearing auth")
            tokenHolder.set(null)
            AuthPrefs.clearAuth(context)
            _state.value = AuthState.SignedOut
        }
    }

    /** 注册后自动登录拿 token。 */
    suspend fun register(
        username: String,
        email: String,
        password: String,
        passwordAgain: String,
        captchaId: String?,
        captchaCode: String?,
        emailCaptchaId: String?,
        emailCode: String?,
    ): Result<Unit> {
        BadgerLog.d(TAG, "register: enter user=${SafeLog.user(username)} email=${SafeLog.email(email)}")
        return runCatching {
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
            persistUser(lr.user)
            _state.value = AuthState.SignedIn
            BadgerLog.d(TAG, "register: success (auto-login), state=SignedIn, isAdmin=${lr.user?.isAdmin}")
            Unit
        }.onFailure { e ->
            val status = (e as? top.mcxiafeng.badger.network.ApiException)?.status
            BadgerLog.w(TAG, "register: failed status=${status ?: "<n/a>"} type=${e.javaClass.name} msg=${e.message}")
            _state.value = AuthState.Error(e.message ?: "register failed")
        }
    }

    suspend fun login(username: String, password: String): Result<Unit> {
        BadgerLog.d(TAG, "login: enter user=${SafeLog.user(username)} passwordLen=${password.length}")
        return runCatching {
            val r = withContext(BadgerDispatchers.io) {
                serverApiFactory.get().login(
                    username, password,
                    deviceId = deviceIdProvider.deviceId(),
                    deviceName = deviceName(),
                )
            }
            onNewAccessToken(r.token)
            persistUser(r.user)
            _state.value = AuthState.SignedIn
            BadgerLog.d(TAG, "login: success, state=SignedIn, isAdmin=${r.user?.isAdmin}")
            Unit
        }.onFailure { e ->
            val status = (e as? top.mcxiafeng.badger.network.ApiException)?.status
            BadgerLog.w(TAG, "login: failed status=${status ?: "<n/a>"} type=${e.javaClass.name} msg=${e.message}")
            _state.value = AuthState.Error(e.message ?: "login failed")
        }
    }

    /** 拉注册策略。 */
    suspend fun fetchRegisterPolicy(): RegisterPolicy = withContext(BadgerDispatchers.io) {
        serverApiFactory.get().registerPolicy()
    }

    /** 取图形验证码。 */
    suspend fun fetchCaptcha(): CaptchaResult = withContext(BadgerDispatchers.io) {
        serverApiFactory.get().getCaptcha()
    }

    /** 发邮箱验证码。 */
    suspend fun sendVerificationCode(email: String, purpose: String): VerificationCodeResult =
        withContext(BadgerDispatchers.io) {
            serverApiFactory.get().sendVerificationCode(email, purpose)
        }

    /** 重置密码。不返回 Result<Unit> 以回避 MockK 泛型擦除问题。 */
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
        BadgerLog.w(TAG, "fetchMe: failed ${e.javaClass.simpleName}: ${e.message}")
        null
    }

    suspend fun logout() {
        BadgerLog.d(TAG, "logout: enter")
        runCatching { withContext(BadgerDispatchers.io) { serverApiFactory.get().logout() } }
            .onSuccess { BadgerLog.d(TAG, "logout: server revoke OK") }
            .onFailure { e ->
                BadgerLog.w(TAG, "logout: server revoke failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        tokenHolder.set(null)
        AuthPrefs.clearAuth(context)
        _state.value = AuthState.SignedOut
        BadgerLog.d(TAG, "logout: cleared local auth, state=SignedOut")
    }

    /** Current access JWT, used by ServerApi directly (not via interceptor). */
    fun currentToken(): String? = tokenHolder.get()

    private fun onNewAccessToken(t: String) {
        tokenHolder.set(t)
        AuthPrefs.writeRefreshToken(context, t)
        BadgerLog.d(TAG, "onNewAccessToken: tokenHolder updated, len=${t.length}; refresh token persisted")
    }

    /** 把 user 字段刷进 AuthPrefs。 */
    private fun persistUser(user: AuthUser?) {
        if (user == null) return
        if (user.uuid.isNotBlank()) AuthPrefs.writeUserId(context, user.uuid)
        if (user.name.isNotBlank()) AuthPrefs.writeUsername(context, user.name)
        user.displayName?.takeIf { it.isNotBlank() }?.let { AuthPrefs.writeDisplayName(context, it) }
        user.email?.takeIf { it.isNotBlank() }?.let { AuthPrefs.writeEmail(context, it) }
        AuthPrefs.writeIsAdmin(context, user.isAdmin)
        BadgerLog.d(TAG, "persistUser: uuid=${user.uuid.take(8)}... name=${SafeLog.user(user.name)} isAdmin=${user.isAdmin}")
    }

    /** 设备显示名（服务端 Device 行展示用）。 */
    private fun deviceName(): String {
        val s = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        return s.ifBlank { "Android" }
    }
}

/**
 * ServerApi 的进程级单例工厂。
 * [updateBaseUrl] 是运行时换服务地址的唯一入口。
 */
class ServerApiFactory {
    @Volatile private var serverApi: ServerApi? = null
    @Volatile private var currentBaseUrl: String = ""

    fun install(api: ServerApi, initialBaseUrl: String) {
        this.serverApi = api
        this.currentBaseUrl = initialBaseUrl
    }

    fun get(): ServerApi =
        serverApi ?: error("ServerApi not yet installed; NetworkModule must initialize first")

    /** 运行时热更新 ServerApi base URL。调用方必须先写 AuthPrefs。 */
    fun updateBaseUrl(newUrl: String) {
        val api = serverApi ?: error("ServerApi not yet installed")
        val normalized = newUrl.trim().trimEnd('/')
        if (normalized == currentBaseUrl) return
        currentBaseUrl = normalized
        api.setBaseUrl(normalized)
    }
}