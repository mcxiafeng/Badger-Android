package top.mcxiafeng.badger.data.repository

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.AuthPrefs
import top.mcxiafeng.badger.NetworkModule
import top.mcxiafeng.badger.network.AuthUser
import top.mcxiafeng.badger.network.RegisterPolicy
import top.mcxiafeng.badger.network.CaptchaResult
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.network.VerificationCodeResult
import top.mcxiafeng.badger.sync.DeviceIdProvider
import top.mcxiafeng.badger.utils.SafeLog

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

/**
 * [§14.2] Hilt `@Singleton @Inject constructor(@ApplicationContext ..., tokenHolder, serverApiFactory)` →
 * Koin `singleOf(::UserAuthRepository)`。`@ApplicationContext` 由 Koin 自动注入顶级 `Context` 依赖。
 * [Phase 2] 新增 [DeviceIdProvider] 依赖（Koin 已有 single，自动解析），用于登录时携带 deviceId 做设备登记。
 */
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
                // [Phase 2]: /me 返回新契约 data（uuid/name/displayName/email/isAdmin），
                // 顺带刷新本地 user 缓存，避免重启后 prefs 里是旧契约字段。
                persistUser(AuthUser.from(me))
                Log.d(TAG, "bootstrap: /me OK, state=SignedIn")
                _state.value = AuthState.SignedIn
            } else {
                Log.w(TAG, "bootstrap: /me returned null, clearing auth, state=SignedOut")
                tokenHolder.set(null)
                AuthPrefs.clearAuth(context)
                _state.value = AuthState.SignedOut
            }
        } catch (e: Exception) {
            // 修复防御:bootstrap 失败不能吞掉根因 —— 记录异常类型与消息,便于排查
            // "app 重启后莫名其妙掉登录" 这一类问题。token 本身仍然只在 prefs 里,
            // 这里不打印。
            Log.w(TAG, "bootstrap: /me threw ${e.javaClass.simpleName}: ${e.message}, clearing auth")
            tokenHolder.set(null)
            AuthPrefs.clearAuth(context)
            _state.value = AuthState.SignedOut
        }
    }

    /**
     * [Phase 2] 注册：新契约 register 成功只返回 `data:null`（无 token），
     * 因此注册成功后**自动 login** 拿 token + user，再进入 SignedIn。
     *
     * 若注册成功但自动登录失败（网络抖动），账号已建但本端未登录 —— 错误会冒泡给 UI，
     * 用户手动登录即可（注册成功不可重放，重试会收到"用户名已被占用"）。
     */
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
        Log.d(TAG, "register: enter user=${SafeLog.user(username)} email=${SafeLog.email(email)}")
        return runCatching {
            // [修复防御]: ServerApi.* 是普通函数,内部走 OkHttp 同步 execute()。
            // viewModelScope 默认 Main.immediate,直接调用会抛 NetworkOnMainThreadException。
            withContext(Dispatchers.IO) {
                serverApiFactory.get().register(
                    username, email, password, passwordAgain,
                    captchaId, captchaCode, emailCaptchaId, emailCode,
                )
            }
            Log.d(TAG, "register: register OK (no token), auto-login")
            val lr = withContext(Dispatchers.IO) {
                serverApiFactory.get().login(
                    username, password,
                    deviceId = deviceIdProvider.deviceId(),
                    deviceName = deviceName(),
                )
            }
            onNewAccessToken(lr.token)
            persistUser(lr.user)
            _state.value = AuthState.SignedIn
            Log.d(TAG, "register: success (auto-login), state=SignedIn, isAdmin=${lr.user?.isAdmin}")
            Unit
        }.onFailure { e ->
            // 修复防御:服务端常见错误(用户名重复 / 弱密码 / 验证码错误)会以 4xx 抛 ApiException;
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
            // 否则会抛 NetworkOnMainThreadException。这是 logcat 已确认的真凶。
            val r = withContext(Dispatchers.IO) {
                serverApiFactory.get().login(
                    username, password,
                    deviceId = deviceIdProvider.deviceId(),
                    deviceName = deviceName(),
                )
            }
            onNewAccessToken(r.token)
            persistUser(r.user)
            _state.value = AuthState.SignedIn
            Log.d(TAG, "login: success, state=SignedIn, isAdmin=${r.user?.isAdmin}")
            Unit
        }.onFailure { e ->
            // [修复防御]: 把异常类型完整打出来 —— 之前只打 ApiException 的 status,其他一律 <n/a>,
            // 导致 ConnectException / UnknownHostException / SSLHandshakeException 等被掩盖成 "登录失败"。
            val status = (e as? top.mcxiafeng.badger.network.ApiException)?.status
            Log.w(TAG, "login: failed status=${status ?: "<n/a>"} type=${e.javaClass.name} msg=${e.message}")
            _state.value = AuthState.Error(e.message ?: "login failed")
        }
    }

    /** [Phase 2] 拉注册策略（注册页据此决定是否显示图形/邮箱验证码）。 */
    suspend fun fetchRegisterPolicy(): RegisterPolicy = withContext(Dispatchers.IO) {
        serverApiFactory.get().registerPolicy()
    }

    /** [Phase 2] 取图形验证码（dev 下发明文 code 供展示）。 */
    suspend fun fetchCaptcha(): CaptchaResult = withContext(Dispatchers.IO) {
        serverApiFactory.get().getCaptcha()
    }

    /** [Phase 2] 发邮箱验证码（purpose=register/forgotPassword）。 */
    suspend fun sendVerificationCode(email: String, purpose: String): VerificationCodeResult =
        withContext(Dispatchers.IO) {
            serverApiFactory.get().sendVerificationCode(email, purpose)
        }

    /**
     * 重置密码：`POST /api/auth/forgotPassword`。
     * 需先调用 [sendVerificationCode]（purpose="forgotPassword"）获取 [captchaId] + [captchaCode]。
     * 成功静默返回，失败抛 [ApiException]。
     *
     * 注意：不返回 `Result<Unit>` —— MockK 泛型擦除导致 suspend fun 返回 Result 时
     * `coAnswers { Result.success(Unit) }` 在 `r.fold()` 处触发 ClassCastException。
     * ViewModel 层用 `runCatching` 兜底（与 signIn/register 模式一致）。
     */
    suspend fun forgotPassword(
        email: String,
        captchaId: String,
        captchaCode: String,
        newPassword: String,
        newPasswordAgain: String,
    ) {
        withContext(Dispatchers.IO) {
            serverApiFactory.get().forgotPassword(email, captchaId, captchaCode, newPassword, newPasswordAgain)
        }
        Log.d(TAG, "forgotPassword: success for email=${SafeLog.email(email)}")
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

    /**
     * [Phase 2] 把新契约 user 字段刷进 [AuthPrefs]，供设置页/个人页本地快照读取。
     * [user] 为 null（如 refresh 端点无 user）时静默跳过，不清已有缓存。
     */
    private fun persistUser(user: AuthUser?) {
        if (user == null) return
        if (user.uuid.isNotBlank()) AuthPrefs.writeUserId(context, user.uuid)
        if (user.name.isNotBlank()) AuthPrefs.writeUsername(context, user.name)
        user.displayName?.takeIf { it.isNotBlank() }?.let { AuthPrefs.writeDisplayName(context, it) }
        user.email?.takeIf { it.isNotBlank() }?.let { AuthPrefs.writeEmail(context, it) }
        AuthPrefs.writeIsAdmin(context, user.isAdmin)
        Log.d(TAG, "persistUser: uuid=${user.uuid.take(8)}... name=${SafeLog.user(user.name)} isAdmin=${user.isAdmin}")
    }

    /** 设备显示名（服务端 Device 行展示用）。 */
    private fun deviceName(): String {
        val s = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        return s.ifBlank { "Android" }
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
/**
 * [§14.2] Hilt `@Singleton @Inject constructor()` → Koin `single { ServerApiFactory() }`。
 * 无构造依赖,Koin 直接 new,工厂本身保持 volatile state 与原 Hilt 完全一致。
 */
class ServerApiFactory {
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