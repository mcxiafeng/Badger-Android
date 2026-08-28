package top.mcxiafeng.badger.network

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * [B3] DeviceApi 契约测试 —— 真实 OkHttp + [LocalHttpServer]。
 *
 * 覆盖：
 * - list 数组解析 + 缺 uuid 行跳过
 * - rename PUT 路径 + body
 * - delete 路径 + 404 幂等
 * - delete 403 不吞
 * - uuid 路径穿越拒绝
 */
class DeviceApiTest {

    private lateinit var server: LocalHttpServer
    private lateinit var api: DeviceApi

    @Before
    fun setUp() {
        server = LocalHttpServer().also { it.start() }
        api = DeviceApi(ApiCore(server.baseUrl, OkHttpClient(), { "tok" }))
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `list parses rows and skips missing uuid`() {
        server.enqueue(
            200,
            """{"code":200,"data":[""" +
                """{"uuid":"d-1","deviceId":"dev-aaa","deviceName":"Pixel 7","ip":"1.2.3.4","online":true,"loginTime":"2026-08-01T00:00:00Z"},""" +
                """{"deviceId":"no-uuid","deviceName":"skip me"},""" +
                """{"uuid":"d-2","deviceId":"dev-bbb","deviceName":"iPhone","ip":"5.6.7.8","online":false,"loginTime":1719900000000}""" +
                """]}""",
        )
        val rows = api.listDevices()
        assertThat(rows.map { it.uuid }).containsExactly("d-1", "d-2").inOrder()
        assertThat(rows[0].deviceName).isEqualTo("Pixel 7")
        assertThat(rows[0].online).isTrue()
        assertThat(rows[0].ip).isEqualTo("1.2.3.4")
        assertThat(rows[0].loginTime).isEqualTo("2026-08-01T00:00:00Z")
        assertThat(rows[1].online).isFalse()
        assertThat(rows[1].loginTime).isEqualTo("1719900000000")
        assertThat(server.lastPath.get()).isEqualTo("/api/user/devices")
    }

    @Test
    fun `list non-array data returns empty`() {
        server.enqueue(200, """{"code":200,"data":{"items":[]}}""")
        assertThat(api.listDevices()).isEmpty()
    }

    @Test
    fun `rename hits put path`() {
        server.enqueue(200, """{"code":200,"data":null}""")
        api.renameDevice("d-1", "new-name")
        assertThat(server.lastPath.get()).isEqualTo("/api/user/devices/d-1")
    }

    @Test
    fun `delete 404 is idempotent success`() {
        server.enqueue(404, """{"code":404,"message":"not found"}""")
        assertThat(api.deleteDevice("d-1")).isTrue()
    }

    @Test
    fun `delete 403 is not swallowed`() {
        server.enqueue(403, """{"code":403,"message":"forbidden"}""")
        try {
            api.deleteDevice("d-1")
            error("should have thrown")
        } catch (e: ApiException) {
            assertThat(e.status).isEqualTo(403)
        }
    }

    @Test
    fun `delete success returns true`() {
        server.enqueue(200, """{"code":200,"data":null}""")
        assertThat(api.deleteDevice("d-1")).isTrue()
        assertThat(server.lastPath.get()).isEqualTo("/api/user/devices/d-1")
    }

    @Test
    fun `uuid with slash is rejected before request`() {
        try {
            api.renameDevice("../x", "bad")
            error("should have thrown")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("uuid")
        }
        assertThat(server.requestCount.get()).isEqualTo(0)
    }

    @Test
    fun `uuid with question mark is rejected`() {
        try {
            api.deleteDevice("a?b=c")
            error("should have thrown")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("uuid")
        }
        assertThat(server.requestCount.get()).isEqualTo(0)
    }
}
