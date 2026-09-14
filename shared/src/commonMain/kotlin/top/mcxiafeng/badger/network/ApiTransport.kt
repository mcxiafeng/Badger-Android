package top.mcxiafeng.badger.network

class ApiHttpRequest(
    val method: String,
    val url: String,
    val body: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val multipart: ApiMultipartPart? = null,
)

class ApiMultipartPart(
    val fieldName: String,
    val fileName: String,
    val bytes: ByteArray,
    val mediaType: String,
)

class ApiHttpResponse(
    val code: Int,
    val message: String,
    val bodyText: String?,
) : AutoCloseable {
    override fun close() {}
}

fun interface ApiTransport {
    fun execute(request: ApiHttpRequest): ApiHttpResponse
}
