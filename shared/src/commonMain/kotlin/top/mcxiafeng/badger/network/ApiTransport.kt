package top.mcxiafeng.badger.network

/**
 * [KMP K16] 传输中立的 HTTP 请求模型（`/api` 契约层专用）。
 *
 * ApiCore 与 12 个子 Api 客户端只认这一层——OkHttp（Android）与 Ktor（iOS）各自
 * 通过 [ApiTransport] 把它落到真实网络调用。字段与 OkHttp 原语义一一对应：
 * - [body]：JSON 文本体（POST/PATCH/PUT 缺省 "{}" 由各传输层补齐，与原 buildRequest 一致）；
 * - [multipart]：非空时请求体为 multipart/form-data（仅 uploadImage 使用，字段名固定 file）。
 */
class ApiHttpRequest(
    val method: String,
    val url: String,
    val body: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val multipart: ApiMultipartPart? = null,
)

/** multipart/form-data 的单个 part（服务端契约：`POST /api/user/upload` 字段名 file）。 */
class ApiMultipartPart(
    val fieldName: String,
    val fileName: String,
    val bytes: ByteArray,
    val mediaType: String,
)

/**
 * 传输中立的 HTTP 响应快照。body 在传输层内**一次性读出**（本契约层所有调用点都恰好
 * 消费一次 body，无流式需求），因此无需持有底层连接；[close] 为 no-op，仅为让子 Api
 * 的 `.use { }` 调用形态在迁移后保持不变。
 */
class ApiHttpResponse(
    val code: Int,
    val message: String,
    val bodyText: String?,
) : AutoCloseable {
    override fun close() {}
}

/**
 * [KMP K16] 传输抽象：阻塞式语义（Android=OkHttp execute 原路径；iOS=Ktor + runBlocking）。
 *
 * ServerApi 契约本身是阻塞 API（调用方均在 BadgerDispatchers.io 上），保持阻塞签名
 * 避免全链路 suspend 化的大改；iOS 侧 runBlocking 只发生在 IO 调度器线程。
 */
fun interface ApiTransport {
    fun execute(request: ApiHttpRequest): ApiHttpResponse
}
