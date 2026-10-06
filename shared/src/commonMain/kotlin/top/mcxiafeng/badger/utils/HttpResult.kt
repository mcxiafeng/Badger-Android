package top.mcxiafeng.badger.utils

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
