package top.mcxiafeng.badger.shared

import android.provider.Settings
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.system.database.SystemDbHolder
import top.mcxiafeng.badger.data.system.entity.UserInfo
import top.mcxiafeng.badger.network.auth.AuthApi
import top.mcxiafeng.badger.network.core.PublicApi
import top.mcxiafeng.badger.platform.deviceIdentity
import top.mcxiafeng.badger.platform.initDeviceIdentity
import top.mcxiafeng.badger.utils.HttpResult
import top.mcxiafeng.badger.utils.SafeLog
import java.util.UUID

/**
 * AuthApi 全端点集成测试：直连运行中的 Badger-Server（PublicApi.serverUrl），验证真实契约。
 *
 * 环境前提（偏离时对应用例会红，属预期）：
 * - 服务器在线且 registerPolicy = 允许注册、免验证码、免邮箱验证码（t07 断言此前提）；
 * - SMTP 未开启（t08 断言 503；开启后该断言需改为 200）；
 * - 服务端登出只删 Device 行、不吊销 token 本体（2026-10-06 实测，t12 记录此行为）；
 * - 服务端校验注册时两次密码一致（t14 断言此前提）。
 * - 登录限流 10 次/5 分钟/IP——全类共 9 次登录（t10 打死端口不计），5 分钟内重跑会撞 429，稍等或重启服务器。
 *
 * 每次调用的日志 = 请求体 + 响应体全量 + 耗时；请求体由测试按 AuthApi 契约拼串（含密码原文，
 * 仅测试环境输出），响应体取自 HttpResult 原文。
 *
 * 用例按方法名升序串成生命周期链（注册→登录→改密→登出），整类运行；单跑中后段用例
 * 会因共享账号尚未注册而失败。
 * 运行：./gradlew :shared:testAndroidHostTest --tests "top.mcxiafeng.badger.shared.AuthApiTest"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AuthApiTest {

    companion object {
        /** 每次运行随机生成账号（跨 JVM 不冲突）；同 JVM 重跑时 t01 对"已被占用"放行并靠登录兜底。 */
        private val suffix = UUID.randomUUID().toString().replace("-", "").take(8)
        private val username = "badger_t_$suffix"
        private val email = "$suffix@badger-test.com"
        private const val INITIAL_PASSWORD = "BadgerTest123"
        private const val NEW_PASSWORD = "BadgerTest456"

        /** t04 改密后更新，t05/t12 登出用当前密码登录。 */
        private var currentPassword = INITIAL_PASSWORD
    }

    private val originalUrl = PublicApi.serverUrl

    @get:Rule
    val logRule = object : TestWatcher() {
        override fun starting(description: Description) {
            log("========== ${description.methodName} 开始 ==========")
        }

        override fun failed(e: Throwable, description: Description) {
            log("!!! ${description.methodName} 失败: ${e::class.simpleName}: ${e.message}")
        }

        override fun finished(description: Description) {
            log("---------- ${description.methodName} 结束 ----------")
        }
    }

    @Before
    fun setUp() {
        runBlocking { TestSession.ensureLoggedIn() }
        initDeviceIdentity(RuntimeEnvironment.getApplication())
        Settings.Secure.putString(
            RuntimeEnvironment.getApplication().contentResolver,
            Settings.Secure.ANDROID_ID,
            "test-device-id",
        )
    }

    @After
    fun tearDown() {
        TestSession.closeHolderDb()
        PublicApi.serverUrl = originalUrl
    }

    @Test
    fun `t01 注册并登录 - 新用户载体写入且游标从零`() = runTest {
        log("注册账号 user=$username email=$email")
        val registerResult = timed("register", registerRequestBody(username, email, INITIAL_PASSWORD, INITIAL_PASSWORD)) {
            AuthApi.register(username, email, INITIAL_PASSWORD, INITIAL_PASSWORD)
        }
        // 用户名跨 JVM 随机不会撞；同 JVM 重跑才会 400（已被占用），此时由登录兜底验证账号可用
        assertTrue("注册异常: ${describe(registerResult)}", registerResult is HttpResult.Success || (registerResult as? HttpResult.Failure)?.code == 400)

        val loginResult = timed("login", loginRequestBody(username, INITIAL_PASSWORD)) { AuthApi.login(username, INITIAL_PASSWORD) }
        assertTrue("登录失败: ${describe(loginResult)}", loginResult is HttpResult.Success)

        val carrier = carrier()!!
        log("载体写入: user=${carrier.userUuid} token=${SafeLog.token(carrier.token)} sync=${carrier.syncVersion} last=${carrier.lastSyncTime}")
        assertTrue(carrier.token.isNotBlank())
        // setUp 种的是 SEED 载体，登录成功后必须已被新用户覆盖
        assertNotEquals(TestSession.SEED_USER_UUID, carrier.userUuid)
        assertNotEquals(TestSession.SEED_TOKEN, carrier.token)
        assertEquals(0L, carrier.syncVersion)
        assertEquals(0L, carrier.lastSyncTime)
    }

    @Test
    fun `t02 登录同用户重登 - 保留同步游标`() = runTest {
        assertTrue(timed("首次 login", loginRequestBody(username, INITIAL_PASSWORD)) { AuthApi.login(username, INITIAL_PASSWORD) } is HttpResult.Success)
        val dao = SystemDbHolder.get().userInfoDao()
        val before = dao.getActiveUserInfo()!!

        log("人为改写游标: sync=7 last=99 (user=${before.userUuid})")
        dao.upsertUserInfo(UserInfo(before.userUuid, token = before.token, syncVersion = 7L, lastSyncTime = 99L))
        assertTrue(timed("重登 login", loginRequestBody(username, INITIAL_PASSWORD)) { AuthApi.login(username, INITIAL_PASSWORD) } is HttpResult.Success)

        val after = dao.getActiveUserInfo()!!
        log("重登后游标: sync=${after.syncVersion} last=${after.lastSyncTime}")
        assertEquals(7L, after.syncVersion)
        assertEquals(99L, after.lastSyncTime)
    }

    @Test
    fun `t03 登录密码错误 - 400 且不动载体`() = runTest {
        val result = timed("login(错误密码)", loginRequestBody(username, "WrongPassword99")) { AuthApi.login(username, "WrongPassword99") }

        assertTrue(result is HttpResult.Failure)
        assertEquals(400, (result as HttpResult.Failure).code)
        log("载体未被破坏: token=${SafeLog.token(carrier()!!.token)}")
        assertEquals(TestSession.SEED_TOKEN, carrier()!!.token)
    }

    @Test
    fun `t04 改密 - 旧密码失效新密码生效`() = runTest {
        assertTrue(timed("login(旧密码)", loginRequestBody(username, INITIAL_PASSWORD)) { AuthApi.login(username, INITIAL_PASSWORD) } is HttpResult.Success)

        val changeResult = timed("changePassword", changePasswordRequestBody(INITIAL_PASSWORD, NEW_PASSWORD, NEW_PASSWORD)) {
            AuthApi.changePassword(INITIAL_PASSWORD, NEW_PASSWORD, NEW_PASSWORD)
        }
        assertTrue("改密失败: ${describe(changeResult)}", changeResult is HttpResult.Success)
        log("改密完成，currentPassword 切换为 NEW_PASSWORD")

        val oldLogin = timed("login(旧密码复验)", loginRequestBody(username, INITIAL_PASSWORD)) { AuthApi.login(username, INITIAL_PASSWORD) }
        assertTrue("旧密码仍可登录", oldLogin is HttpResult.Failure && oldLogin.code == 400)

        val newLogin = timed("login(新密码)", loginRequestBody(username, NEW_PASSWORD)) { AuthApi.login(username, NEW_PASSWORD) }
        assertTrue("新密码登录失败: ${describe(newLogin)}", newLogin is HttpResult.Success)

        currentPassword = NEW_PASSWORD
    }

    @Test
    fun `t05 登出 - 清空本地载体`() = runTest {
        assertTrue(timed("login", loginRequestBody(username, currentPassword)) { AuthApi.login(username, currentPassword) } is HttpResult.Success)

        val logoutResult = timed("logout") { AuthApi.logout() }
        assertTrue("登出失败: ${describe(logoutResult)}", logoutResult is HttpResult.Success)
        assertNull("登出后载体应已清空", carrier())
    }

    @Test
    fun `t06 注册重复用户名 - 400 透传`() = runTest {
        val result = timed("register(重复用户名)", registerRequestBody(username, "dup$suffix@badger-test.com", INITIAL_PASSWORD, INITIAL_PASSWORD)) {
            AuthApi.register(username, "dup$suffix@badger-test.com", INITIAL_PASSWORD, INITIAL_PASSWORD)
        }

        assertTrue(result is HttpResult.Failure)
        assertEquals(400, (result as HttpResult.Failure).code)
    }

    @Test
    fun `t07 注册策略 - 解码三开关且为套件前提值`() = runTest {
        // GET /api/auth/registerPolicy 无请求体；registerPolicy() 只回解码结果，响应载荷即 policy 本身
        val policy = AuthApi.registerPolicy()

        // 套件依赖：允许注册 + 免验证码 + 免邮箱验证码（否则 t01 无法走通）
        assertNotNull(policy)
        assertTrue(policy!!.allowRegister)
        assertFalse(policy.requireCaptcha)
        assertFalse("套件前提：注册不要求邮箱验证码", policy.requireEmailCode)
        log("registerPolicy（GET 无请求体）响应载荷: $policy")
    }

    @Test
    fun `t08 发验证码 - dev 服务器未开 SMTP 透传 503`() = runTest {
        val result = timed("sendVerificationCode", sendCodeRequestBody(email)) { AuthApi.sendVerificationCode(email) }

        // 断言跟随 dev 服务器当前 SMTP 状态（未开启）；开启后应改为 Success
        assertTrue(result is HttpResult.Failure)
        val failure = result as HttpResult.Failure
        assertEquals(503, failure.code)
        assertEquals(HttpResult.ErrorType.SERVER, failure.errorType)
    }

    @Test
    fun `t09 忘记密码异常路径 - 不存在邮箱与未发码均 400`() = runTest {
        val ghostResult = timed(
            "forgotPassword(幽灵邮箱)",
            forgotPasswordRequestBody("ghost_$suffix@badger-test.com", "000000", NEW_PASSWORD, NEW_PASSWORD),
        ) {
            AuthApi.forgotPassword("ghost_$suffix@badger-test.com", "000000", NEW_PASSWORD, NEW_PASSWORD)
        }
        assertTrue(ghostResult is HttpResult.Failure && ghostResult.code == 400)

        // 真实邮箱但从未发过验证码（SMTP 关闭时永远发不出）
        val noCodeResult = timed("forgotPassword(真实邮箱无码)", forgotPasswordRequestBody(email, "000000", NEW_PASSWORD, NEW_PASSWORD)) {
            AuthApi.forgotPassword(email, "000000", NEW_PASSWORD, NEW_PASSWORD)
        }
        assertTrue(noCodeResult is HttpResult.Failure && noCodeResult.code == 400)
    }

    @Test
    fun `t10 网络失败 - 折叠为 NETWORK 类型`() = runTest {
        try {
            PublicApi.serverUrl = "http://127.0.0.1:9"
            val result = timed("login(死端口)", loginRequestBody(username, currentPassword)) { AuthApi.login(username, currentPassword) }
            assertTrue(result is HttpResult.Failure)
            val failure = result as HttpResult.Failure
            assertEquals(0, failure.code)
            assertEquals(HttpResult.ErrorType.NETWORK, failure.errorType)
        } finally {
            PublicApi.serverUrl = originalUrl
            log("serverUrl 已还原")
        }
    }

    @Test
    fun `t11 未登录调用鉴权端点 - 本地 fail-loud 抛 IllegalStateException`() = runTest {
        SystemDbHolder.get().userInfoDao().clearAllUserInfos()
        log("载体已清空，调 changePassword 应本地抛错而非裸发请求")

        // PublicApi.authHeaders() 契约：未登录直接抛，不把错误推迟到服务端 401
        val thrown = runCatching { AuthApi.changePassword("old", "new", "new") }.exceptionOrNull()
        assertNotNull("未登录调用应抛异常，实际: $thrown", thrown)
        assertTrue("异常类型: ${thrown!!::class.simpleName}", thrown is IllegalStateException)
        log("fail-loud 按预期: ${thrown.message}")
        assertNull("载体应保持为空", carrier())
    }

    @Test
    fun `t12 登出后旧 token 调 me - 实测服务端不吊销 token 本体`() = runTest {
        assertTrue(timed("login", loginRequestBody(username, currentPassword)) { AuthApi.login(username, currentPassword) } is HttpResult.Success)
        val staleToken = carrier()!!.token
        log("已捕获旧 token: ${SafeLog.token(staleToken)}")

        assertTrue(timed("logout") { AuthApi.logout() } is HttpResult.Success)

        // 2026-10-06 实测：登出只删 Device 行，/me 带旧 token 仍 200——服务端鉴权不查 Device 表，
        // token 本体未吊销。若服务端后续改为登出即吊销 token，把此断言翻转为 401 / AUTH。
        val result = timed("GET /api/auth/me(旧 token)") {
            AuthApi.httpCore.get(PublicApi.serverUrl + "/api/auth/me", headers = mapOf("Authorization" to "Bearer $staleToken"))
        }
        assertTrue("登出后旧 token 应仍有效（当前契约）: ${describe(result)}", result is HttpResult.Success)
        log("旧 token 仍可调鉴权端点（服务端不吊销 token，安全侧需知悉）")
    }

    @Test
    fun `t13 登出网络失败 - 载体保留可重试`() = runTest {
        // setUp 已种子 SEED 载体；AuthApi 契约：logout 失败保留载体，等网络恢复重试
        PublicApi.serverUrl = "http://127.0.0.1:9"
        try {
            val before = carrier()!!
            val result = timed("logout(死端口)") { AuthApi.logout() }
            assertTrue(result is HttpResult.Failure)
            assertEquals(HttpResult.ErrorType.NETWORK, (result as HttpResult.Failure).errorType)

            val after = carrier()
            assertNotNull("logout 失败不得清载体", after)
            assertEquals(before.token, after!!.token)
            log("载体保留: token=${SafeLog.token(after.token)}")
        } finally {
            PublicApi.serverUrl = originalUrl
            log("serverUrl 已还原")
        }
    }

    @Test
    fun `t14 注册两次密码不一致 - 400 拒绝`() = runTest {
        // 契约前提：服务端校验 passwordAgain 与 password 一致；
        // 用独立用户名，防服务器未校验时意外建号污染 t01 主链账号
        val mismatchUser = "badger_m_$suffix"
        log("尝试注册 user=$mismatchUser（两次密码不同）")
        val result = timed("register(密码不一致)", registerRequestBody(mismatchUser, "mismatch$suffix@badger-test.com", INITIAL_PASSWORD, "Different456")) {
            AuthApi.register(mismatchUser, "mismatch$suffix@badger-test.com", INITIAL_PASSWORD, "Different456")
        }

        assertTrue("密码不一致应 400: ${describe(result)}", result is HttpResult.Failure)
        assertEquals(400, (result as HttpResult.Failure).code)
    }

    private fun log(message: String) = println("[AuthApiTest] $message")

    /** 单次调用一行日志：结果 + 耗时 + 请求体（测试按 AuthApi 契约拼串）+ 响应体全量。 */
    private suspend fun timed(label: String, requestBody: String? = null, block: suspend () -> HttpResult): HttpResult {
        val startAt = System.currentTimeMillis()
        val result = block()
        val requestPart = requestBody?.let { " 请求体: $it" }.orEmpty()
        log("$label → ${describe(result)}（${System.currentTimeMillis() - startAt}ms）$requestPart 响应体: ${responseBodyOf(result)}")
        return result
    }

    // —— 请求体打印串，按 AuthApi 各端点的 JSON 契约拼装（login 另含设备身份字段） ——

    private fun loginRequestBody(user: String, password: String): String {
        val identity = deviceIdentity()
        return "{\"username\":\"${user.trim()}\",\"password\":\"$password\",\"deviceId\":\"${identity.deviceId}\",\"deviceName\":\"${identity.deviceName}\"}"
    }

    private fun registerRequestBody(user: String, mail: String, pass: String, passAgain: String): String =
        "{\"username\":\"${user.trim()}\",\"email\":\"${mail.trim()}\",\"password\":\"$pass\",\"passwordAgain\":\"$passAgain\"}"

    private fun changePasswordRequestBody(old: String, new: String, newAgain: String): String =
        "{\"oldPassword\":\"$old\",\"newPassword\":\"$new\",\"newPasswordAgain\":\"$newAgain\"}"

    private fun sendCodeRequestBody(mail: String): String =
        "{\"email\":\"${mail.trim()}\"}"

    private fun forgotPasswordRequestBody(mail: String, code: String, new: String, newAgain: String): String =
        "{\"email\":\"${mail.trim()}\",\"verifyCode\":\"${code.trim()}\",\"newPassword\":\"$new\",\"newPasswordAgain\":\"$newAgain\"}"

    private fun describe(result: HttpResult): String = when (result) {
        is HttpResult.Success -> "success"
        is HttpResult.Failure -> "HTTP ${result.code} ${result.errorType}"
    }

    private fun responseBodyOf(result: HttpResult): String = when (result) {
        is HttpResult.Success -> result.body
        is HttpResult.Failure -> result.body ?: "(无响应体)"
    }

    private suspend fun carrier() = SystemDbHolder.get().userInfoDao().getActiveUserInfo()
}
