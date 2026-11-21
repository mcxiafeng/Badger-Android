package top.mcxiafeng.badger.network.sync

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.math.BigDecimal
import java.math.BigInteger
import java.security.MessageDigest

/** Bounded, duplicate-rejecting JSON boundary. Versions never pass through floating point. */
internal object SyncJson {
    const val SCHEMA = 2
    const val MAX_JSON_BYTES = 2 * 1024 * 1024
    const val MAX_VALUE_BYTES = 128 * 1024
    const val MAX_MUTATION_BYTES = 256 * 1024
    const val MAX_SNAPSHOT_BYTES = 64 * 1024 * 1024
    private const val MAX_DEPTH = 32
    private val decimal = Regex("0|[1-9][0-9]*")
    private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    private val gson = GsonBuilder().disableHtmlEscaping().serializeNulls().create()

    fun parse(text: String): JsonObject {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_JSON_BYTES) { "同步响应超出大小限制" }
        return JsonReader(StringReader(text)).use { reader ->
            val result = read(reader, 0)
            require(reader.peek() == JsonToken.END_DOCUMENT) { "同步响应包含多余内容" }
            require(result.isJsonObject) { "同步响应必须是对象" }
            result.asJsonObject
        }
    }

    private fun read(reader: JsonReader, depth: Int): JsonElement {
        require(depth <= MAX_DEPTH) { "同步响应嵌套过深" }
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> JsonObject().apply {
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = validText(reader.nextName())
                    require(!has(name)) { "同步响应包含重复字段" }
                    add(name, read(reader, depth + 1))
                }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> JsonArray().apply {
                reader.beginArray()
                while (reader.hasNext()) add(read(reader, depth + 1))
                reader.endArray()
            }
            JsonToken.STRING -> JsonPrimitive(validText(reader.nextString()))
            JsonToken.NUMBER -> JsonPrimitive(BigDecimal(reader.nextString()))
            JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
            JsonToken.NULL -> { reader.nextNull(); JsonNull.INSTANCE }
            else -> error("同步响应 JSON 不完整")
        }
    }

    private fun validText(text: String): String {
        var index = 0
        while (index < text.length) {
            val ch = text[index]
            require(ch != '\u0000') { "同步文本包含非法字符" }
            if (ch.isHighSurrogate()) {
                require(index + 1 < text.length && text[index + 1].isLowSurrogate()) { "同步文本包含不完整的 Unicode 字符" }
                index++
            } else require(!ch.isLowSurrogate()) { "同步文本包含不完整的 Unicode 字符" }
            index++
        }
        return text
    }

    fun obj(vararg values: Pair<String, Any?>): JsonObject = JsonObject().apply {
        values.forEach { (key, value) -> add(key, element(value)) }
    }

    fun element(value: Any?): JsonElement = when (value) {
        null -> JsonNull.INSTANCE
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Iterable<*> -> JsonArray().apply { value.forEach { add(element(it)) } }
        else -> error("不支持的同步字段类型")
    }

    fun encode(value: JsonElement): String = gson.toJson(value)

    /** Metadata uses only integral numbers; page hashes use the exact wire rows below. */
    fun canonical(value: JsonElement): String = when {
        value.isJsonNull -> "null"
        value.isJsonArray -> value.asJsonArray.joinToString(",", "[", "]") { canonical(it) }
        value.isJsonObject -> value.asJsonObject.keySet().sorted().joinToString(",", "{", "}") {
            gson.toJson(it) + ":" + canonical(value.asJsonObject[it])
        }
        value.asJsonPrimitive.isNumber -> value.asBigDecimal.stripTrailingZeros().toPlainString()
        else -> gson.toJson(value)
    }

    fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun counter(value: String): BigInteger {
        require(decimal.matches(value)) { "非法同步版本" }
        return value.toBigInteger().also { require(it <= BigInteger.valueOf(Long.MAX_VALUE)) }
    }

    fun id(value: String): String = value.also { require(uuid.matches(it)) { "非法同步标识" } }

    fun text(obj: JsonObject, key: String): String? {
        val value = obj[key] ?: return null
        if (value.isJsonNull) return null
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "同步字段 $key 类型错误" }
        return value.asString
    }

    fun array(obj: JsonObject, key: String): JsonArray {
        val value = obj[key]
        require(value != null && value.isJsonArray) { "同步字段 $key 必须是数组" }
        return value.asJsonArray
    }

    fun long(obj: JsonObject, key: String): Long {
        val value = obj[key]
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
        return value.asBigDecimal.longValueExact()
    }

    /** Locate the top-level rows array without reformatting numeric or Unicode wire bytes. */
    fun wireRows(text: String): ByteArray {
        var depth = 0
        var quoted = false
        var escaped = false
        var start = -1
        var index = 0
        while (index < text.length) {
            val ch = text[index]
            if (quoted) {
                if (escaped) escaped = false else if (ch == '\\') escaped = true else if (ch == '"') quoted = false
            } else when (ch) {
                '"' -> {
                    if (depth == 1 && text.startsWith("\"rows\"", index)) {
                        var cursor = index + "\"rows\"".length
                        while (cursor < text.length && text[cursor].isWhitespace()) cursor++
                        if (text.getOrNull(cursor) == ':') {
                            cursor++
                            while (cursor < text.length && text[cursor].isWhitespace()) cursor++
                            require(text.getOrNull(cursor) == '[')
                            start = cursor
                            index = cursor - 1
                        } else quoted = true
                    } else quoted = true
                }
                '{', '[' -> depth++
                '}', ']' -> {
                    depth--
                    if (start >= 0 && depth == 1) return text.substring(start, index + 1).toByteArray(Charsets.UTF_8)
                }
            }
            index++
        }
        error("同步分页缺少 rows")
    }
}

internal data class SyncRoot(
    val kind: String,
    val id: String,
    val version: String = "0",
    val deleted: Boolean = false,
    val value: JsonObject? = null,
) {
    val key: String get() = "$kind/$id"
    fun json(): JsonObject = SyncJson.obj("kind" to kind, "id" to id, "version" to version,
        "deleted" to deleted, "value" to value)

    companion object {
        fun read(obj: JsonObject): SyncRoot {
            val kind = requireNotNull(SyncJson.text(obj, "kind"))
            require(kind in setOf("person", "selfCard", "collection", "tag", "fieldDefinition", "asset", "publishedCard"))
            val id = SyncJson.id(requireNotNull(SyncJson.text(obj, "id")))
            val version = requireNotNull(SyncJson.text(obj, "version")).also { SyncJson.counter(it) }
            val deleted = obj["deleted"].let { require(it?.isJsonPrimitive == true && it.asJsonPrimitive.isBoolean); it.asBoolean }
            val value = obj["value"]?.takeUnless { it.isJsonNull }?.asJsonObject
            require((deleted && value == null) || (!deleted && value != null))
            return SyncRoot(kind, id, version, deleted, value)
        }
    }
}
