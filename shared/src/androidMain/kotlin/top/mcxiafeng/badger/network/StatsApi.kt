package top.mcxiafeng.badger.network

import kotlinx.serialization.json.JsonObject
import top.mcxiafeng.badger.utils.BadgerLog

/**
 * [C1] Dashboard 统计概览 endpoints（新 Java `/api` 契约）。
 *
 * - `GET /api/user/stats` → `data: { persons, personsDelta, tags, tagsDelta, collections, collectionsDelta, storageBytes, recentPersons, recentCollections }`
 *   服务端返回当前用户的统计概览；若端点不存在（404），客户端降级为本地 Room 计数。
 *
 * 鉴权走 [ApiCore] Bearer。
 */
class StatsApi(private val core: ApiCore) {

    /**
     * GET /api/user/stats — 统计概览。
     *
     * 404 时返回 null（服务端尚未部署此端点），调用方降级为本地计数。
     * 其它非 2xx 原样抛 [ApiException]。
     */
    fun getStats(): UserStats? {
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] stats.get")
        return try {
            core.execute(core.buildRequest("GET", "/api/user/stats").build())
                .unwrapApiResult("stats.get", tag) { data ->
                    val obj = data as? JsonObject
                    if (obj == null) {
                        BadgerLog.w(TAG, "[$tag] stats: expected data object, got ${data::class.simpleName}")
                        return@unwrapApiResult null
                    }
                    UserStats.parse(obj)
                }
        } catch (e: ApiException) {
            if (e.status == 404) {
                BadgerLog.d(TAG, "[$tag] stats 404: endpoint not deployed, falling back to local counts")
                null
            } else throw e
        }
    }

    companion object {
        private const val TAG = "StatsApi"
    }
}
