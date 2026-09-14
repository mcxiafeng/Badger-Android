package top.mcxiafeng.badger.network

import kotlin.concurrent.Volatile

class TokenHolder {
    @Volatile
    private var token: String? = null

    fun get(): String? = token

    fun set(token: String?) {
        this.token = token
    }
}
