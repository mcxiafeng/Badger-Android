package top.mcxiafeng.badger.network

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.utils.SafeLog

// [KMP K08-B] IdentifyResponse/NetworkResolveResult 纯数据模型迁 shared commonMain（ResolveModels.kt）。
// [KMP K13b] 静态门面（@Deprecated identify/identifyBatch/getResultInfo）随 JVM 专属
// KoinJavaComponent 一并退役——调用方改为 Koin 注入实例。

/** Server-authoritative identification via the canonical POST /api/resolve/ contract. */
class ContactNetworkResolver(
    private val serverApi: ServerApi,
) {
    companion object {
        const val TAG = "ContactNetworkResolver"
        const val MAX_BATCH_SIZE = 50
    }

    fun identify(input: String): IdentifyResponse? = identifyWith(serverApi, input)

    fun identifyBatch(inputs: List<String>): List<IdentifyResponse?> =
        identifyBatchWith(serverApi, inputs)

    private fun parseOne(obj: JsonObject?): IdentifyResponse? {
        if (obj == null) return null
        fun primitive(key: String): String? = (obj[key] as? JsonPrimitive)?.content
        val kind = primitive("platform")?.takeIf { it.isNotBlank() } ?: "unknown"
        val name = primitive("name")
        val description = primitive("description")
        val avatarUrl = primitive("avatarUrl")
        val contactMap = (obj["contacts"] as? JsonObject)
            ?.filterValues { it is JsonPrimitive }
            ?.mapValues { (it.value as JsonPrimitive).content }
            ?: emptyMap()
        val status = primitive("status")
        if (status == "error") {
            val error = primitive("error")
            BadgerLog.w(TAG, "resolve returned error: kind=$kind error=$error")
        }
        return IdentifyResponse(kind, name, avatarUrl, description, contactMap)
    }

    internal fun identifyWith(api: ServerApi, input: String): IdentifyResponse? {
        if (input.isBlank()) return null
        return try {
            parseOne(api.resolveIdentify(input))
        } catch (e: Exception) {
            BadgerLog.w(TAG, "identify failed for ${SafeLog.unknown(input)}", e)
            null
        }
    }

    internal fun identifyBatchWith(
        api: ServerApi,
        inputs: List<String>,
    ): List<IdentifyResponse?> {
        if (inputs.isEmpty()) return emptyList()
        val indexed = inputs.withIndex().filter { it.value.isNotBlank() }
        if (indexed.isEmpty()) return List(inputs.size) { null }
        val out = arrayOfNulls<IdentifyResponse>(inputs.size)
        indexed.chunked(MAX_BATCH_SIZE).forEach { chunk ->
            val cleanList = chunk.map { it.value }
            val raws = try {
                api.resolveIdentifyBatch(cleanList)
            } catch (e: Exception) {
                BadgerLog.w(TAG, "identifyBatch chunk size=${cleanList.size} failed", e)
                List(cleanList.size) { null }
            }
            if (raws.size != cleanList.size) {
                BadgerLog.w(TAG, "identifyBatch result size mismatch: requested=${cleanList.size} got=${raws.size}")
            }
            chunk.forEachIndexed { index, (originalIndex, _) ->
                out[originalIndex] = parseOne(raws.getOrNull(index))
            }
        }
        BadgerLog.d(TAG, "identifyBatch: requested=${indexed.size} got=${out.count { it != null }}")
        return out.toList()
    }
}
