package top.mcxiafeng.badger.network.auth

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import top.mcxiafeng.badger.network.HttpResult
import top.mcxiafeng.badger.network.core.PublicApi
import top.mcxiafeng.badger.utils.SafeLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 服务端 AuthModule 契约（移植自 dev 分支 network/auth/AuthApi，传输层 Ktor→OkHttp）。
 * 登录态载体 = [AuthSessionStore]（login 写入 / logout 清除）。
 */
@Singleton
class AuthApi @Inject constructor(
    private val client: OkHttpClient,
    private val sessionStore: AuthSessionStore,
    private val deviceIdentity: DeviceIdentityProvider,
    private val publicApi: PublicApi,
) {

    suspend fun login(username: String, password: String): HttpResult = withContext(Dispatchers.IO) {
        val identity = deviceIdentity.identity()
        val loginResult = postJson(
            "/api/auth/login",
            jsonBody(
                "username" to username.trim(),
                "password" to password,
                "deviceId" to identity.deviceId,
                "deviceName" to identity.deviceName,
            ),
        )
        val token = dataOf(loginResult)?.stringField("token") ?: return@withContext loginResult

        val userUuid = getRaw("/api/auth/me", mapOf("Authorization" to "Bearer $token"))
            ?.stringField("uuid")
        if (userUuid == null) {
            Log.e(TAG, "login 后拉取 /me 失败，无法写入载体")
            return@withContext HttpResult.Failure(0, null, null, HttpResult.ErrorType.OTHER)
        }

        val previous = sessionStore.read()
        sessionStore.save(
            AuthSession(
                userUuid = userUuid,
                token = token,
                username = username.trim(),
                syncVersion = previous?.syncVersion ?: 0L,
                lastSyncTime = previous?.lastSyncTime ?: 0L,
            )
        )
        Log.d(TAG, "login 成功，载体已写入（user=$userUuid, 游标=${previous?.syncVersion ?: 0L}）")
        publicApi.recordSuccessBase(publicApi.serverUrl)
        loginResult
    }

    /** 成功不返回 token，注册完需再 login；captchaCode 空白不发（服务端 hasEmpty 拒空串字段）。 */
    suspend fun register(
        username: String,
        email: String,
        password: String,
        passwordAgain: String,
        captchaCode: String? = null,
    ): HttpResult {
        val body = JsonObject().apply {
            addProperty("username", username.trim())
            addProperty("email", email.trim())
            addProperty("password", password)
            addProperty("passwordAgain", passwordAgain)
            captchaCode?.takeIf { it.isNotBlank() }?.let { addProperty("captchaCode", it) }
        }
        val result = postJson("/api/auth/register", body.toString())
        if (result is HttpResult.Success) publicApi.recordSuccessBase(publicApi.serverUrl)
        return result
    }

    /** 服务端删 Device 行；成功才清本地载体，失败保留可重试。 */
    suspend fun logout(): HttpResult {
        val result = postJson("/api/auth/logout", body = "", headers = publicApi.authHeaders())
        if (result is HttpResult.Success) {
            sessionStore.clear()
            Log.d(TAG, "logout 本地载体已清空")
        }
        return result
    }

    suspend fun registerPolicy(): RegisterPolicy? {
        val data = getRaw("/api/auth/registerPolicy") ?: return null
        return try {
            RegisterPolicy.from(data)
                .also { Log.d(TAG, "registerPolicy：allow=${it.allowRegister} captcha=${it.requireCaptcha}") }
        } catch (e: Exception) {
            Log.e(TAG, "registerPolicy 解析失败", e)
            null
        }
    }

    /** 服务端限流 5 次/10 分钟/IP；未配 SMTP 返回 503。 */
    suspend fun sendVerificationCode(email: String): HttpResult =
        postJson("/api/auth/sendVerificationCode", jsonBody("email" to email.trim()))

    /** 先 sendVerificationCode 收邮件验证码，再四字段一起提交。 */
    suspend fun forgotPassword(
        email: String,
        verifyCode: String,
        newPassword: String,
        newPasswordAgain: String,
    ): HttpResult = postJson(
        "/api/auth/forgotPassword",
        jsonBody(
            "email" to email.trim(),
            "verifyCode" to verifyCode.trim(),
            "newPassword" to newPassword,
            "newPasswordAgain" to newPasswordAgain,
        ),
    )

    /**
     * 连通性探测：GET base URL 本身（不拼 /api 路径）。
     * 服务端以任意 HTTP 状态响应（含 404）都算可达；只有网络异常/DNS/超时（code=0）算不可达。
     */
    suspend fun probeBaseUrl(url: String): HttpResult {
        val result = request(method = METHOD_GET, path = "", body = null, headers = null, absoluteUrl = url)
        Log.d(TAG, "probe ${SafeLog.url(url)} → ${describe(result)}")
        return result
    }

    // —— HTTP 传输 ——

    private suspend fun postJson(path: String, body: String, headers: Map<String, String>? = null): HttpResult {
        val result = request(method = METHOD_POST, path = path, body = body, headers = headers)
        Log.d(TAG, "$path → ${describe(result)}")
        return result
    }

    private suspend fun getRaw(path: String, headers: Map<String, String>? = null): JsonObject? {
        val result = request(method = METHOD_GET, path = path, body = null, headers = headers)
        if (result is HttpResult.Failure) {
            Log.w(TAG, "$path 失败：${describe(result)}")
            return null
        }
        return dataOf(result).also { if (it == null) Log.w(TAG, "$path 响应缺 data 对象") }
    }

    private suspend fun request(
        method: String,
        path: String,
        body: String?,
        headers: Map<String, String>?,
        absoluteUrl: String? = null,
    ): HttpResult = withContext(Dispatchers.IO) {
        val url = absoluteUrl ?: (publicApi.serverUrl + path)
        try {
            val builder = Request.Builder().url(url)
            headers?.forEach { (k, v) -> builder.header(k, v) }
            when (method) {
                METHOD_POST -> builder.post((body ?: "").toRequestBody(JSON_MEDIA_TYPE))
                else -> builder.get()
            }
            client.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    HttpResult.Success(text)
                } else {
                    HttpResult.Failure(
                        code = response.code,
                        body = text,
                        message = messageOf(text),
                        errorType = errorTypeOf(response.code),
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "request failed: ${SafeLog.url(url)}", e)
            HttpResult.Failure(0, null, null, HttpResult.ErrorType.NETWORK)
        }
    }

    private fun jsonBody(vararg pairs: Pair<String, String>): String {
        val obj = JsonObject()
        pairs.forEach { (k, v) -> obj.addProperty(k, v) }
        return obj.toString()
    }

    /** 服务端响应统一为 {"code","message","data"}；data 缺失/非对象时返回 null。 */
    private fun dataOf(result: HttpResult): JsonObject? =
        (result as? HttpResult.Success)?.let { success ->
            runCatching {
                val root = JsonParser.parseString(success.body).asJsonObject
                root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            }.getOrNull()
        }

    private fun messageOf(body: String): String? = runCatching {
        JsonParser.parseString(body).asJsonObject.get("message")
            ?.takeIf { it.isJsonPrimitive }?.asString
    }.getOrNull()

    private fun errorTypeOf(code: Int): HttpResult.ErrorType = when {
        code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN -> HttpResult.ErrorType.AUTH
        code == HTTP_TOO_MANY_REQUESTS -> HttpResult.ErrorType.RATE_LIMIT
        code in SERVER_ERROR_RANGE -> HttpResult.ErrorType.SERVER
        code in CLIENT_ERROR_RANGE -> HttpResult.ErrorType.OTHER
        else -> HttpResult.ErrorType.UNKNOWN
    }

    private fun describe(result: HttpResult): String = when (result) {
        is HttpResult.Success -> "success"
        is HttpResult.Failure -> "HTTP ${result.code} (${result.errorType})"
    }

    companion object {
        private const val TAG = "AuthApiTester"
        private const val METHOD_GET = "GET"
        private const val METHOD_POST = "POST"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private val SERVER_ERROR_RANGE = 500..599
        private val CLIENT_ERROR_RANGE = 400..499
        private val JSON_MEDIA_TYPE = "application/json; charset=UTF-8".toMediaType()

        private fun JsonObject.stringField(key: String): String? =
            get(key)?.takeIf { it.isJsonPrimitive }?.asString
    }
}

/** GET /api/auth/registerPolicy 的 data 载荷；默认值对齐服务端 SettingsStore。 */
data class RegisterPolicy(
    val allowRegister: Boolean = true,
    val requireCaptcha: Boolean = true,
    val requireEmailCode: Boolean = false,
) {
    companion object {
        private const val DEFAULT_ALLOW_REGISTER = true
        private const val DEFAULT_REQUIRE_CAPTCHA = true
        private const val DEFAULT_REQUIRE_EMAIL_CODE = false

        /** Gson 无 kotlinx 默认值语义：布尔字段缺省时手动回退。 */
        private fun JsonObject.boolOr(key: String, default: Boolean): Boolean =
            if (has(key) && !get(key).isJsonNull) get(key).asBoolean else default

        fun from(data: JsonObject): RegisterPolicy = RegisterPolicy(
            allowRegister = data.boolOr("allowRegister", DEFAULT_ALLOW_REGISTER),
            requireCaptcha = data.boolOr("requireCaptcha", DEFAULT_REQUIRE_CAPTCHA),
            requireEmailCode = data.boolOr("requireEmailCode", DEFAULT_REQUIRE_EMAIL_CODE),
        )
    }
}
