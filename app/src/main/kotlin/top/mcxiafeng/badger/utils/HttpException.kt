package top.mcxiafeng.badger.utils

/**
 * 网络请求失败时抛出的异常，携带 [HttpResult.ErrorType] 让调用方做用户友好提示。
 *
 * 通常由 `HttpUtil.postOrThrow` 等便捷包装方法抛出；调用方也可以手动构造。
 */
class HttpException(
    val code: Int,
    val errorType: HttpResult.ErrorType,
    val responseBody: String?,
    message: String,
) : RuntimeException(message)