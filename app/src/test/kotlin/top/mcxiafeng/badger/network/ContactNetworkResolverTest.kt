package top.mcxiafeng.badger.network

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import io.mockk.unmockkAll
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * ContactNetworkResolver 端到端测试 —— 走服务端 `/v1/resolver/identify` 唯一入口。
 *
 * 旧版基于 5 个 extract 正则的用例（GitHub login / B站 UID / QQ 数字 / None）已废弃:
 * 客户端不再做 URL 解析。所有识别一律由服务端在 POST /v1/resolver/identify 上返回。
 *
 * 测试用一个进程内的 Java ServerSocket 模拟服务端,通过 [LocalHttpServer] 拿到一个
 * 真实的 http://127.0.0.1:port URL,再用一个直连此 URL 的 [ServerApi] 走完整 OkHttp
 * 栈。避免引入 MockWebServer 等第三方依赖。
 *
 * 覆盖 5 个核心 case:
 * 1. 任意 input → POST /v1/resolver/identify → IdentifyResponse(kind, ...)
 * 2. 服务端识别为 unknown → null fields,kind="unknown"
 * 3. 服务端 5xx → identify 返回 null
 * 4. 旧 getResultInfo 签名仍可用,内部委托给 identify
 * 5. getResultInfo type 参数兜底（服务端 unknown 但调用方已知）
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContactNetworkResolverTest {

    private lateinit var server: LocalHttpServer
    private lateinit var api: ServerApi

    @Before
    fun setUp() {
        server = LocalHttpServer().also { it.start() }
        // 直连本地 server 的 ServerApi —— 走完整 OkHttp 栈(同一个 tokenProvider 不
        // 带 Authorization,模拟未登录态;拦截器在生产中会注入 token,这里不需要)。
        api = ServerApi(
            baseUrl = server.baseUrl,
            http = OkHttpClient(),
            tokenProvider = { null },
        )
        ContactNetworkResolver.setContext(mockk(relaxed = true))
    }

    @After
    fun tearDown() {
        unmockkAll()
        server.stop()
    }

    @Test
    fun `identify returns server-provided kind and contact_map`() {
        server.enqueue(
            status = 200,
            body = """{"kind":"qq","name":"QQ用户12345","avatar_url":"https://q1.qlogo.cn/g?b=qq&nk=12345&s=100","signature":"sig","contact_map":{"qq":"12345"}}"""
        )

        val resp = ContactNetworkResolver.identifyWith(api, "12345")

        assertThat(resp).isNotNull()
        assertThat(resp!!.kind).isEqualTo("qq")
        assertThat(resp.name).isEqualTo("QQ用户12345")
        assertThat(resp.avatarUrl).isEqualTo("https://q1.qlogo.cn/g?b=qq&nk=12345&s=100")
        assertThat(resp.signature).isEqualTo("sig")
        assertThat(resp.contactMap).containsExactly("qq", "12345")
        // [修复防御]: 显式断言服务端只命中 identify 一条请求,且 body 携带 input 字段。
        // 防止有人误把旧 5 个 endpoint 之一恢复回来 —— 那样的话 kind 仍可能拼凑出来,
        // 但 path 不是 /v1/resolver/identify,识别主路径即偏离。
        assertThat(server.requestCount.get()).isEqualTo(1)
        assertThat(server.lastPath.get()).isEqualTo("/v1/resolver/identify")
        assertThat(server.lastBody.get()).contains("\"input\":\"12345\"")
    }

    @Test
    fun `identify returns kind unknown for unknown input`() {
        server.enqueue(
            status = 200,
            body = """{"kind":"unknown","name":null,"avatar_url":null,"signature":null,"contact_map":{}}"""
        )

        val resp = ContactNetworkResolver.identifyWith(api, "gibberish string")

        assertThat(resp).isNotNull()
        assertThat(resp!!.kind).isEqualTo("unknown")
        assertThat(resp.name).isNull()
        assertThat(resp.avatarUrl).isNull()
        assertThat(resp.contactMap).isEmpty()
    }

    @Test
    fun `identify returns null on server 5xx`() {
        server.enqueue(status = 500, body = """{"error":"server down"}""")

        val resp = ContactNetworkResolver.identifyWith(api, "https://github.com/octocat")

        assertThat(resp).isNull()
    }

    @Test
    fun `identify returns null on blank input without hitting server`() {
        // [修复防御]: 空 input 短路 —— 不应浪费一次 HTTP 请求,也是显式契约。
        val resp = ContactNetworkResolver.identifyWith(api, "")
        assertThat(resp).isNull()
        assertThat(server.requestCount.get()).isEqualTo(0)
    }

    @Test
    fun `getResultInfo delegates to identify and projects onto ContactType`() {
        // [修复防御]: 模拟一个完整 ContactType 链路 —— 服务端给出 kind="github",
        // getResultInfo 必须用 kindToContactType 把它投到 ContactType.GitHub,
        // 且 contact_map 透传给 UI。kindToContactType 是字符串到 UI 标签的固定映射。
        server.enqueue(
            status = 200,
            body = """{"kind":"github","name":"The Octocat","avatar_url":"https://avatars.githubusercontent.com/u/583231","signature":"bio","contact_map":{"github":"octocat"}}"""
        )

        val result = ContactNetworkResolver.getResultInfoInternal(api, "https://github.com/octocat")

        assertThat(result).isNotNull()
        assertThat(result!!.type).isEqualTo(ContactType.GitHub)
        assertThat(result.nickname).isEqualTo("The Octocat")
        assertThat(result.avatarUrl).isEqualTo("https://avatars.githubusercontent.com/u/583231")
        assertThat(result.contactMap).containsExactly("github", "octocat")
        assertThat(server.requestCount.get()).isEqualTo(1)
        assertThat(server.lastPath.get()).isEqualTo("/v1/resolver/identify")
    }

    @Test
    fun `getResultInfo uses kindToContactType when type hint omitted`() {
        server.enqueue(
            status = 200,
            body = """{"kind":"bilibili","name":"B站用户","avatar_url":null,"signature":null,"contact_map":{"bilibili":"99999"}}"""
        )

        val result = ContactNetworkResolver.getResultInfoInternal(api, "https://space.bilibili.com/99999")

        assertThat(result).isNotNull()
        assertThat(result!!.type).isEqualTo(ContactType.Bilibili)
    }
}

/**
 * 进程内 HTTP server,用来替代 MockWebServer。
 *
 * 用 [Executors.newSingleThreadExecutor] 处理单连接,
 * 每次请求取队头的 [MockResponse] 应答;队空则返回 500。
 * 通过 [ServerSocket] 绑 0 端口 → 拿到真实可访问的 URL,
 * 让 OkHttp 走完整栈(TLS 跳过是因为明文 http://localhost)。
 *
 * [lastBody] 记录最近一次请求的 body,用于断言 POST /v1/resolver/identify 时
 * 携带了 input 字段。
 */
private class LocalHttpServer {
    private var socket: ServerSocket? = null
    internal var localPort: Int = -1
    private var executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "LocalHttpServer-Worker").apply { isDaemon = true }
    }
    private val responses = ArrayDeque<MockResponse>()
    val requestCount = AtomicInteger(0)
    val lastPath = java.util.concurrent.atomic.AtomicReference<String>("")
    val lastBody = java.util.concurrent.atomic.AtomicReference<String>("")

    val baseUrl: String
        get() = "http://127.0.0.1:$localPort"

    fun start() {
        val sock = ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress())
        socket = sock
        localPort = sock.localPort
        executor.execute {
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val client = socket?.accept() ?: return@execute
                    try {
                        handle(client)
                    } catch (_: Throwable) {
                        // 单连接异常不致命 —— 测试只关心响应内容,不关心协议严谨性
                    } finally {
                        runCatching { client.close() }
                    }
                }
            } catch (_: Throwable) {
                // socket closed during shutdown
            }
        }
    }

    fun stop() {
        runCatching { socket?.close() }
        executor.shutdownNow()
    }

    fun enqueue(status: Int, body: String) {
        synchronized(responses) { responses.addLast(MockResponse(status, body)) }
    }

    private fun handle(client: java.net.Socket) {
        val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
        val requestLine = reader.readLine() ?: return
        // requestLine: "POST /v1/resolver/identify HTTP/1.1"
        val path = requestLine.split(' ').getOrNull(1) ?: ""
        requestCount.incrementAndGet()
        lastPath.set(path)
        // 读取 headers,记录 Content-Length 决定是否读 body
        var contentLength = 0
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            val lower = line.lowercase()
            if (lower.startsWith("content-length:")) {
                contentLength = lower.substringAfter(":").trim().toIntOrNull() ?: 0
            }
        }
        if (contentLength > 0) {
            val buf = CharArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val n = reader.read(buf, read, contentLength - read)
                if (n < 0) break
                read += n
            }
            lastBody.set(String(buf, 0, read))
        }
        val resp = synchronized(responses) { responses.removeFirstOrNull() }
            ?: MockResponse(500, """{"error":"no mock queued"}""")
        val out = client.getOutputStream()
        val payload = resp.body.toByteArray(Charsets.UTF_8)
        val headers = buildString {
            append("HTTP/1.1 ${resp.status} REASON\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${payload.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        out.write(headers.toByteArray(Charsets.UTF_8))
        out.write(payload)
        out.flush()
    }

    private data class MockResponse(val status: Int, val body: String)
}