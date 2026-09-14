package top.mcxiafeng.badger.network

import kotlinx.serialization.json.JsonObject
import top.mcxiafeng.badger.utils.BadgerLog

class StatsApi(private val core: ApiCore) {

    

    fun getStats(): UserStats? {
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] stats.get")
        return try {
            core.execute(core.request("GET", "/api/user/stats"))
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
