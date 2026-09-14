package top.mcxiafeng.badger.shared.net

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KtorSpikeClient(private val client: HttpClient = HttpClient()) {

    
    suspend fun getAndAssertOk(url: String): Int = withContext(Dispatchers.Default) {
        val response = client.get(url)
        check(response.status == HttpStatusCode.OK) { "GET $url → ${response.status}" }
        response.bodyAsText().length
    }

    
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
