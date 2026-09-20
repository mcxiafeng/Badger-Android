package top.mcxiafeng.badger.network.core

import top.mcxiafeng.badger.data.system.database.SystemDbHolder

/**
 * 全局 API 配置 + 鉴权头构造。
 *
 * token 唯一来源 = System 库载体 UserSyncState（登录时写入），本类不持有任何凭据。
 * 未登录直接抛错：调用方负责在登录前置检查（如 SyncPullEngine 的载体门控），
 * 请求不带鉴权头裸发只会把错误推迟到服务端 401，掩盖根因。
 */
object PublicApi {

    var serverUrl: String = "http://192.168.10.2:8080"

    /** 每次请求实时构造鉴权头（读库）；未登录抛 IllegalStateException。 */
    suspend fun authHeaders(): Map<String, String> {
        val token = SystemDbHolder.get().userSyncStateDao().getActiveState()?.token
            ?: throw IllegalStateException("未登录：System 库无活跃会话")
        return mapOf("Authorization" to "Bearer $token")
    }
}
