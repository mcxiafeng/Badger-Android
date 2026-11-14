package top.mcxiafeng.badger.network

/**
 * 统一网络结果封装（移植自 dev 分支 utils/HttpResult）。
 * [Failure.errorType] 由 HTTP 状态码映射，供 ViewModel 转成用户文案。
 */
sealed class HttpResult {

    data class Success(val body: String) : HttpResult()

    data class Failure(
        val code: Int,
        val body: String?,
        val message: String?,
        val errorType: ErrorType,
    ) : HttpResult()

    enum class ErrorType {
        AUTH,
        RATE_LIMIT,
        TIMEOUT,
        SERVER,
        NETWORK,
        OTHER,
        UNKNOWN,
    }

    val isSuccess: Boolean get() = this is Success
}
