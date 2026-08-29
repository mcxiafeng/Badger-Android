package top.mcxiafeng.badger.network

import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * [B1] NotificationApi 契约测试 —— 真实 OkHttp + [LocalHttpServer]。
 *
 * 覆盖：
 * - unread-count 解析 / 缺字段降级 0
 * - list 数组解析 + 缺 uuid 行跳过
 * - markAsRead / delete 路径
 * - delete 404 幂等
 * - uuid 路径穿越拒绝
 */
class NotificationApiTest {

    private lateinit var server: LocalHttpServer
    private lateinit var api: NotificationApi

    @Before
    fun setUp() {
        server = LocalHttpServer().also { it.start() }
        api = NotificationApi(ApiCore(server.baseUrl, OkHttpClient(), { "tok" }))
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `unreadCount parses data unread`() {
        server.enqueue(200, """{"code":200,"data":{"unread":7}}""")
        assertThat(api.getUnreadCount()).isEqualTo(7)
        assertThat(server.lastPath.get()).isEqualTo("/api/user/notifications/unread-count")
    }

    @Test
    fun `unreadCount missing unread degrades to 0`() {
        server.enqueue(200, """{"code":200,"data":{}}""")
        assertThat(api.getUnreadCount()).isEqualTo(0)
    }

    @Test
    fun `unreadCount null data degrades to 0`() {
        server.enqueue(200, """{"code":200,"data":null}""")
        assertThat(api.getUnreadCount()).isEqualTo(0)
    }

    @Test
    fun `list parses rows and skips missing uuid`() {
        server.enqueue(
            200,
            """{"code":200,"data":[""" +
                """{"uuid":"n-1","senderName":"admin","title":"t","body":"b","read":false,"createTime":"2026-08-01T00:00:00Z","entityType":"person","entityId":10},""" +
                """{"senderName":"x","title":"no-id"},""" +
                """{"uuid":"n-2","senderName":"sys","title":"t2","body":"","read":true,"createTime":1719900000000}""" +
                """]}""",
        )
        val rows = api.listNotifications()
        assertThat(rows.map { it.uuid }).containsExactly("n-1", "n-2").inOrder()
        assertThat(rows[0].senderName).isEqualTo("admin")
        assertThat(rows[0].read).isFalse()
        assertThat(rows[0].createTime).isEqualTo("2026-08-01T00:00:00Z")
        // [C4] entityType/entityId 解析
        assertThat(rows[0].entityType).isEqualTo("person")
        assertThat(rows[0].entityId).isEqualTo(10L)
        assertThat(rows[1].read).isTrue()
        assertThat(rows[1].createTime).isEqualTo("1719900000000")
        // [C4] 缺 entityType/entityId → null
        assertThat(rows[1].entityType).isNull()
        assertThat(rows[1].entityId).isNull()
        assertThat(server.lastPath.get()).isEqualTo("/api/user/notifications")
    }

    @Test
    fun `list non-array data returns empty`() {
        server.enqueue(200, """{"code":200,"data":{"items":[]}}""")
        assertThat(api.listNotifications()).isEmpty()
    }

    @Test
    fun `markAsRead hits put read path`() {
        server.enqueue(200, """{"code":200,"data":null}""")
        api.markAsRead("n-1")
        assertThat(server.lastPath.get()).isEqualTo("/api/user/notifications/n-1/read")
    }

    @Test
    fun `delete 404 is idempotent success`() {
        server.enqueue(404, """{"code":404,"message":"not found"}""")
        assertThat(api.delete("n-1")).isTrue()
    }

    @Test
    fun `delete 403 is not swallowed`() {
        server.enqueue(403, """{"code":403,"message":"forbidden"}""")
        try {
            api.delete("n-1")
            error("should have thrown")
        } catch (e: ApiException) {
            assertThat(e.status).isEqualTo(403)
        }
    }

    @Test
    fun `uuid with slash is rejected before request`() {
        try {
            api.markAsRead("../x")
            error("should have thrown")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("uuid")
        }
        assertThat(server.requestCount.get()).isEqualTo(0)
    }

    @Test
    fun `parse createTime number and string`() {
        val str = JsonObject().apply {
            addProperty("uuid", "a")
            addProperty("createTime", "iso")
        }
        assertThat(UserNotification.parse(str)!!.createTime).isEqualTo("iso")
        val num = JsonObject().apply {
            addProperty("uuid", "b")
            addProperty("createTime", 123L)
        }
        assertThat(UserNotification.parse(num)!!.createTime).isEqualTo("123")
    }

    // [C4] entityType / entityId 解析
    @Test
    fun `parse entityType and entityId`() {
        val withEntity = JsonObject().apply {
            addProperty("uuid", "n-1")
            addProperty("entityType", "person")
            addProperty("entityId", 42L)
        }
        val parsed = UserNotification.parse(withEntity)!!
        assertThat(parsed.entityType).isEqualTo("person")
        assertThat(parsed.entityId).isEqualTo(42L)
    }

    @Test
    fun `parse missing entityType and entityId defaults to null`() {
        val noEntity = JsonObject().apply {
            addProperty("uuid", "n-2")
        }
        val parsed = UserNotification.parse(noEntity)!!
        assertThat(parsed.entityType).isNull()
        assertThat(parsed.entityId).isNull()
    }

    @Test
    fun `parse null entityType and entityId`() {
        val nullEntity = JsonObject().apply {
            addProperty("uuid", "n-3")
            add("entityType", com.google.gson.JsonNull.INSTANCE)
            add("entityId", com.google.gson.JsonNull.INSTANCE)
        }
        val parsed = UserNotification.parse(nullEntity)!!
        assertThat(parsed.entityType).isNull()
        assertThat(parsed.entityId).isNull()
    }
}
