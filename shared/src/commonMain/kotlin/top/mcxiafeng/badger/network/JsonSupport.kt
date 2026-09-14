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

val BadgerJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = false
}

fun JsonElement?.contentOrNull(): String? = when {
    this == null || this is JsonNull -> null
    else -> this.jsonPrimitive.content
}

fun stringOrNull(o: JsonObject, key: String): String? {
    val v = o[key] ?: return null
    return v.takeIfString()
}

fun JsonElement.takeIfString(): String? {
    if (this is JsonNull) return null
    val p = this as? JsonPrimitive ?: return null
    return p.content.takeIf { it.isNotBlank() }
}

fun jsonTimeOrNull(o: JsonObject, key: String): String? {
    val v = o[key] ?: return null
    val p = v as? JsonPrimitive ?: return null
    return when {
        p.isString -> p.content.takeIf { it.isNotBlank() }
        v.isNumberPrimitive() -> p.content
        else -> null
    }
}

fun jsonObjectOrNull(o: JsonObject, key: String): JsonObject? = o[key] as? JsonObject

fun jsonArrayOrNull(o: JsonObject, key: String): JsonArray? = o[key] as? JsonArray

fun JsonElement?.isNumberPrimitive(): Boolean {
    val p = this as? JsonPrimitive ?: return false
    if (p is JsonNull) return false
    return p.intOrNull != null || p.longOrNull != null || p.floatOrNull != null
}

fun boolOr(v: JsonElement?, default: Boolean): Boolean {
    val p = v as? JsonPrimitive ?: return default
    return p.booleanOrNull ?: default
}

fun intOr(v: JsonElement?, default: Int): Int {
    val p = v as? JsonPrimitive ?: return default
    p.intOrNull?.let { return it }
    return p.content.toDoubleOrNull()?.toInt() ?: default
}

fun longOr(v: JsonElement?, default: Long): Long {
    val p = v as? JsonPrimitive ?: return default
    p.longOrNull?.let { return it }
    return p.content.toDoubleOrNull()?.toLong() ?: default
}

fun stringOr(v: JsonElement?, default: String): String {
    val p = v as? JsonPrimitive ?: return default
    return p.content.ifBlank { default }
}

fun parseStringMap(o: JsonObject?): Map<String, String> {
    if (o == null) return emptyMap()
    return o.entries.associate { (k, v) ->
        k to (if (v is JsonNull) "" else (v as? JsonPrimitive)?.content ?: "")
    }
}

fun parseStringArray(o: JsonArray?): List<String> {
    if (o == null) return emptyList()
    return o.mapNotNull { it.takeIfString() }
}
