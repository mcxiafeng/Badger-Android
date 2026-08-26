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
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ContactNetworkResolver 端到端测试 —— 走服务端 `POST /api/resolve/` 唯一入口。
 *
 * [Phase 4] 契约（`Badger-Server/docs/api-handover.md` §5）：
 * - 批量 body `{ items: [...] }`；响应 ApiResult 壳，批量 `data = { results: [...] }`；
 * - ResolveResult 字段 camelCase（`platform/avatarUrl/description/contacts`），
 *   `signature` → `description`、`contact_map` → `contacts`、`avatar_url` → `avatarUrl`。
 *
 * 测试用一个进程内的 Java ServerSocket 模拟服务端,通过 [LocalHttpServer] 拿到一个
 * 真实的 http://127.0.0.1:port URL,再用一个直连此 URL 的 [ServerApi] 走完整 OkHttp
 * 栈。避免引入 MockWebServer 等第三方依赖。
 *
 * 覆盖 6 个核心 case:
 * 1. 任意 input → POST /api/resolve/ → IdentifyResponse(kind, ...)（camelCase 字段）
 * 2. 服务端识别为 error/unknown → null fields,kind="unknown"
 * 3. 服务端 5xx → identify 返回 null
 * 4. 旧 getResultInfo 签名仍可用,内部委托给 identify
 * 5. getResultInfo type 参数兜底（服务端 unknown 但调用方已知）
 * 6. 批量走单次 POST,按输入顺序返回,空串位置补 null
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContactNetworkResolverTest {

    private lateinit var server: LocalHttpServer
    private lateinit var api: ServerApi

    @Before
    fun setUp() {
        // [§14.2] Robolectric 测试不走 BadgerApplication.onCreate;若 ContactNetworkResolver
        // 走 KoinComponentBy.get<ServerApiFactory>() 路径,必须先 startKoin。
        runCatching { GlobalContext.stopKoin() }
        GlobalContext.startKoin {
            modules(
                module {
                    single { mockk<okhttp3.OkHttpClient>(relaxed = true) }
                },
            )
        }
        server = LocalHttpServer().also { it.start() }
        // 直连本地 server 的 ServerApi —— 走完整 OkHttp 栈(同一个 tokenProvider 不
        // 带 Authorization,模拟未登录态;拦截器在生产中会注入 token,这里不需要)。
        api = ServerApi(
            baseUrl = server.baseUrl,
            http = OkHttpClient(),
            tokenProvider = { null },
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
        server.stop()
        runCatching { GlobalContext.stopKoin() }
    }

    @Test
    fun `identify returns server-provided kind and contact_map`() {
        server.enqueue(
            status = 200,
            body = """{"code":200,"data":{"results":[{"platform":"qq","name":"QQ用户12345","avatarUrl":"https://q1.qlogo.cn/g?b=qq&nk=12345&s=100","description":"sig","contacts":{"qq":"12345"}}]}}"""
        )

        val resp = ContactNetworkResolver.identifyWith(api, "12345")

        assertThat(resp).isNotNull()
        assertThat(resp!!.kind).isEqualTo("qq")
        assertThat(resp.name).isEqualTo("QQ用户12345")
        assertThat(resp.avatarUrl).isEqualTo("https://q1.qlogo.cn/g?b=qq&nk=12345&s=100")
        assertThat(resp.signature).isEqualTo("sig")
        assertThat(resp.contactMap).containsExactly("qq", "12345")
        // [修复防御]: 显式断言服务端只命中一条请求,path 带尾斜杠 `/api/resolve/`,
        // 且 body 携带 items[] 字段。防止有人误把旧 5 个 endpoint 之一恢复回来 ——
        // 那样的话 kind 仍可能拼凑出来,但 path 不是 /api/resolve/,识别主路径即偏离。
        assertThat(server.requestCount.get()).isEqualTo(1)
        assertThat(server.lastPath.get()).isEqualTo("/api/resolve/")
        assertThat(server.lastBody.get()).contains("\"items\":[\"12345\"]")
    }

    @Test
    fun `identify returns kind unknown for error result`() {
        server.enqueue(
            status = 200,
            body = """{"code":200,"data":{"results":[{"platform":null,"input":"gibberish string","status":"error","name":null,"contacts":{}}]}}"""
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
        // [修复防御]: 模拟一个完整 ContactType 链路 —— 服务端给出 platform="github",
        // getResultInfo 必须用 kindToContactType 把它投到 ContactType.GitHub,
        // 且 contacts 透传给 UI。kindToContactType 是字符串到 UI 标签的固定映射。
        server.enqueue(
            status = 200,
            body = """{"code":200,"data":{"results":[{"platform":"github","name":"The Octocat","avatarUrl":"https://avatars.githubusercontent.com/u/583231","description":"bio","contacts":{"github":"octocat"}}]}}"""
        )

        val result = ContactNetworkResolver.getResultInfoInternal(api, "https://github.com/octocat")

        assertThat(result).isNotNull()
        assertThat(result!!.type).isEqualTo(ContactType.GitHub)
        assertThat(result.nickname).isEqualTo("The Octocat")
        assertThat(result.avatarUrl).isEqualTo("https://avatars.githubusercontent.com/u/583231")
        assertThat(result.contactMap).containsExactly("github", "octocat")
        assertThat(server.requestCount.get()).isEqualTo(1)
        assertThat(server.lastPath.get()).isEqualTo("/api/resolve/")
    }

    @Test
    fun `getResultInfo uses kindToContactType when type hint omitted`() {
        server.enqueue(
            status = 200,
            body = """{"code":200,"data":{"results":[{"platform":"bilibili","name":"B站用户","contacts":{"bilibili":"99999"}}]}}"""
        )

        val result = ContactNetworkResolver.getResultInfoInternal(api, "https://space.bilibili.com/99999")

        assertThat(result).isNotNull()
        assertThat(result!!.type).isEqualTo(ContactType.Bilibili)
    }

    /**
     * Batch variant: 单次 POST 装多条 item,服务端按输入顺序返回 N 条结果。
     * 旧实现逐条 POST 这里 5 个 URL 就是 5 次 RTT —— 现在必须折叠成 1 次。
     */
    @Test
    fun `identifyBatch returns one result per input in order, single POST`() {
        server.enqueue(
            status = 200,
            body = """
                {"code":200,"data":{"results":[
                  {"platform":"github","name":"The Octocat","avatarUrl":"https://a","contacts":{"github":"octocat"}},
                  {"platform":"bilibili","name":"B站","contacts":{"bilibili":"99999"}},
                  {"platform":"qq","name":"QQ用户","avatarUrl":"https://q","contacts":{"qq":"12345"}}
                ]}}
            """.trimIndent(),
        )

        // 故意带一个空字符串 + 一个 status=error —— 服务端不会剔除空位，
        // 我们这层负责把空串折叠并在结果数组里填 null，保持 inputs 同长。
        val resp = ContactNetworkResolver.identifyBatchWith(
            api,
            listOf("https://github.com/octocat", "", "https://space.bilibili.com/99999", "12345"),
        )

        // 长度严格等于 inputs,空串对应的位置必须是 null
        assertThat(resp).hasSize(4)
        assertThat(resp[0]).isNotNull()
        assertThat(resp[0]!!.kind).isEqualTo("github")
        assertThat(resp[0]!!.name).isEqualTo("The Octocat")
        assertThat(resp[1]).isNull()                       // 空串被折叠
        assertThat(resp[2]).isNotNull()
        assertThat(resp[2]!!.kind).isEqualTo("bilibili")
        assertThat(resp[3]).isNotNull()
        assertThat(resp[3]!!.kind).isEqualTo("qq")

        // 关键契约: 不管输入几条 URL,HTTP 层只应该发出 1 个 POST。
        assertThat(server.requestCount.get()).isEqualTo(1)
        assertThat(server.lastPath.get()).isEqualTo("/api/resolve/")
        // body 内 items 数组要去掉空串,顺序与 inputs 中非空位一致
        assertThat(server.lastBody.get()).contains("\"items\":[\"https://github.com/octocat\",\"https://space.bilibili.com/99999\",\"12345\"]")
    }

    /**
     * Batch 整个网络失败的兜底：服务端 5xx → 整批 null,不抛异常。
     */
    @Test
    fun `identifyBatch returns all-null on server 5xx`() {
        server.enqueue(status = 500, body = """{"error":"server down"}""")
        val resp = ContactNetworkResolver.identifyBatchWith(api, listOf("https://github.com/octocat", "https://space.bilibili.com/99999"))
        assertThat(resp).containsExactly(null, null)
        assertThat(server.requestCount.get()).isEqualTo(1)
    }

    /**
     * [Phase 4] ApiResult 壳批量契约回归：data 缺 results 或非数组 → 整批 null（有日志，不吞）。
     */
    @Test
    fun `identifyBatch returns all-null when data lacks results`() {
        server.enqueue(
            status = 200,
            body = """{"code":200,"data":{"foo":"bar"}}"""
        )
        val resp = ContactNetworkResolver.identifyBatchWith(api, listOf("https://github.com/octocat"))
        assertThat(resp).containsExactly(null)
        assertThat(server.requestCount.get()).isEqualTo(1)
    }
}