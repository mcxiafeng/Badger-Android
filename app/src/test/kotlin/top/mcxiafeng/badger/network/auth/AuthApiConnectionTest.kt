package top.mcxiafeng.badger.network.auth

import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.source
import org.junit.Before
import org.junit.Test
import top.mcxiafeng.badger.data.account.AccountStore
import top.mcxiafeng.badger.network.HttpResult
import top.mcxiafeng.badger.network.core.PublicApi
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** Real loopback HTTP/1.1; no interceptor supplies responses or simulates a network failure. */
class AuthApiConnectionTest {
    private lateinit var store: AuthSessionStore
    private lateinit var accounts: AccountStore
    private lateinit var publicApi: PublicApi
    private var currentUrl = ""

    @Before
    fun setup() {
        store = mockk(relaxed = true)
        accounts = mockk(relaxed = true)
        publicApi = mockk(relaxed = true)
        every { publicApi.serverUrl } answers { currentUrl }
    }

    @Test
    fun login_succeedsWhenServerHasClosedGeneralClientIdleConnection() = runBlocking {
        val saved = slot<AuthSession>()
        every { store.save(capture(saved)) } returns Unit
        withClosedGeneralConnection { api, server, events ->
            val result = api.login("alice", SYNTHETIC_PASSWORD, currentUrl)
            assertSuccess(result, server, events)
            assertThat(server.paths()).containsExactly(
                "GET /warmup", "GET /api/v1/instance", "POST /api/v1/auth/login",
            ).inOrder()
            assertThat(saved.captured.baseUrl).isEqualTo(currentUrl)
            assertThat(saved.captured.userUuid).isEqualTo(USER)
            assertThat(saved.captured.token).isEqualTo(TOKEN)
            verify(exactly = 1) { accounts.activateRemote(SERVER, USER, EPOCH, "alice", currentUrl) }
            verify(exactly = 1) { publicApi.recordSuccessBase(currentUrl) }
        }
    }

    @Test
    fun logout_reachesRevokeWhenServerHasClosedGeneralClientIdleConnection() = runBlocking {
        every { store.snapshot() } answers {
            AuthSession(USER, TOKEN, "alice", serverId = SERVER, dataEpoch = EPOCH,
                baseUrl = currentUrl, expiresAt = EXPIRY)
        }
        withClosedGeneralConnection { api, server, events ->
            val result = api.logout()
            assertSuccess(result, server, events)
            assertThat(server.paths()).containsExactly(
                "GET /warmup", "GET /api/v1/instance", "POST /api/v1/auth/logout",
            ).inOrder()
            verify(exactly = 1) { store.clear() }
            verify(exactly = 1) { accounts.switchToLocal() }
        }
    }

    private suspend fun withClosedGeneralConnection(
        action: suspend (AuthApi, IdleCloseServer, ConnectionEvents) -> Unit,
    ) {
        IdleCloseServer().use { server ->
            currentUrl = server.baseUrl
            val events = ConnectionEvents(server.closedAtNs)
            val client = OkHttpClient.Builder()
                .proxy(Proxy.NO_PROXY)
                .eventListener(events)
                .connectTimeout(WAIT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(WAIT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(WAIT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(WAIT_SECONDS, TimeUnit.SECONDS)
                .build()
            try {
                val api = AuthApi(client, store, mockk(relaxed = true), publicApi, accounts)
                client.newCall(Request.Builder().url("$currentUrl/warmup").build()).execute().use {
                    assertThat(it.body!!.string()).isEqualTo(WARMUP_BODY)
                }
                // The response has been consumed and released before the peer is allowed to close.
                assertThat(client.connectionPool.idleConnectionCount()).isEqualTo(1)
                // OkHttp 4.12 GETs skip extensive health checks, so a shared pool can reuse this FIN'd socket.
                server.closeIdleConnection()
                action(api, server, events)
            } finally {
                client.dispatcher.cancelAll()
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdownNow()
                check(client.dispatcher.executorService.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    "Synthetic auth client executor did not stop"
                }
            }
        }
    }

    private fun assertSuccess(result: HttpResult, server: IdleCloseServer, events: ConnectionEvents) {
        server.assertHealthy()
        val outcome = when (result) {
            is HttpResult.Success -> "success"
            is HttpResult.Failure -> "${result.errorType} (${result.code})"
        }
        val delayMs = events.closeToAuthMs.get()
        assertThat(delayMs).isAtLeast(0L)
        assertThat(delayMs).isLessThan(SHORT_IDLE_WINDOW_MS)
        // Only synthetic routes, local ports, timing and exception class names appear in failure output.
        assertWithMessage("Auth outcome=%s; close-to-auth=%s ms; received=%s; acquired=%s; failures=%s",
            outcome, delayMs, server.paths(), events.acquired.toList(), events.failures.toList())
            .that(result is HttpResult.Success).isTrue()
        assertThat(events.failures).isEmpty()
    }

    private class ConnectionEvents(private val closedAtNs: AtomicLong) : EventListener() {
        val acquired = Collections.synchronizedList(mutableListOf<String>())
        val failures = Collections.synchronizedList(mutableListOf<String>())
        val closeToAuthMs = AtomicLong(-1L)

        override fun callStart(call: Call) {
            if (call.request().url.encodedPath == "/api/v1/instance") {
                closeToAuthMs.set(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - closedAtNs.get()))
            }
        }

        override fun connectionAcquired(call: Call, connection: Connection) {
            val summary = "${call.request().method} ${call.request().url.encodedPath}@${connection.socket().localPort}"
            acquired.add(summary)
            Log.d(TAG, "Acquired $summary")
        }

        override fun callFailed(call: Call, ioe: IOException) {
            failures.add("${call.request().method} ${call.request().url.encodedPath}: ${ioe.javaClass.simpleName}")
        }
    }

    private class IdleCloseServer : Closeable {
        private val listener = ServerSocket(0, LISTEN_BACKLOG, InetAddress.getByName("127.0.0.1"))
            .apply { soTimeout = SOCKET_TIMEOUT_MS }
        val baseUrl = "http://127.0.0.1:${listener.localPort}"
        val closedAtNs = AtomicLong()
        private val closeWarmup = CountDownLatch(1)
        private val warmupClosed = CountDownLatch(1)
        private val stopping = AtomicBoolean()
        private val active = AtomicReference<Socket?>()
        private val failure = AtomicReference<String?>()
        private val received = Collections.synchronizedList(mutableListOf<String>())
        private val worker = thread(name = "AuthApiConnectionTest-server", isDaemon = true) {
            try {
                serveWarmup()
                while (!stopping.get()) serveAuth()
            } catch (e: Exception) {
                if (!stopping.get()) {
                    failure.set(e.javaClass.simpleName)
                    Log.e(TAG, "Synthetic socket server failed (${e.javaClass.simpleName})")
                }
            } finally {
                warmupClosed.countDown()
            }
        }

        fun closeIdleConnection() {
            closeWarmup.countDown()
            check(warmupClosed.await(WAIT_SECONDS, TimeUnit.SECONDS)) { "Idle peer did not close" }
            assertHealthy()
            check(closedAtNs.get() != 0L) { "Idle peer was not closed" }
        }

        fun paths(): List<String> = synchronized(received) { received.toList() }

        fun assertHealthy() {
            check(failure.get() == null) { "Synthetic socket server failed (${failure.get()})" }
        }

        private fun serveWarmup() {
            accept().use { socket ->
                check(readRequest(socket) == "GET /warmup") { "Expected synthetic warmup route" }
                respond(socket, WARMUP_BODY, keepAlive = true)
                check(closeWarmup.await(WAIT_SECONDS, TimeUnit.SECONDS)) { "Idle close signal missing" }
            }
            active.set(null)
            // This signal is sent only after Socket.close(), mirroring an HTTP idle timeout FIN.
            closedAtNs.set(System.nanoTime())
            warmupClosed.countDown()
            Log.d(TAG, "Closed synthetic general-client idle connection")
        }

        private fun serveAuth() {
            accept().use { socket ->
                val body = when (readRequest(socket)) {
                    "GET /api/v1/instance" -> INSTANCE
                    "POST /api/v1/auth/login" -> LOGIN
                    "POST /api/v1/auth/logout" -> ""
                    else -> error("Unexpected synthetic auth route")
                }
                // Close each auth response so this fixture also works with a pool that retains no idle sockets.
                respond(socket, body, keepAlive = false)
            }
            active.set(null)
        }

        private fun accept(): Socket = listener.accept().also {
            active.set(it)
            it.soTimeout = SOCKET_TIMEOUT_MS
        }

        private fun readRequest(socket: Socket): String {
            val source = socket.source().buffer()
            val route = source.readUtf8LineStrict(MAX_HEADER_BYTES)
            val parts = route.split(' ')
            check(parts.size == REQUEST_LINE_PARTS && parts.last() == "HTTP/1.1") { "Invalid synthetic HTTP request" }
            val safeRoute = "${parts.first()} ${parts[1]}"
            var contentLength = 0L
            while (true) {
                val header = source.readUtf8LineStrict(MAX_HEADER_BYTES)
                if (header.isEmpty()) break
                if (header.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = header.substringAfter(':').trim().toLong()
                }
            }
            check(contentLength in 0L..MAX_BODY_BYTES) { "Invalid synthetic request size" }
            source.skip(contentLength) // Consume credentials without retaining or printing the body.
            received.add(safeRoute)
            Log.d(TAG, "Received $safeRoute")
            return safeRoute
        }

        private fun respond(socket: Socket, body: String, keepAlive: Boolean) {
            val bytes = body.toByteArray(Charsets.UTF_8)
            val connection = if (keepAlive) "keep-alive" else "close"
            val headers = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                "Content-Length: ${bytes.size}\r\nConnection: $connection\r\n\r\n"
            socket.getOutputStream().apply {
                write(headers.toByteArray(Charsets.US_ASCII))
                write(bytes)
                flush()
            }
        }

        override fun close() {
            stopping.set(true)
            closeWarmup.countDown()
            try {
                listener.close()
            } finally {
                try {
                    active.get()?.close()
                } finally {
                    worker.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))
                    check(!worker.isAlive) { "Synthetic socket server did not stop" }
                }
            }
        }
    }

    companion object {
        private const val TAG = "AuthApiConnectionTestTester"
        private const val SERVER = "00000000-0000-0000-0000-000000000002"
        private const val USER = "00000000-0000-0000-0000-000000000001"
        private const val EPOCH = "00000000-0000-0000-0000-000000000003"
        private const val CARD = "00000000-0000-0000-0000-000000000004"
        private const val TOKEN = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        private const val EXPIRY = "2099-01-01T00:00:00.000Z"
        private const val SYNTHETIC_PASSWORD = "synthetic-password"
        private const val WARMUP_BODY = "synthetic warmup"
        private const val WAIT_SECONDS = 5L
        private const val SOCKET_TIMEOUT_MS = 5_000
        private const val SHORT_IDLE_WINDOW_MS = 1_000L
        private const val LISTEN_BACKLOG = 8
        private const val REQUEST_LINE_PARTS = 3
        private const val MAX_HEADER_BYTES = 8_192L
        private const val MAX_BODY_BYTES = 65_536L
        private const val INSTANCE = """{"serverId":"$SERVER","dataEpoch":"$EPOCH","installed":true,"protocol":{"min":"1","max":"1"},"schema":{"min":"1","max":"1"}}"""
        private const val LOGIN = """{"accountIdentity":{"serverId":"$SERVER","userId":"$USER","dataEpoch":"$EPOCH","ownerRevision":"0","roles":["user"],"selfCard":{"id":"$CARD","version":"1","profileId":"$CARD"}},"session":{"token":"$TOKEN","expiresAt":"$EXPIRY"}}"""
    }
}
