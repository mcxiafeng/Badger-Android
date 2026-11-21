package top.mcxiafeng.badger.data.sync

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** Three-way merge. Array items merge only by protocol identity, never by display names or position. */
internal object SyncMerge {
    data class Result(val value: JsonElement?, val conflicts: List<String>)

    fun merge(base: JsonElement?, local: JsonElement?, remote: JsonElement?, choices: Map<String, Boolean> = emptyMap()): Result {
        val conflicts = mutableListOf<String>()
        val result = node(base, local, remote, "", choices, conflicts)
        return Result(result, conflicts.distinct())
    }

    private fun node(base: JsonElement?, local: JsonElement?, remote: JsonElement?, path: String,
        choices: Map<String, Boolean>, conflicts: MutableList<String>): JsonElement? {
        if (local == remote) return local?.deepCopy()
        if (local == base) return remote?.deepCopy()
        if (remote == base) return local?.deepCopy()
        // With no shared ancestor, an empty optional value is absence, not a competing edit.
        if (base == null && local?.isJsonNull == true) return remote?.deepCopy()
        if (base == null && remote?.isJsonNull == true) return local?.deepCopy()
        if (base == null && path.substringAfterLast('.') == "createTime") return remote?.deepCopy()
        if (path.substringAfterLast('.') in setOf("updateTime", "profileUpdateTime") &&
            local?.isJsonPrimitive == true && local.asJsonPrimitive.isNumber &&
            remote?.isJsonPrimitive == true && remote.asJsonPrimitive.isNumber) {
            return if (local.asBigDecimal >= remote.asBigDecimal) local.deepCopy() else remote.deepCopy()
        }
        if (local?.isJsonObject == true && remote?.isJsonObject == true && (base == null || base.isJsonObject)) {
            return objects(base?.asJsonObject, local.asJsonObject, remote.asJsonObject, path, choices, conflicts)
        }
        if (local?.isJsonArray == true && remote?.isJsonArray == true && (base == null || base.isJsonArray)) {
            arrays(base?.asJsonArray, local.asJsonArray, remote.asJsonArray, path, choices, conflicts)?.let { return it }
        }
        val key = path.ifEmpty { "资料" }
        if (!choices.containsKey(key)) conflicts.add(key)
        return (if (choices[key] == false) remote else local)?.deepCopy()
    }

    private fun objects(base: JsonObject?, local: JsonObject, remote: JsonObject, path: String,
        choices: Map<String, Boolean>, conflicts: MutableList<String>): JsonObject = JsonObject().apply {
        val keys = (base?.keySet().orEmpty() + local.keySet() + remote.keySet()).sorted()
        for (key in keys) {
            val childPath = if (path.isBlank()) key else "$path.$key"
            val merged = node(base?.get(key), local[key], remote[key], childPath, choices, conflicts)
            if (merged != null) add(key, merged)
        }
    }

    private fun arrays(base: JsonArray?, local: JsonArray, remote: JsonArray, path: String,
        choices: Map<String, Boolean>, conflicts: MutableList<String>): JsonArray? {
        val all = base.orEmpty() + local + remote
        if (path == "memberIds" && all.all { it.isJsonPrimitive && it.asJsonPrimitive.isString }) {
            val before = base.orEmpty().map { it.asString }.toSet()
            val mine = local.map { it.asString }.toSet()
            val theirs = remote.map { it.asString }.toSet()
            return JsonArray().apply {
                (before + mine + theirs).sorted().filter { id ->
                    if ((id in mine) == (id in before)) id in theirs else id in mine
                }.forEach(::add)
            }
        }
        val identity = listOf("id", "personId").firstOrNull { key ->
            all.isNotEmpty() && all.all { it.isJsonObject && it.asJsonObject[key]?.isJsonPrimitive == true }
        } ?: return null
        fun index(items: JsonArray?): Map<String, JsonElement> {
            val result = linkedMapOf<String, JsonElement>()
            for (item in items.orEmpty()) {
                val id = item.asJsonObject[identity].asString
                require(result.put(id, item) == null) { "同步子项标识重复" }
            }
            return result
        }
        val baseline = index(base)
        val mine = index(local)
        val theirs = index(remote)
        return JsonArray().apply {
            for (id in (baseline.keys + mine.keys + theirs.keys).sorted()) {
                node(baseline[id], mine[id], theirs[id], "$path[$id]", choices, conflicts)?.let(::add)
            }
        }
    }

    private fun JsonArray?.orEmpty(): List<JsonElement> = this?.toList().orEmpty()
}
