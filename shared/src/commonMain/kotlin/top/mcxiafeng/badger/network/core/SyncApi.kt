package top.mcxiafeng.badger.network.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.utils.HttpResult
import top.mcxiafeng.badger.utils.KtorHttpCore

/** /api/user/sync 单条变更（服务端 UserHistory 行；fastjson2 省略 null 字段）。 */
data class SyncChange(
    val version: Long,
    val type: String,
    val objectName: String,
    val objectId: String,
    val fieldName: String? = null,
    val value: String? = null,
    val createTime: Long? = null,
)

/** /api/user/sync 单批结果：changes 按 version 升序；version = 本批最新游标。 */
data class SyncBatch(
    val version: Long,
    val hasMore: Boolean,
    val changes: List<SyncChange>,
)

class SyncApi {

    companion object {
        private const val TAG = "SyncApiTester"

        /** 单批上限：服务端合法域 1-1000。 */
        const val DEFAULT_LIMIT = 500

        val httpCore = KtorHttpCore()

        /**
         * 拉取一批增量：GET /api/user/sync?since=&limit=。
         * since=0 等价全量重放；失败返回 null（游标语义由调用方决定是否保留）。
         * 契约字段（version/type/objectName/objectId）服务端恒下发，缺失即解析失败
         * 返回 null——不做静默兜底，把契约破坏暴露出来。
         */
        suspend fun pull(since: Long, limit: Int = DEFAULT_LIMIT): SyncBatch? {
            val url = "${PublicApi.serverUrl}/api/user/sync?since=$since&limit=$limit"
            when (val result = httpCore.get(url, headers = PublicApi.authHeaders())) {
                is HttpResult.Success -> return runCatching { parse(result.body) }
                    .getOrElse {
                        BadgerLog.e(TAG, "pull 响应解析失败（since=$since）", it)
                        null
                    }
                is HttpResult.Failure -> {
                    BadgerLog.w(TAG, "pull 失败（since=$since）: HTTP ${result.code} (${result.errorType})")
                    return null
                }
            }
        }

        private fun parse(body: String): SyncBatch {
            val data = Json.parseToJsonElement(body).jsonObject["data"]?.jsonObject
                ?: error("响应缺 data 节点")
            val changes = data["changes"]?.jsonArray?.map { element ->
                val obj = element.jsonObject
                SyncChange(
                    version = requireField(obj, "version").toLong(),
                    type = requireField(obj, "type"),
                    objectName = requireField(obj, "objectName"),
                    objectId = requireField(obj, "objectId"),
                    fieldName = optionalField(obj, "fieldName"),
                    value = optionalField(obj, "value"),
                    createTime = optionalField(obj, "createTime")?.toLongOrNull(),
                )
            } ?: error("响应缺 changes")
            return SyncBatch(
                // version 是游标权威，缺失/非法必须显式失败，严禁回退 0（会重置游标为全量重放）
                version = data["version"]?.jsonPrimitive?.content?.toLongOrNull()
                    ?: error("响应缺 version 或格式非法"),
                hasMore = data["hasMore"]?.jsonPrimitive?.content == "true",
                changes = changes,
            )
        }

        private fun JsonObject.field(key: String): String? =
            this[key]?.jsonPrimitive?.content?.takeIf { it != "null" }

        private fun requireField(obj: JsonObject, key: String): String =
            obj.field(key) ?: error("变更行缺契约字段 $key")

        private fun optionalField(obj: JsonObject, key: String): String? =
            obj.field(key)
    }
}
