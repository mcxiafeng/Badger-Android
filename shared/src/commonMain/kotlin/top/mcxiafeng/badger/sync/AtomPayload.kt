package top.mcxiafeng.badger.sync

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.uuid.Uuid

/**
 * [SyncAtom.data] 载荷编解码。分类（谁/哪类实体/什么操作）全在表列上
 * （userUuid/entityKind/syncType），data 只装该操作必须携带的最小内容：
 * - INSERT：data = 实体全文（uuid 在实体里）
 * - DELETE：data = {"uuid": "..."}（唯一没有实体体的形态）
 * - UPDATE：data = {"from": 实体全文, "to": 实体全文}（from = 改前基线，冲突判定依据）
 *
 * 契约字段缺失直接抛错（fail-loud），不做静默兜底。
 */
object AtomPayload {

    private const val KEY_UUID = "uuid"
    private const val KEY_FROM = "from"
    private const val KEY_TO = "to"

    /** INSERT 载荷 = 实体全文，原样透传。 */
    fun insert(entity: JsonObject): JsonObject = entity

    fun delete(uuid: Uuid): JsonObject = buildJsonObject {
        put(KEY_UUID, uuid.toString())
    }

    fun update(from: JsonObject, to: JsonObject): JsonObject = buildJsonObject {
        put(KEY_FROM, from)
        put(KEY_TO, to)
    }

    /** 按 syncType 从对应位置取目标 uuid（INSERT 在实体里、UPDATE 在 to 里、DELETE 顶层）。 */
    fun uuidOf(syncType: SyncType, data: JsonObject): Uuid = when (syncType) {
        SyncType.INSERT -> Uuid.parse(requireString(data))
        SyncType.UPDATE -> Uuid.parse(requireString(requireObject(data, KEY_TO)))
        SyncType.DELETE -> Uuid.parse(requireString(data))
    }

    fun fromOf(data: JsonObject): JsonObject = requireObject(data, KEY_FROM)

    fun toOf(data: JsonObject): JsonObject = requireObject(data, KEY_TO)

    private fun requireString(data: JsonObject): String =
        data[KEY_UUID]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("SyncAtom 载荷缺契约字段 $KEY_UUID（内容不入日志，防敏感信息泄漏）")

    private fun requireObject(data: JsonObject, key: String): JsonObject =
        data[key]?.jsonObject
            ?: throw IllegalStateException("SyncAtom 载荷缺契约字段 $key（内容不入日志，防敏感信息泄漏）")
}
