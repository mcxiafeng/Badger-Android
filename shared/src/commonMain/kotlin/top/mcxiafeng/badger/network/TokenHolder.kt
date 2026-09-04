package top.mcxiafeng.badger.network

import kotlin.concurrent.Volatile

/**
 * [KMP K08-B] 进程内 access token 持有者（纯内存，不落盘——refresh token 在 AuthPrefs）。
 * 从 di/NetworkModule 内部类抽出，供 common 侧 UserAuthRepository / ServerApiFactory 使用。
 */
class TokenHolder {
    @Volatile
    private var token: String? = null

    fun get(): String? = token

    fun set(token: String?) {
        this.token = token
    }
}
