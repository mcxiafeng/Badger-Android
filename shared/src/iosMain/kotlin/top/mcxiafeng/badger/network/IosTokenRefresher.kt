package top.mcxiafeng.badger.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.mcxiafeng.badger.data.prefs.AuthPrefs
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "IosTokenRefresher"

class IosTokenRefresher(engine: HttpClientEngine? = null) {

    private val client: HttpClient = if (engine != null) HttpClient(engine) else HttpClient(Darwin)
    private val refreshMutex = Mutex()

    

    suspend fun refresh(failedToken: String, holder: TokenHolder): String? {
        val latest = holder.get()
        if (!failedToken.isBlank() && latest != null && latest != failedToken) {
            
            BadgerLog.d(TAG, "refresh: token already rotated by concurrent call, reuse")
            return latest
        }
        return refreshMutex.withLock {
            val recheck = holder.get()
            if (!failedToken.isBlank() && recheck != null && recheck != failedToken) {
                return@withLock recheck
            }
            try {
                runRefresh(failedToken.ifBlank { recheck.orEmpty() }, holder)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                
                BadgerLog.w(TAG, "tokenRefresh: network unavailable, keeping auth: ${e::class.simpleName}: ${e.message}", e)
                null
            }
        }
    }

    private suspend fun runRefresh(currentToken: String, holder: TokenHolder): String? {
        val refreshUrl = AuthPrefs.readServerUrl().trimEnd('/')
        BadgerLog.d(TAG, "tokenRefresh: issuing with current token (len=${currentToken.length})")
        val response = client.post("$refreshUrl/api/auth/refresh") {
            header(HttpHeaders.Authorization, "Bearer $currentToken")
            
            setBody("")
            timeout { requestTimeoutMillis = REFRESH_TIMEOUT_MS }
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            BadgerLog.w(TAG, "refresh rejected by server: code=${response.status.value}")
            return reject(currentToken, holder)
        }
        val obj = try {
            BadgerJson.parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject
        } catch (e: Exception) {
            BadgerLog.w(TAG, "refresh: malformed JSON response", e)
            null
        } ?: return reject(currentToken, holder)
        val code = intOr(obj["code"], 0)
        if (code != 200) {
            BadgerLog.w(TAG, "refresh rejected by ApiResult code=$code msg=${stringOrNull(obj, "message")}")
            return reject(currentToken, holder)
        }
        val token = ((obj["data"] as? kotlinx.serialization.json.JsonObject)?.get("token") as? kotlinx.serialization.json.JsonPrimitive)?.content
        if (token.isNullOrBlank()) {
            BadgerLog.w(TAG, "refresh: data.token missing")
            return reject(currentToken, holder)
        }
        holder.set(token)
        AuthPrefs.writeRefreshToken(token)
        BadgerLog.d(TAG, "tokenRefresh OK: tokenLen=${token.length}")
        return token
    }

    
    private fun reject(failedToken: String, holder: TokenHolder): String? {
        if (holder.get() == failedToken) {
            holder.set(null)
            AuthPrefs.clearAuth()
        }
        return null
    }

    private companion object {
        const val REFRESH_TIMEOUT_MS = 15_000L
    }
}
