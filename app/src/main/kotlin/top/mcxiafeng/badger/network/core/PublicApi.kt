package top.mcxiafeng.badger.network.core

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import top.mcxiafeng.badger.network.HttpResult
import top.mcxiafeng.badger.network.auth.AuthSessionStore
import top.mcxiafeng.badger.utils.SafeLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全局服务器 API 配置 + 鉴权头构造（移植自 dev 分支 network/core/PublicApi）。
 *
 * token 唯一来源 = [AuthSessionStore]（登录时写入），本类不持有任何凭据。
 * serverUrl 来源 = [ServerAddressStore] 的激活地址（多入口管理，热更生效）。
 * 未登录直接抛错：调用方负责登录前置检查，
 * 请求不带鉴权头裸发只会把错误推迟到服务端 401，掩盖根因。
 */
@Singleton
class PublicApi @Inject constructor(
    private val client: OkHttpClient,
    private val addressStore: ServerAddressStore,
    private val sessionStore: AuthSessionStore,
) {

    /** 每次访问实时读 store，激活地址变更后立即对全部网络请求生效。 */
    val serverUrl: String
        get() = addressStore.activeUrl.value

    /** 最近一次连通成功的基础地址（探测/登录成功时更新），用于状态展示。 */
    @Volatile
    var lastSuccessBase: String? = null
        private set

    /** 探测/登录成功时记录最近可用地址。 */
    fun recordSuccessBase(base: String) {
        lastSuccessBase = base
    }

    /**
     * 对任意 base 发起 GET（地址测试/探测用，不读默认 serverUrl）。
     * 服务端以任意 HTTP 状态响应都算"有响应"；仅网络异常/DNS/超时（code=0）算不可达。
     */
    suspend fun getDirect(base: String, path: String): HttpResult {
        val url = base.trimEnd('/') + path
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        HttpResult.Success(text)
                    } else {
                        HttpResult.Failure(
                            code = response.code,
                            body = text,
                            message = null,
                            errorType = errorTypeOf(response.code),
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "getDirect failed: ${SafeLog.url(url)}", e)
                HttpResult.Failure(0, null, null, HttpResult.ErrorType.NETWORK)
            }
        }
    }

    private fun errorTypeOf(code: Int): HttpResult.ErrorType = when {
        code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN -> HttpResult.ErrorType.AUTH
        code == HTTP_TOO_MANY_REQUESTS -> HttpResult.ErrorType.RATE_LIMIT
        code in SERVER_ERROR_RANGE -> HttpResult.ErrorType.SERVER
        code in CLIENT_ERROR_RANGE -> HttpResult.ErrorType.OTHER
        else -> HttpResult.ErrorType.UNKNOWN
    }

    /** 每次请求实时构造鉴权头（读会话）；未登录抛 IllegalStateException。 */
    fun authHeaders(): Map<String, String> {
        val token = sessionStore.read()?.token
            ?: throw IllegalStateException("未登录：本地无活跃会话")
        return mapOf("Authorization" to "Bearer $token")
    }

    companion object {
        private const val TAG = "PublicApiTester"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private val SERVER_ERROR_RANGE = 500..599
        private val CLIENT_ERROR_RANGE = 400..499

        /** 地址连通性测试端点（服务端 InfoModule 提供）。 */
        const val INFO_PATH = "/api/info"
    }
}
