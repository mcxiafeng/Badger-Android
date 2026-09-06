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

/**
 * [KMP K16] iOS 侧 token 刷新器（Android `NetworkModule.tokenRefreshInterceptor` 的语义平移）。
 *
 * 刷新调用走**裸 client**（无 401 刷新钩子），对齐 Android baseClient 防递归。
 * 语义（与 Android 逐条对齐）：
 * - 双重检查：进入互斥后 re-check holder，token 已被他人轮换 → 直接复用（返回 true 路径由调用方感知）；
 * - 服务端明确拒绝（非 2xx / ApiResult code≠200 / data.token 缺失）→ holder 仍是旧 token 则
 *   `holder.set(null)` + `AuthPrefs.clearAuth()`，返回 null；
 * - 网络瞬时故障（连接/DNS/超时）→ **保凭证**（不清除），返回 null（上层抛 401 语义）；
 * - 成功 → `holder.set(token)` + `AuthPrefs.writeRefreshToken(token)`，返回新 token。
 */
class IosTokenRefresher(engine: HttpClientEngine? = null) {

    private val client: HttpClient = if (engine != null) HttpClient(engine) else HttpClient(Darwin)
    private val refreshMutex = Mutex()

    /**
     * @param failedToken 触发 401 的旧 token（空串 = 请求本就未带 token，直接走服务端裁决路径）。
     * @return 新 token（可重试）或 null（不可重试；凭证可能已被清除或保留——见类注释）。
     */
    suspend fun refresh(failedToken: String, holder: TokenHolder): String? {
        val latest = holder.get()
        if (!failedToken.isBlank() && latest != null && latest != failedToken) {
            // 他人已刷新完成，直接复用（对齐 Android refreshLock 内的 latestToken 检查）
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
                // 网络不可达：保凭证（不清除），不可重试——对齐 Android catch 三连的语义
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
            // 空体无 Content-Type，对齐 Android "".toRequestBody(null)
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

    /** 服务端明确拒绝：holder 未被他人轮换时清除凭证（对齐 Android clearAuth 条件）。 */
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
