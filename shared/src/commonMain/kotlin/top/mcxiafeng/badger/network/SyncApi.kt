package top.mcxiafeng.badger.network

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import top.mcxiafeng.badger.utils.BadgerLog

/** 多端增量同步拉取：`GET /api/user/sync?since=`。 */
class SyncApi(private val core: ApiCore) {

    /**
     * 拉取 [since] 之后的增量变更。
     *
     * 服务端 owner 域版本严格递增；客户端拒绝回退、无进展和解析后 silently dropped 的页面。
     */
    fun syncSince(since: Long, limit: Int = 500): SyncPage {
        require(since >= 0L) { "since must be >= 0" }
        require(limit in 1..MAX_PAGE_SIZE) { "limit must be in 1..$MAX_PAGE_SIZE" }

        val tag = core.nextCallTag()
        val path = "/api/user/sync?since=$since&limit=$limit"
        BadgerLog.d(TAG, "[$tag] syncSince: since=$since limit=$limit")
        return core.execute(core.request("GET", path))
            .unwrapApiResult("sync.pull", tag) { data ->
                val obj = data as? JsonObject
                if (obj == null) {
                    throw ApiException(0, data.toString().take(LOG_BODY_LIMIT), "sync.pull data not object")
                }

                val changesJson = obj["changes"]?.jsonArray
                    ?: throw ApiException(0, "missing changes", "sync.pull")
                val page = SyncPage.from(obj)

                if (changesJson.size != page.changes.size) {
                    throw ApiException(
                        0,
                        "sync changes parse mismatch raw=${changesJson.size} parsed=${page.changes.size}",
                        "sync.pull",
                    )
                }
                if (page.version < since) {
                    throw ApiException(0, "sync version regressed: $since -> ${page.version}", "sync.pull")
                }
                if (page.changes.any { it.version <= since }) {
                    throw ApiException(0, "sync change version is not ahead of since=$since", "sync.pull")
                }
                if (page.hasMore && page.version <= since) {
                    throw ApiException(0, "sync page hasMore without cursor progress", "sync.pull")
                }

                BadgerLog.d(
                    TAG,
                    "[$tag] syncSince OK: version=${page.version} changes=${page.changes.size} hasMore=${page.hasMore}",
                )
                page
            }
    }

    private companion object {
        const val TAG = ApiCore.TAG
        const val MAX_PAGE_SIZE = 500
        const val LOG_BODY_LIMIT = 200
    }
}
