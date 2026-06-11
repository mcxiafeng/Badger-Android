package top.mcxiafeng.badger.network

sealed class WebDavResult<out T> {
    data class Success<T>(val data: T) : WebDavResult<T>()
    data object NotFound : WebDavResult<Nothing>()
    data object Timeout : WebDavResult<Nothing>()
    data class AuthError(val message: String) : WebDavResult<Nothing>()
    data class NetworkError(val throwable: Throwable) : WebDavResult<Nothing>()
}
