package top.mcxiafeng.badger.network

import android.util.Log

/**
 * [Phase 3] 多端增量同步拉取（新 Java `/api` 契约）。
 *
 * `GET /api/user/sync?since={version}`（`Badger-Server/docs/api-handover.md` §4.8）：
 * - `since` 默认 0（全量重放历史）；单批上限 500 条；
 * - 返回 `data: { version, changes, hasMore }`，[SyncPage.version] 是下一轮 since，
 *   [SyncPage.hasMore] 为 true 时需继续拉直至 false；
 * - changes 为 UserHistory 增量（append-only，owner 域版本严格单调递增），
 *   客户端按序重放落 Room。
 */
class SyncApi(private val core: ApiCore) {

    /**
     * 拉取 [since] 之后的增量变更。
     *
     * @param since 上次消费到的版本号；首次同步传 0 即全量重放。
     * @param limit 单批条数（服务端默认 500，传 500 为显式约定）。
     */
    fun syncSince(since: Long, limit: Int = 500): SyncPage {
        val tag = core.nextCallTag()
        val path = "/api/user/sync?since=$since&limit=$limit"
        Log.d(TAG, "[$tag] syncSince: since=$since limit=$limit")
        return core.execute(core.buildRequest("GET", path).build())
            .unwrapApiResult("sync.pull", tag) { data ->
                if (!data.isJsonObject) {
                    Log.w(TAG, "[$tag] syncSince: data not object, empty page")
                    return@unwrapApiResult SyncPage(since, emptyList(), hasMore = false)
                }
                val page = SyncPage.from(data.asJsonObject)
                Log.d(TAG, "[$tag] syncSince OK: version=${page.version} changes=${page.changes.size} hasMore=${page.hasMore}")
                page
            }
    }

    private companion object {
        const val TAG = ApiCore.TAG
    }
}
