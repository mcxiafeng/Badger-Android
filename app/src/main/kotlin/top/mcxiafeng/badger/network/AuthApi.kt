package top.mcxiafeng.badger.network

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import top.mcxiafeng.badger.utils.SafeLog

/**
 * [§15 #19] Authentication endpoints (V2 §4 auth flow).
 */
class AuthApi(private val core: ApiCore) {

    /** POST /api/auth/register {username, password, email?, display_name?} */
    fun register(username: String, password: String, email: String?, displayName: String?): AuthResponse {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] register: user=${SafeLog.user(username)} email=${SafeLog.email(email)}")
        val payload = JsonObject().apply {
            addProperty("username", username)
            addProperty("password", password)
            email?.takeIf { it.isNotBlank() }?.let { addProperty("email", it) }
            displayName?.takeIf { it.isNotBlank() }?.let { addProperty("display_name", it) }
        }
        return try {
            core.execute(core.buildRequest("POST", "/api/auth/register", payload.toString()).build()).use { resp ->
                core.ensureOk(resp, "register")
                val obj = JsonParser.parseString(resp.body!!.string()).asJsonObject
                val tokenLen = obj.get("token").asString.length
                val usernameEcho = obj.get("username")?.takeIf { !it.isJsonNull }?.asString
                Log.d(TAG, "[$tag] register OK: code=${resp.code} user=${SafeLog.user(usernameEcho)} tokenLen=$tokenLen")
                AuthResponse(
                    token = obj.get("token").asString,
                    expiresIn = obj.get("expires_in")?.asInt ?: 0,
                    role = obj.get("role")?.takeIf { !it.isJsonNull }?.asString,
                    username = obj.get("username")?.takeIf { !it.isJsonNull }?.asString,
                )
            }
        } catch (e: ApiException) {
            Log.w(TAG, "[$tag] register failed: code=${e.status} what=${e.what}")
            throw e
        }
    }

    /** POST /api/auth/login {username, password} */
    fun login(username: String, password: String): AuthResponse {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] login: user=${SafeLog.user(username)} passwordLen=${password.length}")
        val payload = JsonObject().apply {
            addProperty("username", username)
            addProperty("password", password)
        }
        return try {
            core.execute(core.buildRequest("POST", "/api/auth/login", payload.toString()).build()).use { resp ->
                core.ensureOk(resp, "login")
                val obj = JsonParser.parseString(resp.body!!.string()).asJsonObject
                val tokenLen = obj.get("token").asString.length
                val roleEcho = obj.get("role")?.takeIf { !it.isJsonNull }?.asString
                Log.d(TAG, "[$tag] login OK: code=${resp.code} role=${roleEcho ?: "<none>"} tokenLen=$tokenLen")
                AuthResponse(
                    token = obj.get("token").asString,
                    expiresIn = obj.get("expires_in")?.asInt ?: 0,
                    role = obj.get("role")?.takeIf { !it.isJsonNull }?.asString,
                    username = obj.get("username")?.takeIf { !it.isJsonNull }?.asString,
                )
            }
        } catch (e: java.net.ConnectException) {
            Log.w(TAG, "[$tag] login ConnectException: msg=${e.message} reason=${(e.cause as? java.net.SocketException)?.message ?: e.cause?.javaClass?.simpleName}", e)
            throw e
        } catch (e: java.net.SocketTimeoutException) {
            Log.w(TAG, "[$tag] login SocketTimeoutException: msg=${e.message}", e)
            throw e
        } catch (e: java.net.UnknownHostException) {
            Log.w(TAG, "[$tag] login UnknownHostException: msg=${e.message}", e)
            throw e
        } catch (e: java.io.IOException) {
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
            throw e
        } catch (e: ApiException) {
            Log.w(TAG, "[$tag] login failed: code=${e.status} what=${e.what}")
            throw e
        }
    }

    /** POST /api/auth/refresh — server requires the current token. */
    fun refresh(): AuthResponse {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] refresh: issuing with current token")
        return try {
            core.execute(core.buildRequest("POST", "/api/auth/refresh").build()).use { resp ->
                core.ensureOk(resp, "refresh")
                val obj = JsonParser.parseString(resp.body!!.string()).asJsonObject
                val tokenLen = obj.get("token").asString.length
                Log.d(TAG, "[$tag] refresh OK: code=${resp.code} tokenLen=$tokenLen")
                AuthResponse(
                    token = obj.get("token").asString,
                    expiresIn = obj.get("expires_in")?.asInt ?: 0,
                    role = null,
                    username = null,
                )
            }
        } catch (e: ApiException) {
            Log.w(TAG, "[$tag] refresh failed: code=${e.status} what=${e.what}")
            throw e
        }
    }

    /** POST /api/auth/logout */
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

    /** GET /api/auth/me */
    fun me(): JsonObject {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] me: fetching profile")
        return try {
            core.execute(core.buildRequest("GET", "/api/auth/me").build()).use { resp ->
                core.ensureOk(resp, "me")
                val body = resp.body!!.string()
                val obj = JsonParser.parseString(body).asJsonObject
                val username = obj.get("username")?.takeIf { !it.isJsonNull }?.asString
                val role = obj.get("role")?.takeIf { !it.isJsonNull }?.asString
                Log.d(TAG, "[$tag] me OK: code=${resp.code} user=${SafeLog.user(username)} role=${role ?: "<none>"}")
                obj
            }
        } catch (e: ApiException) {
            Log.w(TAG, "[$tag] me failed: code=${e.status} what=${e.what}")
            throw e
        }
    }

    private companion object {
        const val TAG = ApiCore.TAG
    }
}
