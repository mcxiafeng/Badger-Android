package top.mcxiafeng.badger.utils

class HttpException(
    val code: Int,
    val errorType: HttpResult.ErrorType,
    val responseBody: String?,
    message: String,
) : RuntimeException(message)
