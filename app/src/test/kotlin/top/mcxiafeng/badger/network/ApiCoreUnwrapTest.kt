package top.mcxiafeng.badger.network

import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonElement
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * [Phase 1] ApiResult 壳解析（[Response.unwrapApiResult]）行为测试。
 *
 * 对应回归清单「逆向/边缘验证」第一条：
 * - ApiResult 非 200（500/400/404/业务 code≠200）时正确抛 [ApiException]、不透传脏数据
 * - `data` 缺失 / 为 null → 降级 JsonNull（DELETE 等端点合法空 data）
 * - 非法 JSON / 非对象 body → 抛 [ApiException]（契约违反暴露而非掩盖）
 *
 * 走真实 OkHttp 栈 + 进程内 [LocalHttpServer]，与 ContactNetworkResolverTest 同基建。
 */
class ApiCoreUnwrapTest {

    private lateinit var server: LocalHttpServer
    private lateinit var core: ApiCore

    @Before
    fun setUp() {
        server = LocalHttpServer().also { it.start() }
        core = ApiCore(server.baseUrl, OkHttpClient(), { null })
    }

    @After
    fun tearDown() {
        server.stop()
    }

    /** 发一次 GET + unwrapApiResult，返回 onData 收到的元素。 */
    private fun unwrapData(status: Int, body: String): JsonElement {
        server.enqueue(status, body)
        val resp = core.execute(core.buildRequest("GET", "/api/test").build())
        return resp.unwrapApiResult("test.unwrap", "test-tag") { it }
    }

    @Test
    fun `2xx with code 200 passes data object through`() {
        val data = unwrapData(200, """{"code":200,"message":"ok","data":{"token":"abc"}}""")
        assertThat(data.isJsonObject).isTrue()
        assertThat(data.asJsonObject.get("token").asString).isEqualTo("abc")
    }

    @Test
    fun `2xx with data array passes array through`() {
        val data = unwrapData(200, """{"code":200,"message":"ok","data":[1,2,3]}""")
        assertThat(data.isJsonArray).isTrue()
        assertThat(data.asJsonArray).hasSize(3)
    }

    @Test
    fun `http 500 throws ApiException with status 500`() {
        server.enqueue(500, """{"error":"boom"}""")
        val resp = core.execute(core.buildRequest("GET", "/api/test").build())
        try {
            resp.unwrapApiResult("test.unwrap", "test-tag") { it }
            error("should have thrown")
        } catch (e: ApiException) {
            assertThat(e.status).isEqualTo(500)
        }
    }

    @Test
    fun `http 404 throws ApiException with status 404`() {
        server.enqueue(404, """{"error":"not found"}""")
        val resp = core.execute(core.buildRequest("GET", "/api/test").build())
        try {
            resp.unwrapApiResult("test.unwrap", "test-tag") { it }
            error("should have thrown")
        } catch (e: ApiException) {
            assertThat(e.status).isEqualTo(404)
        }
    }

    @Test
    fun `business code 400 on http 200 throws ApiException with message`() {
        server.enqueue(200, """{"code":400,"message":"bad request","data":null}""")
        val resp = core.execute(core.buildRequest("GET", "/api/test").build())
        try {
            resp.unwrapApiResult("test.unwrap", "test-tag") { it }
            error("should have thrown")
        } catch (e: ApiException) {
            assertThat(e.status).isEqualTo(400)
            assertThat(e.bodyText).isEqualTo("bad request")
        }
    }

    @Test
    fun `missing data field degrades to JsonNull`() {
        val data = unwrapData(200, """{"code":200,"message":"ok"}""")
        assertThat(data.isJsonNull).isTrue()
    }

    @Test
    fun `data null degrades to JsonNull`() {
        val data = unwrapData(200, """{"code":200,"message":"ok","data":null}""")
        assertThat(data.isJsonNull).isTrue()
    }

    @Test
    fun `malformed json throws ApiException`() {
        server.enqueue(200, """not json at all""")
        val resp = core.execute(core.buildRequest("GET", "/api/test").build())
        try {
            resp.unwrapApiResult("test.unwrap", "test-tag") { it }
            error("should have thrown")
        } catch (e: ApiException) {
            assertThat(e.status).isEqualTo(200)
        }
    }

    @Test
    fun `non-object body throws ApiException`() {
        server.enqueue(200, """[1,2,3]""")
        val resp = core.execute(core.buildRequest("GET", "/api/test").build())
        try {
            resp.unwrapApiResult("test.unwrap", "test-tag") { it }
            error("should have thrown")
        } catch (e: ApiException) {
            assertThat(e.status).isEqualTo(200)
        }
    }
}
