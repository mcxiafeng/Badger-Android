package top.mcxiafeng.badger.network

import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * 进程内 HTTP server，用来替代 MockWebServer。
 *
 * 用 [Executors.newSingleThreadExecutor] 处理单连接，
 * 每次请求取队头的 [MockResponse] 应答；队空则返回 500。
 * 通过 [ServerSocket] 绑 0 端口 → 拿到真实可访问的 URL，
 * 让 OkHttp 走完整栈（TLS 跳过是因为明文 http://localhost）。
 *
 * [lastBody] 记录最近一次请求的 body，用于断言 POST 时携带的字段。
 *
 * 从 [ContactNetworkResolverTest] 提取为共享测试工具，供
 * [ApiCoreUnwrapTest] 等传输层测试复用。
 */
class LocalHttpServer {
    private var socket: ServerSocket? = null
    internal var localPort: Int = -1
    private var executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "LocalHttpServer-Worker").apply { isDaemon = true }
    }
    private val responses = ArrayDeque<MockResponse>()
    val requestCount = AtomicInteger(0)
    val lastPath = AtomicReference<String>("")
    val lastBody = AtomicReference<String>("")

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
                        // 单连接异常不致命 —— 测试只关心响应内容，不关心协议严谨性
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
        val input = client.getInputStream()
        val headerText = readHeaderBlock(input)
        val requestLine = headerText.lineSequence().firstOrNull() ?: return
        // requestLine: "POST /api/resolve/ HTTP/1.1"
        val path = requestLine.split(' ').getOrNull(1) ?: ""
        requestCount.incrementAndGet()
        lastPath.set(path)
        val contentLength = headerText.lineSequence()
            .filter { it.lowercase().startsWith("content-length:") }
            .firstOrNull()
            ?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0
        if (contentLength > 0) {
            lastBody.set(readBodyBytes(input, contentLength))
        }
        val resp = synchronized(responses) { responses.removeFirstOrNull() }
            ?: MockResponse(500, """{"error":"no mock queued"}""")
        respond(client, resp)
    }

    /** 读到 \r\n\r\n 为止的 header 块（含终止 CRLF），按字节读避免 Reader 预读吞 body。 */
    private fun readHeaderBlock(input: java.io.InputStream): String {
        val headerBytes = ByteArrayOutputStream()
        var prev3 = -1
        var prev2 = -1
        var prev1 = -1
        var cur: Int
        while ((input.read().also { cur = it }) != -1) {
            headerBytes.write(cur)
            if (prev3 == '\r'.code && prev2 == '\n'.code && prev1 == '\r'.code && cur == '\n'.code) break
            prev3 = prev2
            prev2 = prev1
            prev1 = cur
        }
        return headerBytes.toString("UTF-8")
    }

    /** 按 Content-Length 字节数读 body 并以 UTF-8 解码（中文名等多字节 body 必须字节保真）。 */
    private fun readBodyBytes(input: java.io.InputStream, contentLength: Int): String {
        val body = ByteArray(contentLength)
        var off = 0
        while (off < contentLength) {
            val n = input.read(body, off, contentLength - off)
            if (n < 0) break
            off += n
        }
        return String(body, 0, off, Charsets.UTF_8)
    }

    private fun respond(client: java.net.Socket, resp: MockResponse) {
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
