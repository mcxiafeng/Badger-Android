package top.mcxiafeng.badger.network

import android.util.Log
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import top.mcxiafeng.badger.utils.SafeLog

/**
 * [Phase 2] Authentication endpoints（新 Java `/api` 契约，ApiResult 壳）。
 *
 * 与旧 Go `/v1` 契约差异（`Badger-Server/docs/api-handover.md` §3）：
 * - 除两个代理外一律 `{code:200,message,data}` 壳，本类全部走 [Response.unwrapApiResult]；
 * - login 响应 `{token, user:{uuid,name,displayName,email,isAdmin,profile,lastLogin,createTime}}`，
 *   可选传 `deviceId/deviceName` 触发服务端设备登记 upsert；
 * - register 响应 `data:null`（**不返回 token**）——客户端注册成功后需再 login 拿 token；
 * - refresh 返回 `data:{token}`，校验现有 token（无效 401）；
 * - me 字段名全变（username→name、role→isAdmin）。
 */
class AuthApi(private val core: ApiCore) {

    /**
     * POST /api/auth/register
     * `{ username, email, password, passwordAgain, captchaId?, captchaCode?, emailCaptchaId?, emailCode? }`
     *
     * 是否要求图形/邮箱验证码由 `GET /api/auth/registerPolicy` 决定；本方法不关心，字段由调用方按策略填。
     * 成功返回 Unit（新契约 data 为 null，无 token），失败抛 [ApiException]。
     */
    fun register(
        username: String,
        email: String,
        password: String,
        passwordAgain: String,
        captchaId: String?,
        captchaCode: String?,
        emailCaptchaId: String?,
        emailCode: String?,
    ) {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] register: user=${SafeLog.user(username)} email=${SafeLog.email(email)}")
        val payload = buildJsonObject {
            put("username", username)
            put("email", email)
            put("password", password)
            put("passwordAgain", passwordAgain)
            captchaId?.takeIf { it.isNotBlank() }?.let { put("captchaId", it) }
            captchaCode?.takeIf { it.isNotBlank() }?.let { put("captchaCode", it) }
            emailCaptchaId?.takeIf { it.isNotBlank() }?.let { put("emailCaptchaId", it) }
            emailCode?.takeIf { it.isNotBlank() }?.let { put("emailCode", it) }
        }
        try {
            core.execute(core.buildRequest("POST", "/api/auth/register", payload.toString()).build()).use { resp ->
                // [修复防御]: 新契约注册成功 data=null —— 只消费外壳、不解析 data，避免 JsonNull 误伤。
                resp.unwrapApiResult("register", tag) { /* data: null */ }
                Log.d(TAG, "[$tag] register OK: code=200")
            }
        } catch (e: ApiException) {
            Log.w(TAG, "[$tag] register failed: code=${e.status} what=${e.what} body=${e.bodyText?.take(120)}")
            throw e
        }
    }

    /**
     * POST /api/auth/login `{ username, password, deviceId?, deviceName? }`
     * → `data: { token, user:{...} }`。
     *
     * [deviceId]/[deviceName] 可选：传 deviceId 时服务端 upsert 设备登记（多端同步设备列表数据源）。
     */
    fun login(username: String, password: String, deviceId: String? = null, deviceName: String? = null): AuthResponse {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] login: user=${SafeLog.user(username)} passwordLen=${password.length} deviceId=${deviceId?.take(8) ?: "<none>"}")
        val payload = buildJsonObject {
            put("username", username)
            put("password", password)
            deviceId?.takeIf { it.isNotBlank() }?.let { put("deviceId", it) }
            deviceName?.takeIf { it.isNotBlank() }?.let { put("deviceName", it) }
        }
        return try {
            core.execute(core.buildRequest("POST", "/api/auth/login", payload.toString()).build()).use { resp ->
                resp.unwrapApiResult("login", tag) { data ->
                    val obj = data as? JsonObject
                    if (obj == null) {
                        // [修复防御]: 契约违反 —— 登录成功必须给 data 对象，否则不透传脏数据
                        throw ApiException(resp.code, data.toString().take(200), "login data not object")
                    }
                    val parsed = AuthResponse.ofLogin(obj)
                    // [修复防御]: 契约违反 —— 登录成功必须带 token，否则不透传空会话
                    if (parsed.token.isBlank()) throw ApiException(resp.code, "login missing token", "login")
                    Log.d(TAG, "[$tag] login OK: tokenLen=${parsed.token.length} user=${SafeLog.user(parsed.user?.name)} isAdmin=${parsed.user?.isAdmin}")
                    parsed
                }
            }
        } catch (e: Exception) {
            // [修复防御]: 合并冗长 catch 链为单一 catch，根据异常类型记录不同日志详情
            when (e) {
                is java.net.ConnectException -> {
                    val reason = (e.cause as? java.net.SocketException)?.message ?: e.cause?.javaClass?.simpleName
                    Log.w(TAG, "[$tag] login ConnectException: msg=${e.message} reason=$reason", e)
                }
                is java.net.SocketTimeoutException -> {
                    Log.w(TAG, "[$tag] login SocketTimeoutException: msg=${e.message}", e)
                }
                is java.net.UnknownHostException -> {
                    Log.w(TAG, "[$tag] login UnknownHostException: msg=${e.message}", e)
                }
                is java.io.IOException -> {
                    var cur: Throwable? = e
                    var depth = 0
                    val chain = buildString {
                        while (cur != null && depth < 5) {
                            append(" -> [${cur.javaClass.name}] ${cur.message}")
                            cur = cur.cause
                            depth++
                        }
                    }
                    Log.w(TAG, "[$tag] login IOException chain:$chain", e)
                }
                is ApiException -> {
                    Log.w(TAG, "[$tag] login failed: code=${e.status} what=${e.what}")
                }
                else -> {
                    Log.w(TAG, "[$tag] login unexpected error: ${e.javaClass.simpleName}: ${e.message}", e)
                }
            }
            throw e
        }
    }

    /** POST /api/auth/refresh — 校验现有 token（无效/过期 401）后签发新 token，返回 `data:{token}`。 */
    fun refresh(): AuthResponse {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] refresh: issuing with current token")
        return try {
            core.execute(core.buildRequest("POST", "/api/auth/refresh").build()).use { resp ->
                resp.unwrapApiResult("refresh", tag) { data ->
                    val obj = data as? JsonObject
                    if (obj == null) {
                        throw ApiException(resp.code, data.toString().take(200), "refresh data not object")
                    }
                    // [K04] asString 语义平移：blank token 不算缺失（不抛），仅 null/缺失抛
                    val token = (obj["token"] as? JsonPrimitive)?.content
                        ?: throw ApiException(resp.code, "refresh missing token", "refresh")
                    Log.d(TAG, "[$tag] refresh OK: tokenLen=${token.length}")
                    AuthResponse.ofToken(obj)
                }
            }
        } catch (e: ApiException) {
            Log.w(TAG, "[$tag] refresh failed: code=${e.status} what=${e.what}")
            throw e
        }
    }

    /** POST /api/auth/logout — 新契约响应 `ApiResult.success(null)`；401 视为已登出，幂等。 */
    fun logout() {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] logout: server-side revoke")
        try {
            core.execute(core.buildRequest("POST", "/api/auth/logout").build()).use { resp ->
                if (resp.code !in 200..299 && resp.code != 401) {
                    Log.w(TAG, "[$tag] logout non-2xx: code=${resp.code}")
                    throw ApiException(resp.code, resp.message, "logout")
                }
                Log.d(TAG, "[$tag] logout OK: code=${resp.code}")
            }
        } catch (e: ApiException) {
            Log.w(TAG, "[$tag] logout failed: code=${e.status} what=${e.what}")
            throw e
        }
    }

    /** GET /api/auth/me → `data:{uuid,name,displayName,email,isAdmin,lastLogin}`；data 缺失返回 null。 */
    fun me(): JsonObject? {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] me: fetching profile")
        return try {
            core.execute(core.buildRequest("GET", "/api/auth/me").build()).use { resp ->
                resp.unwrapApiResult("me", tag) { data ->
                    when {
                        data is JsonNull -> {
                            Log.w(TAG, "[$tag] me OK but data null (contract violation)")
                            null
                        }
                        data !is JsonObject -> {
                            throw ApiException(resp.code, data.toString().take(200), "me data not object")
                        }
                        else -> {
                            val name = (data["name"] as? JsonPrimitive)?.content
                            Log.d(TAG, "[$tag] me OK: user=${SafeLog.user(name)}")
                            data
                        }
                    }
                }
            }
        } catch (e: ApiException) {
            Log.w(TAG, "[$tag] me failed: code=${e.status} what=${e.what}")
            throw e
        }
    }

    /** GET /api/auth/registerPolicy → `data:{allowRegister,requireCaptcha,requireEmailCode}`。 */
    fun registerPolicy(): RegisterPolicy {
        val tag = core.nextCallTag()
        return try {
            core.execute(core.buildRequest("GET", "/api/auth/registerPolicy").build()).use { resp ->
                resp.unwrapApiResult("registerPolicy", tag) { data ->
                    val obj = data as? JsonObject
                        ?: throw ApiException(resp.code, "registerPolicy data not object", "registerPolicy")
                    RegisterPolicy.from(obj)
                }
            }
        } catch (e: ApiException) {
            Log.w(TAG, "[$tag] registerPolicy failed: code=${e.status} what=${e.what}")
            throw e
        }
    }

    /** GET /api/auth/getCaptcha → `data:{captchaId, code}`（dev 下发明文 code）。 */
    fun getCaptcha(): CaptchaResult {
        val tag = core.nextCallTag()
        return try {
            core.execute(core.buildRequest("GET", "/api/auth/getCaptcha").build()).use { resp ->
                resp.unwrapApiResult("getCaptcha", tag) { data ->
                    val obj = data as? JsonObject
                        ?: throw ApiException(resp.code, "getCaptcha data not object", "getCaptcha")
                    CaptchaResult.from(obj)
                }
            }
        } catch (e: ApiException) {
            Log.w(TAG, "[$tag] getCaptcha failed: code=${e.status} what=${e.what}")
            throw e
        }
    }

    /**
     * POST /api/auth/forgotPassword `{email, captchaId, captchaCode, newPassword, newPasswordAgain}`
     *
     * 重置密码：先通过 [sendVerificationCode]（purpose="forgotPassword"）获取 captchaId + captchaCode，
     * 再调用本端点提交。成功返回 Unit（data 为 null），失败抛 [ApiException]。
     */
    fun forgotPassword(
        email: String,
        captchaId: String,
        captchaCode: String,
        newPassword: String,
        newPasswordAgain: String,
    ) {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] forgotPassword: email=${SafeLog.email(email)} captchaId=${captchaId.take(8)} newPwdLen=${newPassword.length}")
        val payload = buildJsonObject {
            put("email", email)
            put("captchaId", captchaId)
            put("captchaCode", captchaCode)
            put("newPassword", newPassword)
            put("newPasswordAgain", newPasswordAgain)
        }
        try {
            core.execute(core.buildRequest("POST", "/api/auth/forgotPassword", payload.toString()).build()).use { resp ->
                resp.unwrapApiResult("forgotPassword", tag) { /* data: null */ }
                Log.d(TAG, "[$tag] forgotPassword OK: code=200")
            }
        } catch (e: ApiException) {
            Log.w(TAG, "[$tag] forgotPassword failed: code=${e.status} what=${e.what} body=${e.bodyText?.take(120)}")
            throw e
        }
    }

    /** POST /api/auth/sendVerificationCode `{email, purpose}` → `{captchaId, emailSent}`（dev 回退附明文 code）。 */
    fun sendVerificationCode(email: String, purpose: String): VerificationCodeResult {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] sendVerificationCode: email=${SafeLog.email(email)} purpose=$purpose")
        val payload = buildJsonObject {
            put("email", email)
            put("purpose", purpose)
        }
        return try {
            core.execute(core.buildRequest("POST", "/api/auth/sendVerificationCode", payload.toString()).build()).use { resp ->
                resp.unwrapApiResult("sendVerificationCode", tag) { data ->
                    val obj = data as? JsonObject
                        ?: throw ApiException(resp.code, "sendVerificationCode data not object", "sendVerificationCode")
                    VerificationCodeResult.from(obj)
                }
            }
        } catch (e: ApiException) {
            Log.w(TAG, "[$tag] sendVerificationCode failed: code=${e.status} what=${e.what}")
            throw e
        }
    }

    /**
     * POST /api/auth/changePassword `{ oldPassword, newPassword, newPasswordAgain }`
     *
     * 修改当前用户密码。成功返回 Unit（data 为 null），失败抛 [ApiException]。
     * 旧密码错误 → 400，新密码不一致 → 400（服务端校验）。
     */
    fun changePassword(oldPassword: String, newPassword: String, newPasswordAgain: String) {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] changePassword")
        val payload = buildJsonObject {
            put("oldPassword", oldPassword)
            put("newPassword", newPassword)
            put("newPasswordAgain", newPasswordAgain)
        }
        try {
            core.execute(core.buildRequest("POST", "/api/auth/changePassword", payload.toString()).build()).use { resp ->
                resp.unwrapApiResult("changePassword", tag) { /* data: null */ }
                Log.d(TAG, "[$tag] changePassword OK")
            }
        } catch (e: ApiException) {
            Log.w(TAG, "[$tag] changePassword failed: code=${e.status} what=${e.what}")
            throw e
        }
    }

    private companion object {
        const val TAG = ApiCore.TAG
    }
}
