package top.mcxiafeng.badger.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import top.mcxiafeng.badger.utils.BadgerLog

internal class GeoApi(private val core: ApiCore) {

    
    fun district(adcode: String?): AmapDistrictPage {
        val tag = core.nextCallTag()
        val path = buildPath("/api/proxy/amap/district") {
            adcode?.takeIf { it.isNotBlank() }?.let { param("adcode", it) }
        }
        BadgerLog.d(TAG, "[$tag] amap.district: adcodeLen=${adcode?.length ?: 0}")
        return core.execute(core.request("GET", path)).use { resp ->
            core.ensureOk(resp, "amap.district")
            AmapDistrictPage.from(parseBodyObject(resp, "amap.district"))
        }
    }

    

    private fun parseBodyObject(resp: ApiHttpResponse, what: String): JsonObject {
        val bodyStr = resp.bodyText ?: throw ApiException(resp.code, "$what: empty body", what)
        val obj = BadgerJson.parseToJsonElement(bodyStr) as? JsonObject
            ?: throw ApiException(resp.code, "$what: not an object", what)
        return obj
    }

    

    private inline fun buildPath(base: String, build: QueryBuilder.() -> Unit): String {
        val q = QueryBuilder().apply(build).build()
        return if (q.isBlank()) base else "$base?$q"
    }

    private class QueryBuilder {
        private val parts = mutableListOf<String>()

        fun param(key: String, value: String) {
            parts.add(encodeURIComponent(key) + "=" + encodeURIComponent(value))
        }

        fun build(): String = parts.joinToString("&")
    }

    private companion object {
        const val TAG = ApiCore.TAG

        private val UNRESERVED = ("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
            + "-_.~").toHashSet()

        
        fun encodeURIComponent(raw: String): String {
            val sb = StringBuilder(raw.length)
            for (b in raw.encodeToByteArray()) {
                val c = b.toInt() and 0xFF
                val ch = b.toInt().toChar()
                if (ch in UNRESERVED) {
                    sb.append(ch)
                } else {
                    sb.append('%')
                    val hex = c.toString(16).uppercase()
                    if (hex.length == 1) sb.append('0')
                    sb.append(hex)
                }
            }
            return sb.toString()
        }
    }
}

@Serializable
data class AmapDistrict(
    val adcode: String = "",
    val name: String = "",
    val level: String = "",
) {
    companion object {
        fun from(o: JsonObject): AmapDistrict = AmapDistrict(
            adcode = stringOrNull(o, "adcode").orEmpty(),
            name = stringOrNull(o, "name").orEmpty(),
            level = stringOrNull(o, "level").orEmpty(),
        )
    }
}

@Serializable
data class AmapDistrictPage(val districts: List<AmapDistrict> = emptyList()) {
    companion object {
        fun from(o: JsonObject): AmapDistrictPage = AmapDistrictPage(
            districts = jsonArrayOrNull(o, "districts")?.mapNotNull { el ->
                (el as? JsonObject)?.let { AmapDistrict.from(it) }
            } ?: emptyList(),
        )
    }
}
