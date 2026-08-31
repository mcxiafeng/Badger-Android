package top.mcxiafeng.badger.network

import android.util.Log
import com.google.gson.JsonObject
import org.koin.core.context.GlobalContext
import top.mcxiafeng.badger.data.repository.ServerApiFactory
import top.mcxiafeng.badger.utils.SafeLog

data class IdentifyResponse(
    val kind: String,
    val name: String?,
    val avatarUrl: String?,
    val description: String?,
    val contactMap: Map<String, String>,
)

/** Server-authoritative identification via the canonical POST /api/resolve/ contract. */
object ContactNetworkResolver {

    private const val TAG = "ContactNetworkResolver"
    private const val MAX_BATCH_SIZE = 50

    private fun api(): ServerApi =
        GlobalContext.get().get<ServerApiFactory>().get()

    fun identify(input: String): IdentifyResponse? {
        if (input.isBlank()) return null
        return try {
            identifyWith(api(), input)
        } catch (e: Exception) {
            Log.w(TAG, "identify failed for ${SafeLog.unknown(input)}", e)
            null
        }
    }

    fun identifyBatch(inputs: List<String>): List<IdentifyResponse?> {
        if (inputs.isEmpty()) return emptyList()
        return try {
            identifyBatchInternal(api(), inputs, "identifyBatch")
        } catch (e: Exception) {
            Log.w(TAG, "identifyBatch api initialization failed", e)
            List(inputs.size) { null }
        }
    }

    private fun parseOne(obj: JsonObject?): IdentifyResponse? {
        if (obj == null) return null

        val kind = obj.get("platform")
            ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
            ?.asString
            ?.takeIf { it.isNotBlank() }
            ?: "unknown"
        val name = obj.get("name")
            ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
            ?.asString
        val description = obj.get("description")
            ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
            ?.asString
        val avatarUrl = obj.get("avatarUrl")
            ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
            ?.asString

        val contacts = obj.get("contacts")
            ?.takeIf { !it.isJsonNull && it.isJsonObject }
            ?.asJsonObject
        val contactMap = contacts
            ?.entrySet()
            ?.filter { !it.value.isJsonNull && it.value.isJsonPrimitive }
            ?.associate { it.key to it.value.asString }
            ?: emptyMap()

        val status = obj.get("status")
            ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
            ?.asString
        if (status == "error") {
            val error = obj.get("error")
                ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
                ?.asString
            Log.w(TAG, "resolve returned error: kind=$kind error=$error")
        }

        return IdentifyResponse(
            kind = kind,
            name = name,
            avatarUrl = avatarUrl,
            description = description,
            contactMap = contactMap,
        )
    }

    internal fun identifyWith(api: ServerApi, input: String): IdentifyResponse? {
        if (input.isBlank()) return null
        return try {
            parseOne(api.resolveIdentify(input))
        } catch (e: Exception) {
            Log.w(TAG, "identify failed for ${SafeLog.unknown(input)}", e)
            null
        }
    }

    internal fun identifyBatchWith(api: ServerApi, inputs: List<String>): List<IdentifyResponse?> {
        if (inputs.isEmpty()) return emptyList()
        return identifyBatchInternal(api, inputs, "identifyBatchWith")
    }

    private fun identifyBatchInternal(
        api: ServerApi,
        inputs: List<String>,
        caller: String,
    ): List<IdentifyResponse?> {
        val indexed = inputs.withIndex().filter { it.value.isNotBlank() }
        if (indexed.isEmpty()) return List(inputs.size) { null }

        val out = arrayOfNulls<IdentifyResponse>(inputs.size)
        indexed.chunked(MAX_BATCH_SIZE).forEach { chunk ->
            val cleanList = chunk.map { it.value }
            val raws = try {
                api.resolveIdentifyBatch(cleanList)
            } catch (e: Exception) {
                Log.w(TAG, "$caller chunk size=${cleanList.size} failed", e)
                List(cleanList.size) { null }
            }
            if (raws.size != cleanList.size) {
                Log.w(TAG, "$caller result size mismatch: requested=${cleanList.size} got=${raws.size}")
            }
            chunk.forEachIndexed { index, (originalIndex, _) ->
                out[originalIndex] = parseOne(raws.getOrNull(index))
            }
        }
        Log.d(TAG, "$caller: requested=${indexed.size} got=${out.count { it != null }}")
        return out.toList()
    }
}
