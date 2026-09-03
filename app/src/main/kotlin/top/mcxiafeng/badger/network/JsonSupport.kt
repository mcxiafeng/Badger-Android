package top.mcxiafeng.badger.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Shared API models and small JSON helpers for the `/api` network surface. */

/**
 * [K04] 全局 JSON 配置（Gson 容忍语义的 kotlinx 等价）：
 * - ignoreUnknownKeys：Gson 默认忽略未知字段；
 * - isLenient：容忍引号包裹的数字/布尔等松散形态；
 * - coerceInputValues：JSON null / 非法值 coerce 成字段默认值（对齐 Gson 缺字段给默认的防御式语义）；
 * - explicitNulls=false：编码省略 null 字段（对齐 ProfileDto.toJsonObject 的「无值省略」契约）；
 * - encodeDefaults=false：编码省略等于默认值的字段（对齐 outbox payload 的字段级 merge 语义）。
 */
val BadgerJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = false
}

// ========== Gson 防御式解析 helper 的 kotlinx 平移（语义逐条对齐，勿改行为） ==========

/**
 * ⚠️ [K04] kotlinx 与 Gson 的关键差异：kotlinx 的 `JsonNull` 是 `JsonPrimitive` 子类
 * （Gson 里不是）。裸写 `(x["k"] as? JsonPrimitive)?.content` 遇到 JSON null 会得到
 * 字符串 `"null"`——凡取 content 必须经本扩展或 takeIfString（内含 JsonNull 守卫）。
 */
internal fun JsonElement?.contentOrNull(): String? = when {
    this == null || this is JsonNull -> null
    else -> this.jsonPrimitive.content
}

/**
 * 取字符串：JsonNull → null、非 primitive → null、blank → null、数字 → content。
 * 对齐旧 Gson stringOrNull：调用方 `.orEmpty()` 得到与迁移前一致的缺省链。
 */
internal fun stringOrNull(o: JsonObject, key: String): String? {
    val v = o[key] ?: return null
    return v.takeIfString()
}

/** primitive → content（JsonNull / blank → null）；复合类型 → null。 */
internal fun JsonElement.takeIfString(): String? {
    if (this is JsonNull) return null
    val p = this as? JsonPrimitive ?: return null
    return p.content.takeIf { it.isNotBlank() }
}

/**
 * 时间字段取值：字符串取 content（blank → null）、数字取 content 字符串（服务端可能回 epoch 数值）。
 * 对齐旧 Gson jsonTimeOrNull。
 */
internal fun jsonTimeOrNull(o: JsonObject, key: String): String? {
    val v = o[key] ?: return null
    val p = v as? JsonPrimitive ?: return null
    return when {
        p.isString -> p.content.takeIf { it.isNotBlank() }
        v.isNumberPrimitive() -> p.content
        else -> null
    }
}

/** JsonObject 字段取嵌套对象：缺失 / JsonNull / 非对象 → null。 */
internal fun jsonObjectOrNull(o: JsonObject, key: String): JsonObject? = o[key] as? JsonObject

/** JsonObject 字段取数组：缺失 / JsonNull / 非数组 → null。 */
internal fun jsonArrayOrNull(o: JsonObject, key: String): JsonArray? = o[key] as? JsonArray

/**
 * number primitive 判断（kotlinx 无 isNumber 扩展；JsonNull 会伪装成 JsonPrimitive，需排除）。
 * 对齐 Gson `isJsonPrimitive && asJsonPrimitive.isNumber`。
 */
internal fun JsonElement?.isNumberPrimitive(): Boolean {
    val p = this as? JsonPrimitive ?: return false
    if (p is JsonNull) return false
    return p.intOrNull != null || p.longOrNull != null || p.floatOrNull != null
}

/** primitive 布尔缺省：缺失 / JsonNull / 非布尔 → [default]。对齐 Gson `asBoolean ?: default`。 */
internal fun boolOr(v: JsonElement?, default: Boolean): Boolean {
    val p = v as? JsonPrimitive ?: return default
    return p.booleanOrNull ?: default
}

/** primitive 整数缺省。对齐 Gson `asInt ?: default`（含 "200.0" 小数形态整数收敛）。 */
internal fun intOr(v: JsonElement?, default: Int): Int {
    val p = v as? JsonPrimitive ?: return default
    p.intOrNull?.let { return it }
    return p.content.toDoubleOrNull()?.toInt() ?: default
}

/** primitive 长整数缺省。对齐 Gson `asLong ?: default`（含小数形态整数收敛）。 */
internal fun longOr(v: JsonElement?, default: Long): Long {
    val p = v as? JsonPrimitive ?: return default
    p.longOrNull?.let { return it }
    return p.content.toDoubleOrNull()?.toLong() ?: default
}

/** primitive 字符串缺省（不清洗 blank）。对齐 Gson `asString`。 */
internal fun stringOr(v: JsonElement?, default: String): String {
    val p = v as? JsonPrimitive ?: return default
    return p.content.ifBlank { default }
}

/** Map<String, String> 解析：非 primitive 值 → 空串。对齐旧 Gson parseStringMap。 */
internal fun parseStringMap(o: JsonObject?): Map<String, String> {
    if (o == null) return emptyMap()
    return o.entries.associate { (k, v) ->
        k to (if (v is JsonNull) "" else (v as? JsonPrimitive)?.content ?: "")
    }
}

/** List<String> 解析：非 primitive / JsonNull 元素跳过。对齐旧 Gson parseStringArray。 */
internal fun parseStringArray(o: JsonArray?): List<String> {
    if (o == null) return emptyList()
    return o.mapNotNull { it.takeIfString() }
}
