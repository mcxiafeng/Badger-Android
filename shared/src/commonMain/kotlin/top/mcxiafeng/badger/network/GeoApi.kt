package top.mcxiafeng.badger.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import top.mcxiafeng.badger.utils.BadgerLog

/**
 * 高德地图代理 endpoint（`/api/proxy/amap/district`，Badger-Server AmapProxyModule）。
 *
 * 与 short.io 代理同构：成功响应是<b>裸 JSON</b>（不是 ApiResult 外壳）；失败由服务端写
 * 4xx/5xx + ApiResult.error 体，[ApiCore.ensureOk] 统一抛 [ApiException]。
 * Key 只存服务端，本域请求不携带任何高德凭据。
 *
 * 用途唯一：国家/地区填写——选中国后的省→市→区级行政区划级联，无需坐标与 POI。
 * 服务端 [精度封顶到区]：level=district 即叶子，不下发街道级。
 *
 * 归一化契约（服务端 AmapService 归一）：
 * - `{districts:[{adcode,name,level}]}`（仅下一级，扁平；level ∈ province/city/district）
 */
internal class GeoApi(private val core: ApiCore) {

    /** GET /api/proxy/amap/district — adcode 的下一级子区划；adcode 为空 = 中国省级列表。 */
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

    // ========== 响应解析（裸 JSON + 防御） ==========

    private fun parseBodyObject(resp: ApiHttpResponse, what: String): JsonObject {
        val bodyStr = resp.bodyText ?: throw ApiException(resp.code, "$what: empty body", what)
        val obj = BadgerJson.parseToJsonElement(bodyStr) as? JsonObject
            ?: throw ApiException(resp.code, "$what: not an object", what)
        return obj
    }

    /**
     * GET 查询串拼接 + UTF-8 百分号编码（ApiCore 不做编码，必须在本层完成）。
     * encoding：非保留字符直通，其余逐字节 %XX。
     */
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

        /** RFC 3986 unreserved 之外的字符按 UTF-8 百分号编码（空格 → %20）。 */
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

// ========== DTO ==========

/** 行政区划行。level ∈ province/city/district（客户端以 district 为叶子，最多到区）。 */
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

/** /district 响应页。 */
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
