package top.mcxiafeng.badger.network.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import top.mcxiafeng.badger.data.system.database.SystemDbHolder
import top.mcxiafeng.badger.data.system.entity.UserInfo
import top.mcxiafeng.badger.network.core.PublicApi
import top.mcxiafeng.badger.platform.deviceIdentity
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.utils.HttpResult
import top.mcxiafeng.badger.utils.KtorHttpCore
import kotlin.uuid.Uuid

/** 服务端 AuthModule 契约；登录态载体 = System 库 UserInfo（login 写入 / logout 清除）。 */
object AuthApi {

    private const val TAG = "AuthApi"
    private val json = Json { ignoreUnknownKeys = true }

    /** 测试用 MockEngine 替换此传输（androidHostTest 与主源集同模块，可写 internal）。 */
    internal var httpCore = KtorHttpCore()

    suspend fun login(username: String, password: String): HttpResult {
        val identity = deviceIdentity()
        val loginResult = postJson(
            "/api/auth/login",
            buildJsonObject {
                put("username", username.trim())
                put("password", password)
                put("deviceId", identity.deviceId)
                put("deviceName", identity.deviceName)
            }.toString(),
        )
        val token = dataOf(loginResult)?.get("token")?.jsonPrimitive?.content ?: return loginResult

        val userUuid = getRaw("/api/auth/me", mapOf("Authorization" to "Bearer $token"))
            ?.get("uuid")?.jsonPrimitive?.content?.let { Uuid.parse(it) }
        if (userUuid == null) {
            BadgerLog.e(TAG, "login 后拉取 /me 失败，无法写入载体")
            return HttpResult.Failure(0, null, null,HttpResult.ErrorType.OTHER)
        }

        val dao = SystemDbHolder.get().userInfoDao()
        val previous = dao.getUserInfo(userUuid)
        dao.clearAllUserInfos()
        dao.upsertUserInfo(
            UserInfo(
                userUuid = userUuid,
                token = token,
                syncVersion = previous?.syncVersion ?: 0L,
                lastSyncTime = previous?.lastSyncTime ?: 0L,
            )
        )
        BadgerLog.d(TAG, "login 成功，载体已写入（user=$userUuid, 游标=${previous?.syncVersion ?: 0L}）")
        return loginResult
    }

    /** 成功不返回 token，注册完需再 login；captchaCode 空白不发（服务端 hasEmpty 拒空串字段）。 */
    suspend fun register(
        username: String,
        email: String,
        password: String,
        passwordAgain: String,
        captchaCode: String? = null,
    ): HttpResult = postJson(
        "/api/auth/register",
        buildJsonObject {
            put("username", username.trim())
            put("email", email.trim())
            put("password", password)
            put("passwordAgain", passwordAgain)
            captchaCode?.takeIf { it.isNotBlank() }?.let { put("captchaCode", it) }
        }.toString(),
    )

    suspend fun changePassword(oldPassword: String, newPassword: String, newPasswordAgain: String): HttpResult =
        postJson(
            "/api/auth/changePassword",
            buildJsonObject {
                put("oldPassword", oldPassword)
                put("newPassword", newPassword)
                put("newPasswordAgain", newPasswordAgain)
            }.toString(),
            PublicApi.authHeaders(),
        )

    /** 服务端删 Device 行；成功才清本地载体，失败保留可重试。 */
    suspend fun logout(): HttpResult {
        val result = postJson("/api/auth/logout", body = "", headers = PublicApi.authHeaders())
        if (result is HttpResult.Success) {
            SystemDbHolder.get().userInfoDao().clearAllUserInfos()
            BadgerLog.d(TAG, "logout 本地载体已清空")
        }
        return result
    }

    suspend fun registerPolicy(): RegisterPolicy? {
        val data = getRaw("/api/auth/registerPolicy") ?: return null
        return try {
            json.decodeFromJsonElement<RegisterPolicy>(data)
                .also { BadgerLog.d(TAG, "registerPolicy：allow=${it.allowRegister} captcha=${it.requireCaptcha}") }
        } catch (e: Exception) {
            BadgerLog.e(TAG, "registerPolicy 解析失败", e)
            null
        }
    }

    /** 服务端限流 5 次/10 分钟/IP；未配 SMTP 返回 503。 */
    suspend fun sendVerificationCode(email: String): HttpResult =
        postJson("/api/auth/sendVerificationCode", buildJsonObject { put("email", email.trim()) }.toString())

    /** 先 sendVerificationCode 收邮件验证码，再四字段一起提交。 */
    suspend fun forgotPassword(
        email: String,
        verifyCode: String,
        newPassword: String,
        newPasswordAgain: String,
    ): HttpResult = postJson(
        "/api/auth/forgotPassword",
        buildJsonObject {
            put("email", email.trim())
            put("verifyCode", verifyCode.trim())
            put("newPassword", newPassword)
            put("newPasswordAgain", newPasswordAgain)
        }.toString(),
    )

    private suspend fun postJson(path: String, body: String, headers: Map<String, String>? = null): HttpResult {
        val result = httpCore.post(PublicApi.serverUrl + path, body = body, headers = headers)
        BadgerLog.d(TAG, "$path → ${describe(result)}")
        return result
    }

    private suspend fun getRaw(path: String, headers: Map<String, String>? = null): JsonObject? {
        val result = httpCore.get(PublicApi.serverUrl + path, headers = headers)
        if (result is HttpResult.Failure) {
            BadgerLog.w(TAG, "$path 失败：${describe(result)}")
            return null
        }
        return dataOf(result).also { if (it == null) BadgerLog.w(TAG, "$path 响应缺 data 对象") }
    }

    private fun dataOf(result: HttpResult): JsonObject? =
        (result as? HttpResult.Success)?.let { Json.parseToJsonElement(it.body).jsonObject["data"]?.jsonObject }

    private fun describe(result: HttpResult): String = when (result) {
        is HttpResult.Success -> "success"
        is HttpResult.Failure -> "HTTP ${result.code} (${result.errorType})"
    }
}

/** GET /api/auth/registerPolicy 的 data 载荷（默认值对齐服务端 SettingsStore）。 */
@Serializable
data class RegisterPolicy(
    val allowRegister: Boolean = true,
    val requireCaptcha: Boolean = true,
    val requireEmailCode: Boolean = false,
)
