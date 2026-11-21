package top.mcxiafeng.badger.network.auth

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import top.mcxiafeng.badger.data.account.AccountStore
import top.mcxiafeng.badger.network.HttpResult
import top.mcxiafeng.badger.network.core.PublicApi
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** 正式 Android V1 认证；每次操作只使用一个固定地址和实例绑定。 */
@Singleton
class AuthApi @Inject constructor(
    client: OkHttpClient,
    private val sessionStore: AuthSessionStore,
    private val deviceIdentity: DeviceIdentityProvider,
    private val publicApi: PublicApi,
    private val accountStore: AccountStore,
) {
    // Authentication does not retry requests. Close idle sockets immediately so
    // a server-closed connection cannot prevent instance checks or revocation.
    private val authClient = client.newBuilder()
        .connectionPool(ConnectionPool(AUTH_MAX_IDLE_CONNECTIONS, AUTH_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS))
        .followRedirects(false).followSslRedirects(false)
        .cookieJar(CookieJar.NO_COOKIES)
        .authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE)
        .retryOnConnectionFailure(false).cache(null)
        .callTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
        .also { Log.d(TAG, "认证传输使用独立连接池，请求结束后释放空闲连接") }
    private val loginLock = Any()
    private var loginGeneration = 0L
    private data class Instance(val serverId: String, val dataEpoch: String)
    private data class LoginAttempt(val generation: Long, val baseUrl: String)

    /** 页面离开、换服务器或新提交立即作废旧登录，即便旧 HTTP 响应已经到达。 */
    fun cancelPendingLogin() = synchronized(loginLock) {
        loginGeneration++
        Log.d(TAG, "已作废在途登录")
    }

    suspend fun login(
        username: String,
        password: String,
        baseUrl: String = publicApi.serverUrl,
    ): HttpResult {
        val attempt = synchronized(loginLock) { LoginAttempt(++loginGeneration, baseUrl.trimEnd('/')) }
        Log.d(TAG, "开始实例绑定登录")
        try {
            require(AuthSession.validBaseUrl(attempt.baseUrl))
            checkAttempt(attempt)
            val descriptor = request(attempt.baseUrl, INSTANCE_PATH)
            if (descriptor is HttpResult.Failure) return descriptor
            val instance = parseInstance((descriptor as HttpResult.Success).body)
            checkAttempt(attempt)
            val result = request(attempt.baseUrl, LOGIN_PATH, jsonBody(
                "serverId" to instance.serverId, "dataEpoch" to instance.dataEpoch,
                "loginName" to username.trim(), "password" to password,
            ), mapOf(CLIENT_HEADER to ANDROID_CLIENT))
            if (result is HttpResult.Failure) return result
            val session = parseLogin((result as HttpResult.Success).body, instance, attempt.baseUrl, username.trim())
            commitLogin(attempt, session)
            return result
        } catch (e: CancellationException) {
            Log.d(TAG, "登录已取消")
            throw e
        } catch (e: Exception) {
            // JSON 解析异常可能包含原响应，不把异常正文和 token 写入日志。
            Log.e(TAG, "登录响应或会话保存无效 (${e.javaClass.simpleName})")
            return invalidResponse()
        }
    }

    private suspend fun checkAttempt(attempt: LoginAttempt) {
        currentCoroutineContext().ensureActive()
        synchronized(loginLock) {
            if (attempt.generation != loginGeneration || publicApi.serverUrl.trimEnd('/') != attempt.baseUrl) {
                throw CancellationException("登录任务已失效")
            }
        }
    }

    private suspend fun commitLogin(attempt: LoginAttempt, session: AuthSession) {
        val context = currentCoroutineContext()
        synchronized(loginLock) {
            context.ensureActive()
            if (attempt.generation != loginGeneration || publicApi.serverUrl.trimEnd('/') != attempt.baseUrl) {
                throw CancellationException("登录任务已失效")
            }
            try {
                sessionStore.save(session)
                accountStore.activateRemote(session.serverId, session.userUuid, session.dataEpoch, session.username, session.baseUrl)
            } catch (e: Exception) {
                Log.e(TAG, "账号激活失败 (${e.javaClass.simpleName})")
                sessionStore.clear()
                accountStore.switchToLocal()
                throw e
            }
            publicApi.recordSuccessBase(attempt.baseUrl)
            Log.d(TAG, "登录成功，已激活绑定账号")
        }
    }

    /** 离线也立即退出本机，账号数据库由 AccountStore 保留。远端撤销固定使用退出前的快照。 */
    suspend fun logout(expectedScopeKey: String? = null): HttpResult {
        currentCoroutineContext().ensureActive()
        val session = synchronized(loginLock) {
            if (expectedScopeKey != null && accountStore.current().key != expectedScopeKey) {
                Log.d(TAG, "旧空间退出请求已取消，不改变当前会话")
                return HttpResult.Failure(0, null, "账号已切换，退出请求已取消", HttpResult.ErrorType.OTHER)
            }
            loginGeneration++
            sessionStore.snapshot().also {
                try { sessionStore.clear() } finally { accountStore.switchToLocal() }
                Log.d(TAG, "已退出本机并切回本地账号")
            }
        } ?: return HttpResult.Success("")
        // Switching spaces destroys the caller's ViewModel. Keep only this bounded cleanup alive.
        return withContext(NonCancellable + Dispatchers.IO) {
            withTimeoutOrNull(LOGOUT_REVOKE_TIMEOUT_MS) { revokeSession(session) } ?: run {
                Log.e(TAG, "本机已退出，远端会话撤销超时，未确认成功")
                HttpResult.Failure(0, null, "本机已退出，远端会话撤销尚未确认", HttpResult.ErrorType.TIMEOUT)
            }
        }
    }

    private suspend fun revokeSession(session: AuthSession): HttpResult {
        val descriptor = request(session.baseUrl, INSTANCE_PATH)
        if (descriptor is HttpResult.Failure) return descriptor
        val instance = try { parseInstance((descriptor as HttpResult.Success).body) }
        catch (e: Exception) {
            Log.e(TAG, "退出前实例校验失败 (${e.javaClass.simpleName})")
            return invalidResponse()
        }
        if (instance.serverId != session.serverId || instance.dataEpoch != session.dataEpoch) {
            Log.e(TAG, "退出前实例或数据代次已改变，停止发送凭据")
            return invalidResponse()
        }
        return request(session.baseUrl, LOGOUT_PATH, jsonBody(
            "mutationId" to UUID.randomUUID().toString(), "dataEpoch" to session.dataEpoch,
        ), mapOf("Authorization" to "Bearer ${session.token}", CLIENT_HEADER to ANDROID_CLIENT))
    }

    // ExplicitRoutes 未注册旧注册/邮件找回路由；客户端保留入口的失败语义，不发送旧凭据体系请求。
    suspend fun register(username: String, email: String, password: String, passwordAgain: String, captchaCode: String? = null): HttpResult =
        unavailable("当前服务器不支持自助注册")

    suspend fun registerPolicy(): RegisterPolicy? = RegisterPolicy(allowRegister = false, requireCaptcha = false)
        .also { Log.d(TAG, "正式认证未开放自助注册") }

    suspend fun sendVerificationCode(email: String): HttpResult = unavailable("请联系管理员重置密码")

    suspend fun forgotPassword(email: String, verifyCode: String, newPassword: String, newPasswordAgain: String): HttpResult =
        unavailable("请联系管理员重置密码")

    /** 探测保留现有可达性语义；登录另外验证正式实例描述。 */
    suspend fun probeBaseUrl(url: String): HttpResult = request(url.trimEnd('/'), "")

    private fun parseInstance(body: String): Instance {
        val root = JsonParser.parseString(body).asJsonObject
        require(root.get("installed")?.let { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean } == true)
        require(supportsV1(root.getAsJsonObject("protocol")) && supportsV1(root.getAsJsonObject("schema")))
        return Instance(root.uuidField("serverId"), root.uuidField("dataEpoch"))
    }

    private fun supportsV1(range: JsonObject): Boolean {
        val min = range.counterField("min")
        val max = range.counterField("max")
        return min >= 0 && min <= PROTOCOL_VERSION && max >= PROTOCOL_VERSION
    }

    private fun parseLogin(body: String, instance: Instance, baseUrl: String, username: String): AuthSession {
        val root = JsonParser.parseString(body).asJsonObject
        val identity = root.getAsJsonObject("accountIdentity")
        val session = root.getAsJsonObject("session")
        require(identity.uuidField("serverId") == instance.serverId)
        require(identity.uuidField("dataEpoch") == instance.dataEpoch)
        validateIdentity(identity)
        return AuthSession(
            userUuid = identity.uuidField("userId"), token = session.stringField("token"), username = username,
            serverId = instance.serverId, dataEpoch = instance.dataEpoch, baseUrl = baseUrl,
            expiresAt = session.stringField("expiresAt"),
        ).also { require(it.isUsable(Instant.now())) }
    }

    private fun validateIdentity(identity: JsonObject) {
        identity.counterField("ownerRevision")
        val roles = identity.getAsJsonArray("roles").map { value ->
            require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
            value.asString
        }
        require(roles == listOf("user") || roles == listOf("user", "admin"))
        val selfCard = identity.getAsJsonObject("selfCard")
        selfCard.uuidField("id")
        selfCard.uuidField("profileId")
        require(selfCard.counterField("version") > 0L)
    }

    private suspend fun request(
        baseUrl: String,
        path: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): HttpResult {
        currentCoroutineContext().ensureActive()
        return try {
            require(AuthSession.validBaseUrl(baseUrl))
            val builder = Request.Builder().url((baseUrl + path).toHttpUrl())
            headers.forEach { (name, value) -> builder.header(name, value) }
            if (body == null) builder.get() else builder.post(body.toRequestBody(JSON_MEDIA_TYPE))
            awaitResult(authClient.newCall(builder.build())).also { Log.d(TAG, "认证请求结束 ${describe(it)}") }
        } catch (e: CancellationException) {
            Log.d(TAG, "认证请求已取消")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "认证请求创建失败 (${e.javaClass.simpleName})")
            invalidResponse()
        }
    }

    private suspend fun awaitResult(call: Call): HttpResult = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "认证网络请求失败 (${e.javaClass.simpleName})")
                val type = if (e is SocketTimeoutException) HttpResult.ErrorType.TIMEOUT else HttpResult.ErrorType.NETWORK
                continuation.resume(HttpResult.Failure(0, null, null, type))
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use {
                        val text = it.body?.string().orEmpty()
                        if (it.isSuccessful) HttpResult.Success(text)
                        else HttpResult.Failure(it.code, text, null, errorTypeOf(it.code))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "认证响应读取失败 (${e.javaClass.simpleName})")
                    HttpResult.Failure(0, null, null, HttpResult.ErrorType.NETWORK)
                }
                continuation.resume(result)
            }
        })
    }

    private fun jsonBody(vararg pairs: Pair<String, String>): String = JsonObject().apply {
        pairs.forEach { (key, value) -> addProperty(key, value) }
    }.toString()

    private fun invalidResponse(): HttpResult.Failure = HttpResult.Failure(0, null, null, HttpResult.ErrorType.OTHER)

    private fun unavailable(message: String): HttpResult.Failure {
        Log.d(TAG, "旧认证功能未开放")
        return HttpResult.Failure(0, null, message, HttpResult.ErrorType.OTHER)
    }

    private fun errorTypeOf(code: Int): HttpResult.ErrorType = when {
        code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN -> HttpResult.ErrorType.AUTH
        code == HTTP_TOO_MANY_REQUESTS -> HttpResult.ErrorType.RATE_LIMIT
        code in SERVER_ERROR_RANGE -> HttpResult.ErrorType.SERVER
        else -> HttpResult.ErrorType.OTHER
    }

    private fun describe(result: HttpResult): String = when (result) {
        is HttpResult.Success -> "success"
        is HttpResult.Failure -> "HTTP ${result.code} (${result.errorType})"
    }

    companion object {
        private const val TAG = "AuthApiTester"
        private const val INSTANCE_PATH = "/api/v1/instance"
        private const val LOGIN_PATH = "/api/v1/auth/login"
        private const val LOGOUT_PATH = "/api/v1/auth/logout"
        private const val CLIENT_HEADER = "X-Badger-Client"
        private const val ANDROID_CLIENT = "android"
        private const val PROTOCOL_VERSION = 1L
        private const val AUTH_MAX_IDLE_CONNECTIONS = 0
        // ConnectionPool requires a positive duration; zero idle slots close immediately.
        private const val AUTH_KEEP_ALIVE_SECONDS = 1L
        private const val REQUEST_TIMEOUT_SECONDS = 30L
        private const val LOGOUT_REVOKE_TIMEOUT_MS = 10_000L
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private val SERVER_ERROR_RANGE = 500..599
        private val JSON_MEDIA_TYPE = "application/json; charset=UTF-8".toMediaType()
        private val COUNTER_PATTERN = Regex("0|[1-9][0-9]*")

        private fun JsonObject.counterField(key: String): Long = stringField(key).also {
            require(COUNTER_PATTERN.matches(it))
        }.toLong()

        private fun JsonObject.stringField(key: String): String {
            val value = get(key)
            require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString)
            return value.asString.also { require(it.isNotBlank()) }
        }

        private fun JsonObject.uuidField(key: String): String = stringField(key).also { require(AuthSession.canonicalUuid(it)) }
    }
}

/** 保留现有表单接口；正式路由目前不开放自助注册。 */
data class RegisterPolicy(
    val allowRegister: Boolean = true,
    val requireCaptcha: Boolean = true,
    val requireEmailCode: Boolean = false,
) {
    companion object {
        private fun JsonObject.boolOr(key: String, default: Boolean): Boolean =
            if (has(key) && !get(key).isJsonNull) get(key).asBoolean else default

        fun from(data: JsonObject): RegisterPolicy = RegisterPolicy(
            allowRegister = data.boolOr("allowRegister", true),
            requireCaptcha = data.boolOr("requireCaptcha", true),
            requireEmailCode = data.boolOr("requireEmailCode", false),
        )
    }
}
