package top.mcxiafeng.badger.network

import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import io.mockk.unmockkAll
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Contract tests for the canonical POST /api/resolve/ endpoint. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContactNetworkResolverTest {

    private lateinit var server: LocalHttpServer
    private lateinit var api: ServerApi
    private lateinit var resolver: ContactNetworkResolver

    @Before
    fun setUp() {
        runCatching { GlobalContext.stopKoin() }
        GlobalContext.startKoin {
            modules(module { single { mockk<okhttp3.OkHttpClient>(relaxed = true) } })
        }
        server = LocalHttpServer().also { it.start() }
        api = OkHttpServerApi(
            baseUrl = server.baseUrl,
            http = OkHttpClient(),
            tokenProvider = { null },
            outboxStore = mockk(relaxed = true),
            outboxScheduler = mockk(relaxed = true),
        )
        resolver = ContactNetworkResolver(api)
    }

    @After
    fun tearDown() {
        unmockkAll()
        server.stop()
        runCatching { GlobalContext.stopKoin() }
    }

    @Test
    fun `identify parses canonical single-item response`() {
        server.enqueue(
            status = 200,
            body = """{"code":200,"data":{"platform":"qq","name":"QQ用户12345","avatarUrl":"https://q1.qlogo.cn/g?b=qq&nk=12345&s=100","description":"sig","contacts":{"qq":"12345"}}}"""
        )

        val resp = resolver.identify("12345")

        assertThat(resp).isNotNull()
        assertThat(resp!!.kind).isEqualTo("qq")
        assertThat(resp.name).isEqualTo("QQ用户12345")
        assertThat(resp.avatarUrl).isEqualTo("https://q1.qlogo.cn/g?b=qq&nk=12345&s=100")
        assertThat(resp.description).isEqualTo("sig")
        assertThat(resp.contactMap).containsExactly("qq", "12345")
        assertThat(server.requestCount.get()).isEqualTo(1)
        assertThat(server.lastPath.get()).isEqualTo("/api/resolve/")
        assertThat(server.lastBody.get()).contains("\"input\":\"12345\"")
    }

    @Test
    fun `identify does not accept removed legacy field names`() {
        server.enqueue(
            status = 200,
            body = """{"code":200,"data":{"kind":"github","signature":"legacy","avatar_url":"legacy","contact_map":{"github":"octocat"}}}"""
        )

        val resp = resolver.identify("https://github.com/octocat")

        assertThat(resp).isNotNull()
        assertThat(resp!!.kind).isEqualTo("unknown")
        assertThat(resp.name).isNull()
        assertThat(resp.avatarUrl).isNull()
        assertThat(resp.description).isNull()
        assertThat(resp.contactMap).isEmpty()
    }

    @Test
    fun `identify returns null on blank input without hitting server`() {
        val resp = resolver.identify("")
        assertThat(resp).isNull()
        assertThat(server.requestCount.get()).isEqualTo(0)
    }

    @Test
    fun `identifyBatch chunks canonical api requests by server limit`() {
        val first = (1..50).map { "input-$it" }
        val second = listOf("input-51")
        val result1 = first.map { "{\"platform\":\"qq\",\"contacts\":{\"qq\":\"$it\"}}" }.joinToString(",")
        val result2 = second.map { "{\"platform\":\"qq\",\"contacts\":{\"qq\":\"$it\"}}" }.joinToString(",")
        server.enqueue(status = 200, body = """{"code":200,"data":{"results":[$result1]}}""")
        server.enqueue(status = 200, body = """{"code":200,"data":{"results":[$result2]}}""")

        val response = resolver.identifyBatch(first + second)

        assertThat(response).hasSize(51)
        assertThat(response).doesNotContain(null)
        assertThat(server.requestCount.get()).isEqualTo(2)
        assertThat(server.lastPath.get()).isEqualTo("/api/resolve/")
    }

    @Test
    fun `identifyBatch returns all-null on server 5xx`() {
        server.enqueue(status = 500, body = """{"error":"server down"}""")
        val resp = resolver.identifyBatch(
            listOf("https://github.com/octocat", "https://space.bilibili.com/99999"),
        )
        assertThat(resp).containsExactly(null, null)
        assertThat(server.requestCount.get()).isEqualTo(1)
    }

    @Test
    fun `identifyBatch returns all-null when data lacks results`() {
        server.enqueue(status = 200, body = """{"code":200,"data":{"foo":"bar"}}""")
        val resp = resolver.identifyBatch(listOf("https://github.com/octocat"))
        assertThat(resp).containsExactly(null)
        assertThat(server.requestCount.get()).isEqualTo(1)
    }
}
