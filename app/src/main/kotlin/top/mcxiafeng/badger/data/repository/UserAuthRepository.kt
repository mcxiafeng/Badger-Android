package top.mcxiafeng.badger.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.AuthPrefs
import top.mcxiafeng.badger.di.NetworkModule
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.utils.SafeLog
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "UserAuthRepository"

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
            Log.d(TAG, "bootstrap: no cached refresh token, state=SignedOut")
            tokenHolder.set(null)
            _state.value = AuthState.SignedOut
            return
        }
        // 仅记录存在性 + token 长度,绝不打印 token 本身
        Log.d(TAG, "bootstrap: cached refresh token found, len=${existing.length}, probing /me")
        // Try with the cached token first — the OkHttp interceptor handles
        // 401-driven refresh on its own.
        tokenHolder.set(existing)
        try {
            // [修复防御]: 同 register/login —— 同步 OkHttp 调用必须切 IO 线程
            val me = withContext(Dispatchers.IO) { serverApiFactory.get().me() }
            if (me != null) {
                Log.d(TAG, "bootstrap: /me OK, state=SignedIn")
                _state.value = AuthState.SignedIn
            } else {
                Log.w(TAG, "bootstrap: /me returned null, clearing auth, state=SignedOut")
                tokenHolder.set(null)
                AuthPrefs.clearAuth(context)
                _state.value = AuthState.SignedOut
            }
        } catch (e: Throwable) {
            // 修复防御:bootstrap 失败不能吞掉根因 —— 记录异常类型与消息,便于排查
            // "app 重启后莫名其妙掉登录" 这一类问题。token 本身仍然只在 prefs 里,
            // 这里不打印。
            Log.w(TAG, "bootstrap: /me threw ${e.javaClass.simpleName}: ${e.message}, clearing auth")
            tokenHolder.set(null)
            AuthPrefs.clearAuth(context)
            _state.value = AuthState.SignedOut
        }
    }

    suspend fun register(username: String, password: String, email: String?, displayName: String?): Result<Unit> {
        Log.d(TAG, "register: enter user=${SafeLog.user(username)} email=${SafeLog.email(email)}")
        return runCatching {
            // [修复防御]: ServerApi.* 是普通函数,内部走 OkHttp 同步 execute()。
            // viewModelScope 默认 Main.immediate,直接调用会抛 NetworkOnMainThreadException
            // (从 logcat 实测已复现:type=android.os.NetworkOnMainThreadException)。
            // 显式切到 IO 线程,避免阻塞 UI / 系统拦截。
            val r = withContext(Dispatchers.IO) {
                serverApiFactory.get().register(username, password, email, displayName)
            }
            onNewAccessToken(r.token)
            if (!r.username.isNullOrBlank()) AuthPrefs.writeUsername(context, r.username)
            if (!r.role.isNullOrBlank()) AuthPrefs.writeRole(context, r.role)
            _state.value = AuthState.SignedIn
            Log.d(TAG, "register: success, state=SignedIn, role=${r.role ?: "<none>"}")
            // 修复防御:Kotlin 中 Log.d 返回 Int,显式 Unit 让 runCatching 推断为 Result<Unit>
            Unit
        }.onFailure { e ->
            // 修复防御:服务端常见错误(用户名重复 / 弱密码)会以 4xx 抛 ApiException;
            // 记录 status 与异常类型,便于稳定聚合。
            val status = (e as? top.mcxiafeng.badger.network.ApiException)?.status
            Log.w(TAG, "register: failed status=${status ?: "<n/a>"} type=${e.javaClass.name} msg=${e.message}")
            _state.value = AuthState.Error(e.message ?: "register failed")
        }
    }

    suspend fun login(username: String, password: String): Result<Unit> {
        Log.d(TAG, "login: enter user=${SafeLog.user(username)} passwordLen=${password.length}")
        return runCatching {
            // [修复防御]: 同 register —— ServerApi.login 是同步阻塞调用,必须切到 IO 线程,
            // 否则会抛 NetworkOnMainThreadException。这是 logcat 已确认的真凶:
            // "type=android.os.NetworkOnMainThreadException msg=null" → OkHttp 一个字节都没发出去,
            // 所以服务器看不到任何连接。
            val r = withContext(Dispatchers.IO) {
                serverApiFactory.get().login(username, password)
            }
            onNewAccessToken(r.token)
            AuthPrefs.writeUsername(context, username)
            if (!r.role.isNullOrBlank()) AuthPrefs.writeRole(context, r.role)
            _state.value = AuthState.SignedIn
            Log.d(TAG, "login: success, state=SignedIn, role=${r.role ?: "<none>"}")
            // 修复防御:同上,显式 Unit 保证 Result<Unit>
            Unit
        }.onFailure { e ->
            // [修复防御]: 把异常类型完整打出来 —— 之前只打 ApiException 的 status,其他一律 <n/a>,
            // 导致 ConnectException / UnknownHostException / SSLHandshakeException 等被掩盖成 "登录失败"。
            // 诊断 "浏览器能访问,APP 连不上" 时,异常链就是关键线索。
            val status = (e as? top.mcxiafeng.badger.network.ApiException)?.status
            Log.w(TAG, "login: failed status=${status ?: "<n/a>"} type=${e.javaClass.name} msg=${e.message}")
            _state.value = AuthState.Error(e.message ?: "login failed")
        }
    }

    suspend fun fetchMe(): JsonObject? = runCatching {
        withContext(Dispatchers.IO) { serverApiFactory.get().me() }
    }.getOrElse { e ->
        // 修复防御:不吞掉根因,记录异常类型便于排查"个人页加载失败"类问题
        Log.w(TAG, "fetchMe: failed ${e.javaClass.simpleName}: ${e.message}")
        null
    }

    suspend fun logout() {
        Log.d(TAG, "logout: enter")
        runCatching { withContext(Dispatchers.IO) { serverApiFactory.get().logout() } }
            .onSuccess { Log.d(TAG, "logout: server revoke OK") }
            .onFailure { e ->
                // 修复防御:server revoke 失败不应阻塞本地清凭证,但要记录原因
                Log.w(TAG, "logout: server revoke failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        tokenHolder.set(null)
        AuthPrefs.clearAuth(context)
        _state.value = AuthState.SignedOut
        Log.d(TAG, "logout: cleared local auth, state=SignedOut")
    }

    /** Current access JWT, used by ServerApi directly (not via interceptor). */
    fun currentToken(): String? = tokenHolder.get()

    private fun onNewAccessToken(t: String) {
        tokenHolder.set(t)
        AuthPrefs.writeRefreshToken(context, t)
        // 修复防御:登录/刷新成功后 token 写入 prefs 与 TokenHolder 是异步落盘,
        // 这里打印"已写入 token holder + 写入 prefs"两条独立日志,便于排查
        // "登录成功但下次启动就掉登录" 这种 TokenHolder 与 prefs 不一致的问题。
        Log.d(TAG, "onNewAccessToken: tokenHolder updated, len=${t.length}; refresh token persisted")
    }
}

/**
 * Process-singleton factory + base-URL controller for [ServerApi].
 *
 * Why this exists:
 * - [ServerApi] is a single shared instance held by every repository /
 *   ViewModel / OCR shim in the app. Constructing it twice would split the
 *   base URL into two realities.
 * - The OkHttp + token holder form a circular dependency with [ServerApi]
 *   (the auth interceptor needs the holder, the holder is created before
 *   [ServerApi]). The factory resolves the loop by accepting an
 *   [install] call from [NetworkModule] after both sides exist.
 *
 * Hot-reload: [updateBaseUrl] is the only sanctioned way to change the
 * server URL at runtime. It pushes the new URL into the live [ServerApi]
 * (which already wrote to prefs via [AuthPrefs]) so subsequent requests
 * route to the new host without a process restart.
 */
@Singleton
class ServerApiFactory @Inject constructor() {
    @Volatile private var serverApi: ServerApi? = null
    @Volatile private var currentBaseUrl: String = ""

    /**
     * Hand the constructed [ServerApi] to the factory and remember the
     * initial base URL for change-detection in [updateBaseUrl]. Called
     * exactly once by [NetworkModule.provideOkHttpClient] at app start.
     */
    fun install(api: ServerApi, initialBaseUrl: String) {
        this.serverApi = api
        this.currentBaseUrl = initialBaseUrl
    }

    fun get(): ServerApi =
        serverApi ?: error("ServerApi not yet installed; NetworkModule must initialize first")

    /**
     * Hot-update the shared [ServerApi]'s base URL. No-op when the input
     * matches the current value (avoids unnecessary volatile writes).
     *
     * Callers MUST persist via [AuthPrefs.writeServerUrl] before invoking
     * this — the factory does not write to disk itself, so a kill between
     * "change URL in memory" and "persist" would silently revert on next
     * launch. [AccountSettingsViewModel] is the canonical caller and
     * already follows that order.
     */
    fun updateBaseUrl(newUrl: String) {
        val api = serverApi ?: error("ServerApi not yet installed")
        val normalized = newUrl.trim().trimEnd('/')
        if (normalized == currentBaseUrl) return
        currentBaseUrl = normalized
        api.setBaseUrl(normalized)
    }
}