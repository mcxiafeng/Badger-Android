package top.mcxiafeng.badger.shared.net

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [K02 spike] Ktor Client（Q2 裁决选型）最小验证面。
 * commonMain 仅依赖 ktor-client-core（无引擎）；引擎由平台注入：JVM/Android=CIO，iOS=Darwin。
 * K04 重建 ServerApi 时沿用此分层：请求语义在 common，引擎各端就位。
 */
class KtorSpikeClient(private val client: HttpClient = HttpClient()) {

    /** GET 断言 200 并返回 body 长度 */
    suspend fun getAndAssertOk(url: String): Int = withContext(Dispatchers.Default) {
        val response = client.get(url)
        check(response.status == HttpStatusCode.OK) { "GET $url → ${response.status}" }
        response.bodyAsText().length
    }

    /** POST JSON body（对齐 ServerApi 的手写 POST 语义） */
    suspend fun postEcho(url: String, jsonBody: String): String = withContext(Dispatchers.Default) {
        val response = client.post(url) {
            setBody(jsonBody)
        }
        val statusCode = response.status.value
        check(statusCode in SUCCESS_RANGE) { "POST $url → ${response.status}" }
        response.bodyAsText()
    }

    fun close() = client.close()

    companion object {
        private const val SUCCESS_MIN = 200
        private const val SUCCESS_MAX = 299
        private val SUCCESS_RANGE = SUCCESS_MIN..SUCCESS_MAX
    }
}
