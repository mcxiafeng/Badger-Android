package top.mcxiafeng.badger.utils

/**
 * 网络请求的统一返回类型。
 *
 * 把 [HttpUtil] 老接口的 `String?`（null = 任何非 2xx 都吞掉）拆成结构化结果：
 * - [Success] 携带 body 字符串
 * - [Failure] 携带 HTTP 状态码 + 错误类别 + 响应体,调用方按 [ErrorType] 给用户精确提示
 *
 * 老接口 [HttpUtil.post]/[get]/[patch]/[put] 仍返回 `String?`，内部委托到 Result 版，
 * 保持向后兼容（其他 ShortLinkService / PlatformAdapter 等暂不迁移）。
 */
sealed class HttpResult {

    data class Success(val body: String) : HttpResult()

    data class Failure(
        /**
         * HTTP 状态码。0 表示没有状态码（IOException / SocketTimeoutException 等网络层错误）。
         */
        val code: Int,
        val body: String?,
        val errorType: ErrorType,
    ) : HttpResult()

    enum class ErrorType {
        /** 401 / 403 — API Key 错或权限不足 */
        AUTH,
        /** 429 — 限流 */
        RATE_LIMIT,
        /** SocketTimeoutException — 读/连超时 */
        TIMEOUT,
        /** 5xx — 服务端错误 */
        SERVER,
        /** 其他 IOException — DNS / 连接失败 / SSL 错误等 */
        NETWORK,
        /** 其他 4xx — 业务错误,需要看 body 解读 */
        OTHER,
        /** 未知错误 */
        UNKNOWN,
    }

    /** [Success] 返回 body，其他返回 null。等价于老接口 `String?` 的语义。 */
    fun bodyOrNull(): String? = (this as? Success)?.body

    val isSuccess: Boolean get() = this is Success
}