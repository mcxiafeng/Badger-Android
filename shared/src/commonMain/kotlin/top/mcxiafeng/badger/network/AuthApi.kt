package top.mcxiafeng.badger.network

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import top.mcxiafeng.badger.utils.SafeLog
import top.mcxiafeng.badger.utils.BadgerLog

class AuthApi(private val core: ApiCore) {

    

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
        BadgerLog.d(TAG, "[$tag] register: user=${SafeLog.user(username)} email=${SafeLog.email(email)}")
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
            core.execute(core.request("POST", "/api/auth/register", payload.toString())).use { resp ->
                
                resp.unwrapApiResult("register", tag) {  }
                BadgerLog.d(TAG, "[$tag] register OK: code=200")
            }
        } catch (e: ApiException) {
            BadgerLog.w(TAG, "[$tag] register failed: code=${e.status} what=${e.what} body=${e.bodyText?.take(120)}")
            throw e
        }
    }

    

    fun login(username: String, password: String, deviceId: String? = null, deviceName: String? = null): AuthResponse {
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] login: user=${SafeLog.user(username)} passwordLen=${password.length} deviceId=${deviceId?.take(8) ?: "<none>"}")
        val payload = buildJsonObject {
            put("username", username)
            put("password", password)
            deviceId?.takeIf { it.isNotBlank() }?.let { put("deviceId", it) }
            deviceName?.takeIf { it.isNotBlank() }?.let { put("deviceName", it) }
        }
        return try {
            core.execute(core.request("POST", "/api/auth/login", payload.toString())).use { resp ->
                resp.unwrapApiResult("login", tag) { data ->
                    val obj = data as? JsonObject
                    if (obj == null) {
                        
                        throw ApiException(resp.code, data.toString().take(200), "login data not object")
                    }
                    val parsed = AuthResponse.ofLogin(obj)
                    
                    if (parsed.token.isBlank()) throw ApiException(resp.code, "login missing token", "login")
                    BadgerLog.d(TAG, "[$tag] login OK: tokenLen=${parsed.token.length} user=${SafeLog.user(parsed.user?.name)} isAdmin=${parsed.user?.isAdmin}")
                    parsed
                }
            }
        } catch (e: Exception) {
            
            
            
            when (e) {
                is ApiException -> {
                    BadgerLog.w(TAG, "[$tag] login failed: code=${e.status} what=${e.what}")
                }
                else -> {
                    var cur: Throwable? = e
                    var depth = 0
                    val chain = buildString {
                        while (cur != null && depth < 5) {
                            append(" -> [${cur::class.simpleName}] ${cur.message}")
                            cur = cur.cause
                            depth++
                        }
                    }
                    BadgerLog.w(TAG, "[$tag] login ${e::class.simpleName}: ${e.message} chain:$chain", e)
                }
            }
            throw e
        }
    }

    
    fun refresh(): AuthResponse {
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] refresh: issuing with current token")
        return try {
            core.execute(core.request("POST", "/api/auth/refresh")).use { resp ->
                resp.unwrapApiResult("refresh", tag) { data ->
                    val obj = data as? JsonObject
                    if (obj == null) {
                        throw ApiException(resp.code, data.toString().take(200), "refresh data not object")
                    }
                    
                    val token = (obj["token"] as? JsonPrimitive)?.content
                        ?: throw ApiException(resp.code, "refresh missing token", "refresh")
                    BadgerLog.d(TAG, "[$tag] refresh OK: tokenLen=${token.length}")
                    AuthResponse.ofToken(obj)
                }
            }
        } catch (e: ApiException) {
            BadgerLog.w(TAG, "[$tag] refresh failed: code=${e.status} what=${e.what}")
            throw e
        }
    }

    
    fun logout() {
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] logout: server-side revoke")
        try {
            core.execute(core.request("POST", "/api/auth/logout")).use { resp ->
                if (resp.code !in 200..299 && resp.code != 401) {
                    BadgerLog.w(TAG, "[$tag] logout non-2xx: code=${resp.code}")
                    throw ApiException(resp.code, resp.message, "logout")
                }
                BadgerLog.d(TAG, "[$tag] logout OK: code=${resp.code}")
            }
        } catch (e: ApiException) {
            BadgerLog.w(TAG, "[$tag] logout failed: code=${e.status} what=${e.what}")
            throw e
        }
    }

    
    fun me(): JsonObject? {
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] me: fetching profile")
        return try {
            core.execute(core.request("GET", "/api/auth/me")).use { resp ->
                resp.unwrapApiResult("me", tag) { data ->
                    when {
                        data is JsonNull -> {
                            BadgerLog.w(TAG, "[$tag] me OK but data null (contract violation)")
                            null
                        }
                        data !is JsonObject -> {
                            throw ApiException(resp.code, data.toString().take(200), "me data not object")
                        }
                        else -> {
                            val name = (data["name"] as? JsonPrimitive)?.content
                            BadgerLog.d(TAG, "[$tag] me OK: user=${SafeLog.user(name)}")
                            data
                        }
                    }
                }
            }
        } catch (e: ApiException) {
            BadgerLog.w(TAG, "[$tag] me failed: code=${e.status} what=${e.what}")
            throw e
        }
    }

    
    fun registerPolicy(): RegisterPolicy {
        val tag = core.nextCallTag()
        return try {
            core.execute(core.request("GET", "/api/auth/registerPolicy")).use { resp ->
                resp.unwrapApiResult("registerPolicy", tag) { data ->
                    val obj = data as? JsonObject
                        ?: throw ApiException(resp.code, "registerPolicy data not object", "registerPolicy")
                    RegisterPolicy.from(obj)
                }
            }
        } catch (e: ApiException) {
            BadgerLog.w(TAG, "[$tag] registerPolicy failed: code=${e.status} what=${e.what}")
            throw e
        }
    }

    
    fun getCaptcha(): CaptchaResult {
        val tag = core.nextCallTag()
        return try {
            core.execute(core.request("GET", "/api/auth/getCaptcha")).use { resp ->
                resp.unwrapApiResult("getCaptcha", tag) { data ->
                    val obj = data as? JsonObject
                        ?: throw ApiException(resp.code, "getCaptcha data not object", "getCaptcha")
                    CaptchaResult.from(obj)
                }
            }
        } catch (e: ApiException) {
            BadgerLog.w(TAG, "[$tag] getCaptcha failed: code=${e.status} what=${e.what}")
            throw e
        }
    }

    

    fun forgotPassword(
        email: String,
        captchaId: String,
        captchaCode: String,
        newPassword: String,
        newPasswordAgain: String,
    ) {
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] forgotPassword: email=${SafeLog.email(email)} captchaId=${captchaId.take(8)} newPwdLen=${newPassword.length}")
        val payload = buildJsonObject {
            put("email", email)
            put("captchaId", captchaId)
            put("captchaCode", captchaCode)
            put("newPassword", newPassword)
            put("newPasswordAgain", newPasswordAgain)
        }
        try {
            core.execute(core.request("POST", "/api/auth/forgotPassword", payload.toString())).use { resp ->
                resp.unwrapApiResult("forgotPassword", tag) {  }
                BadgerLog.d(TAG, "[$tag] forgotPassword OK: code=200")
            }
        } catch (e: ApiException) {
            BadgerLog.w(TAG, "[$tag] forgotPassword failed: code=${e.status} what=${e.what} body=${e.bodyText?.take(120)}")
            throw e
        }
    }

    
    fun sendVerificationCode(email: String, purpose: String): VerificationCodeResult {
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] sendVerificationCode: email=${SafeLog.email(email)} purpose=$purpose")
        val payload = buildJsonObject {
            put("email", email)
            put("purpose", purpose)
        }
        return try {
            core.execute(core.request("POST", "/api/auth/sendVerificationCode", payload.toString())).use { resp ->
                resp.unwrapApiResult("sendVerificationCode", tag) { data ->
                    val obj = data as? JsonObject
                        ?: throw ApiException(resp.code, "sendVerificationCode data not object", "sendVerificationCode")
                    VerificationCodeResult.from(obj)
                }
            }
        } catch (e: ApiException) {
            BadgerLog.w(TAG, "[$tag] sendVerificationCode failed: code=${e.status} what=${e.what}")
            throw e
        }
    }

    

    fun changePassword(oldPassword: String, newPassword: String, newPasswordAgain: String) {
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] changePassword")
        val payload = buildJsonObject {
            put("oldPassword", oldPassword)
            put("newPassword", newPassword)
            put("newPasswordAgain", newPasswordAgain)
        }
        try {
            core.execute(core.request("POST", "/api/auth/changePassword", payload.toString())).use { resp ->
                resp.unwrapApiResult("changePassword", tag) {  }
                BadgerLog.d(TAG, "[$tag] changePassword OK")
            }
        } catch (e: ApiException) {
            BadgerLog.w(TAG, "[$tag] changePassword failed: code=${e.status} what=${e.what}")
            throw e
        }
    }

    private companion object {
        const val TAG = ApiCore.TAG
    }
}
