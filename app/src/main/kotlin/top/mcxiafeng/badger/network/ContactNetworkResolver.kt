package top.mcxiafeng.badger.network

import android.util.Log
import com.google.gson.JsonObject
import top.mcxiafeng.badger.di.KoinComponentBy
import top.mcxiafeng.badger.utils.SafeLog

data class IdentifyResponse(
    val kind: String,
    val name: String?,
    val avatarUrl: String?,
    val description: String?,
    val contactMap: Map<String, String>,
) {
    /** Legacy scanner/UI aliases kept while consumers migrate to server-side `kind`. */
    val nickname: String? get() = name
    val signature: String? get() = description
    val type: ContactType get() = kindToContactType(kind) ?: ContactType.None
}

/** Legacy projection used by older scanner UI call sites. */
data class NetworkResolveResult(
    val nickname: String? = null,
    val description: String? = null,
    val avatarUrl: String? = null,
    val contactMap: Map<String, String> = emptyMap(),
    val type: ContactType = ContactType.None,
    val kind: String? = null,
    val name: String? = null,
) {
    val signature: String? get() = description
}

/** Server-authoritative identification via the canonical POST /api/resolve/ contract. */
class ContactNetworkResolver(
    private val serverApi: ServerApi,
) {
    companion object {
        const val TAG = "ContactNetworkResolver"
        const val MAX_BATCH_SIZE = 50

        /**
         * Deprecated compatibility entry point for legacy UI call sites.
         * New code should inject [ContactNetworkResolver] and call its instance methods.
         */
        @Deprecated(
            message = "Inject ContactNetworkResolver and call identify() instead.",
            level = DeprecationLevel.WARNING,
        )
        @JvmStatic
        fun identify(input: String): IdentifyResponse? =
            KoinComponentBy.get<ContactNetworkResolver>().identify(input)

        /** Deprecated compatibility entry point; prefer constructor injection. */
        @Deprecated(
            message = "Inject ContactNetworkResolver and call identifyBatch() instead.",
            level = DeprecationLevel.WARNING,
        )
        @JvmStatic
        fun identifyBatch(inputs: List<String>): List<IdentifyResponse?> =
            KoinComponentBy.get<ContactNetworkResolver>().identifyBatch(inputs)

        /** Deprecated compatibility entry point; prefer constructor injection. */
        @Deprecated(
            message = "Inject ContactNetworkResolver and call identify() instead.",
            level = DeprecationLevel.WARNING,
        )
        @JvmStatic
        fun getResultInfo(
            input: String,
            existing: Map<String, String> = emptyMap(),
            type: ContactType? = null,
        ): IdentifyResponse? {
            // `existing` and `type` remain for source compatibility. The server response is
            // authoritative and therefore intentionally takes precedence over local hints.
            return KoinComponentBy.get<ContactNetworkResolver>().identify(input)
        }
    }

    fun identify(input: String): IdentifyResponse? = identifyWith(serverApi, input)

    fun identifyBatch(inputs: List<String>): List<IdentifyResponse?> =
        identifyBatchWith(serverApi, inputs)

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
        val contactMap = obj.get("contacts")
            ?.takeIf { !it.isJsonNull && it.isJsonObject }
            ?.asJsonObject
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
        return IdentifyResponse(kind, name, avatarUrl, description, contactMap)
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
                Log.w(TAG, "identifyBatch chunk size=${cleanList.size} failed", e)
                List(cleanList.size) { null }
            }
            if (raws.size != cleanList.size) {
                Log.w(
                    TAG,
                    "identifyBatch result size mismatch: requested=${cleanList.size} got=${raws.size}",
                )
            }
            chunk.forEachIndexed { index, (originalIndex, _) ->
                out[originalIndex] = parseOne(raws.getOrNull(index))
            }
        }
        Log.d(TAG, "identifyBatch: requested=${indexed.size} got=${out.count { it != null }}")
        return out.toList()
    }
}
