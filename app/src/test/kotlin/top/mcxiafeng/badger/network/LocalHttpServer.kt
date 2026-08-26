package top.mcxiafeng.badger.network

import java.io.BufferedReader
import java.io.InputStreamReader
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
        val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
        val requestLine = reader.readLine() ?: return
        // requestLine: "POST /api/resolve/ HTTP/1.1"
        val path = requestLine.split(' ').getOrNull(1) ?: ""
        requestCount.incrementAndGet()
        lastPath.set(path)
        // 读取 headers，记录 Content-Length 决定是否读 body
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
