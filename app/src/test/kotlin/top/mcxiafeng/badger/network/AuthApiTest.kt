package top.mcxiafeng.badger.network

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * [Phase 2] AuthApi 集成测试 —— 走真实 OkHttp 栈 + 进程内 [LocalHttpServer]。
 *
 * 覆盖新 Java /api 契约的关键路径：
 * - login 解析 `data:{token, user:{...}}` 并携带 deviceId/deviceName
 * - register 成功 `data:null`（不返回 token）不炸，业务 code 非 200 抛 [ApiException]
 * - refresh 解析 `data:{token}`
 * - me 返回 `data:{...}`、data=null 时返回 null
 * - registerPolicy / getCaptcha / sendVerificationCode 解析
 */
class AuthApiTest {

    private lateinit var server: LocalHttpServer
    private lateinit var api: AuthApi

    @Before
    fun setUp() {
        server = LocalHttpServer().also { it.start() }
        api = AuthApi(ApiCore(server.baseUrl, OkHttpApiTransport(OkHttpClient()), { null }))
    }

    @After
    fun tearDown() {
        server.stop()
    }

    // ========== login ==========

    private fun loginOkBody() =
        """{"code":200,"message":"ok","data":""" +
            """{"token":"tok-1","user":""" +
            """{"uuid":"u-1","name":"alice","displayName":"Alice","email":"alice@x.com","isAdmin":true,"profile":{},"lastLogin":"2026-08-01T00:00:00Z","createTime":"2026-08-01T00:00:00Z"}}}"""

    @Test
    fun `login parses token and full user from ApiResult data`() {
        server.enqueue(200, loginOkBody())
        val r = api.login("alice", "password123")
        assertThat(r.token).isEqualTo("tok-1")
        val u = r.user!!
        assertThat(u.uuid).isEqualTo("u-1")
        assertThat(u.name).isEqualTo("alice")
        assertThat(u.displayName).isEqualTo("Alice")
        assertThat(u.email).isEqualTo("alice@x.com")
        assertThat(u.isAdmin).isTrue()
        assertThat(u.profile).isNotNull()
        assertThat(u.lastLogin).isNotNull()
    }

    @Test
    fun `login sends deviceId and deviceName in body`() {
        server.enqueue(200, loginOkBody())
        api.login("alice", "password123", deviceId = "dev-abc", deviceName = "Pixel 8")
        assertThat(server.lastBody.get()).contains("\"deviceId\":\"dev-abc\"")
        assertThat(server.lastBody.get()).contains("\"deviceName\":\"Pixel 8\"")
        assertThat(server.lastPath.get()).isEqualTo("/api/auth/login")
    }

    @Test
    fun `login omits device fields when not provided`() {
        server.enqueue(200, loginOkBody())
        api.login("alice", "password123")
        assertThat(server.lastBody.get()).doesNotContain("deviceId")
        assertThat(server.lastBody.get()).doesNotContain("deviceName")
    }

    @Test
    fun `login http 400 surfaces server message via ApiException`() {
        server.enqueue(400, """{"code":400,"message":"用户名/邮箱或密码错误"}""")
        try {
            api.login("alice", "wrong")
            error("should have thrown")
        } catch (e: ApiException) {
            assertThat(e.status).isEqualTo(400)
            assertThat(e.bodyText).contains("密码错误")
        }
    }

    @Test
    fun `login missing token in data throws ApiException`() {
        server.enqueue(200, """{"code":200,"message":"ok","data":{"user":{}}}""")
        try {
            api.login("alice", "password123")
            error("should have thrown")
        } catch (e: ApiException) {
            assertThat(e.what).contains("login")
        }
    }

    @Test
    fun `login business code non-200 on http 200 throws`() {
        server.enqueue(200, """{"code":403,"message":"账号已被停用","data":null}""")
        try {
            api.login("alice", "password123")
            error("should have thrown")
        } catch (e: ApiException) {
            assertThat(e.status).isEqualTo(403)
            assertThat(e.bodyText).contains("停用")
        }
    }

    // ========== register ==========

    @Test
    fun `register success with data null does not throw`() {
        server.enqueue(200, """{"code":200,"message":"success","data":null}""")
        // 不应抛异常
        api.register(
            username = "newuser", email = "new@x.com",
            password = "password123", passwordAgain = "password123",
            captchaId = null, captchaCode = null, emailCaptchaId = null, emailCode = null,
        )
        assertThat(server.lastBody.get()).contains("\"passwordAgain\":\"password123\"")
        assertThat(server.lastBody.get()).contains("\"email\":\"new@x.com\"")
    }

    @Test
    fun `register sends captcha fields when provided`() {
        server.enqueue(200, """{"code":200,"message":"success","data":null}""")
        api.register(
            username = "newuser", email = "new@x.com",
            password = "password123", passwordAgain = "password123",
            captchaId = "cid-1", captchaCode = "K7P2", emailCaptchaId = null, emailCode = null,
        )
        assertThat(server.lastBody.get()).contains("\"captchaId\":\"cid-1\"")
        assertThat(server.lastBody.get()).contains("\"captchaCode\":\"K7P2\"")
    }

    @Test
    fun `register business error throws ApiException with server message`() {
        server.enqueue(400, """{"code":400,"message":"两次密码输入不一致"}""")
        try {
            api.register(
                username = "newuser", email = "new@x.com",
                password = "password123", passwordAgain = "password1234",
                captchaId = null, captchaCode = null, emailCaptchaId = null, emailCode = null,
            )
            error("should have thrown")
        } catch (e: ApiException) {
            assertThat(e.status).isEqualTo(400)
            assertThat(e.bodyText).contains("两次密码")
        }
    }

    @Test
    fun `register rejected captcha throws ApiException`() {
        server.enqueue(400, """{"code":400,"message":"验证码错误"}""")
        try {
            api.register(
                username = "newuser", email = "new@x.com",
                password = "password123", passwordAgain = "password123",
                captchaId = "cid-1", captchaCode = "WRONG", emailCaptchaId = null, emailCode = null,
            )
            error("should have thrown")
        } catch (e: ApiException) {
            assertThat(e.status).isEqualTo(400)
            assertThat(e.bodyText).contains("验证码")
        }
    }

    // ========== refresh ==========

    @Test
    fun `refresh parses data token only`() {
        server.enqueue(200, """{"code":200,"message":"ok","data":{"token":"tok-new"}}""")
        val r = api.refresh()
        assertThat(r.token).isEqualTo("tok-new")
        assertThat(r.user).isNull()
        assertThat(server.lastPath.get()).isEqualTo("/api/auth/refresh")
    }

    @Test
    fun `refresh http 401 throws ApiException`() {
        server.enqueue(401, """{"code":401,"message":"未登录或会话已过期"}""")
        try {
            api.refresh()
            error("should have thrown")
        } catch (e: ApiException) {
            assertThat(e.status).isEqualTo(401)
        }
    }

    // ========== me ==========

    @Test
    fun `me returns data object with user fields`() {
        server.enqueue(
            200,
            """{"code":200,"message":"ok","data":{"uuid":"u-1","name":"alice","displayName":"Alice","email":"alice@x.com","isAdmin":false,"lastLogin":"2026-08-01T00:00:00Z"}}""",
        )
        val me = api.me()
        assertThat(me).isNotNull()
        assertThat(me!!["name"]?.jsonPrimitive?.content).isEqualTo("alice")
        assertThat((me["isAdmin"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBooleanStrictOrNull()).isFalse()
    }

    @Test
    fun `me with data null returns null`() {
        server.enqueue(200, """{"code":200,"message":"ok","data":null}""")
        assertThat(api.me()).isNull()
    }

    // ========== registerPolicy / getCaptcha / sendVerificationCode ==========

    @Test
    fun `registerPolicy parses allow register and captcha flags`() {
        server.enqueue(
            200,
            """{"code":200,"message":"ok","data":{"allowRegister":true,"requireCaptcha":true,"requireEmailCode":false}}""",
        )
        val p = api.registerPolicy()
        assertThat(p.allowRegister).isTrue()
        assertThat(p.requireCaptcha).isTrue()
        assertThat(p.requireEmailCode).isFalse()
        assertThat(server.lastPath.get()).isEqualTo("/api/auth/registerPolicy")
    }

    @Test
    fun `getCaptcha parses captchaId and dev code`() {
        server.enqueue(200, """{"code":200,"message":"ok","data":{"captchaId":"cid-9","code":"K7P2"}}""")
        val c = api.getCaptcha()
        assertThat(c.captchaId).isEqualTo("cid-9")
        assertThat(c.code).isEqualTo("K7P2")
        assertThat(server.lastPath.get()).isEqualTo("/api/auth/getCaptcha")
    }

    @Test
    fun `sendVerificationCode smtp enabled returns captchaId and emailSent`() {
        server.enqueue(200, """{"code":200,"message":"ok","data":{"captchaId":"eid-1","emailSent":true}}""")
        val r = api.sendVerificationCode("alice@x.com", "register")
        assertThat(r.captchaId).isEqualTo("eid-1")
        assertThat(r.emailSent).isTrue()
        assertThat(r.code).isNull()
        assertThat(server.lastPath.get()).isEqualTo("/api/auth/sendVerificationCode")
        assertThat(server.lastBody.get()).contains("\"purpose\":\"register\"")
    }

    @Test
    fun `sendVerificationCode dev fallback returns plaintext code`() {
        server.enqueue(200, """{"code":200,"message":"ok","data":{"captchaId":"eid-2","code":"654321","emailSent":false}}""")
        val r = api.sendVerificationCode("alice@x.com", "register")
        assertThat(r.emailSent).isFalse()
        assertThat(r.code).isEqualTo("654321")
    }

    @Test
    fun `sendVerificationCode register closed returns 403 ApiException`() {
        server.enqueue(403, """{"code":403,"message":"注册功能已关闭"}""")
        try {
            api.sendVerificationCode("alice@x.com", "register")
            error("should have thrown")
        } catch (e: ApiException) {
            assertThat(e.status).isEqualTo(403)
            assertThat(e.bodyText).contains("已关闭")
        }
    }
}
