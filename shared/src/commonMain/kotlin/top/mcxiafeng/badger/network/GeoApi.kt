package top.mcxiafeng.badger.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.utils.SafeLog

/**
 * 高德地图代理 endpoints（`/api/proxy/amap` 域，Badger-Server AmapProxyModule）。
 *
 * 与 short.io 代理同构：成功响应是<b>裸 JSON</b>（不是 ApiResult 外壳）；失败由服务端写
 * 4xx/5xx + ApiResult.error 体，[ApiCore.ensureOk] 统一抛 [ApiException]。
 * Key 只存服务端，本域请求不携带任何高德凭据。
 *
 * 归一化契约（服务端 AmapService 归一， camelCase、坐标为 GCJ-02 数值）：
 * - `{count, pois:[{id,name,address,longitude,latitude,province,city,district,distance?}]}`
 * - regeo: `{formattedAddress, province, city, district, township, adcode, longitude, latitude, pois:[...]}`
 * - geocode: `{found, longitude?, latitude?, level?, province?, city?, district?, adcode?}`
 * - config: `{enabled, webKeySet, jsKey, jsSecurityCode}`
 */
internal class GeoApi(private val core: ApiCore) {

    /** GET /api/proxy/amap/config — 代理可用性 + 前端 JS API 配置（jsKey 公开性设计）。 */
    fun config(): AmapMapConfig {
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] amap.config")
        return core.execute(core.request("GET", "/api/proxy/amap/config")).use { resp ->
            core.ensureOk(resp, "amap.config")
            val obj = parseBodyObject(resp, "amap.config")
            AmapMapConfig.from(obj)
        }
    }

    /** GET /api/proxy/amap/regeo — 坐标（GCJ-02 "lng,lat"）→ 地址 + 附近 POI。 */
    fun regeo(location: String, radiusMeters: Int? = null): RegeoResult {
        val tag = core.nextCallTag()
        val path = buildPath("/api/proxy/amap/regeo") {
            param("location", location)
            radiusMeters?.let { param("radius", it.toString()) }
        }
        BadgerLog.d(TAG, "[$tag] amap.regeo: location=${SafeLog.url(location)}")
        return core.execute(core.request("GET", path)).use { resp ->
            core.ensureOk(resp, "amap.regeo")
            RegeoResult.from(parseBodyObject(resp, "amap.regeo"))
        }
    }

    /** GET /api/proxy/amap/place/text — POI 关键字搜索。 */
    fun poiText(
        keywords: String,
        region: String? = null,
        cityLimit: Boolean = false,
        pageNum: Int = 1,
        pageSize: Int = 10,
    ): AmapPoiPage {
        val tag = core.nextCallTag()
        val path = buildPath("/api/proxy/amap/place/text") {
            param("keywords", keywords)
            region?.takeIf { it.isNotBlank() }?.let {
                param("region", it)
                if (cityLimit) param("city_limit", "true")
            }
            param("pageNum", pageNum.toString())
            param("pageSize", pageSize.toString())
        }
        BadgerLog.d(TAG, "[$tag] amap.place.text: keywordsLen=${keywords.length} page=$pageNum")
        return core.execute(core.request("GET", path)).use { resp ->
            core.ensureOk(resp, "amap.place.text")
            AmapPoiPage.from(parseBodyObject(resp, "amap.place.text"))
        }
    }

    /** GET /api/proxy/amap/place/around — POI 周边搜索（服务端按距离排序）。 */
    fun poiAround(
        location: String,
        keywords: String? = null,
        radiusMeters: Int? = null,
        pageNum: Int = 1,
        pageSize: Int = 10,
    ): AmapPoiPage {
        val tag = core.nextCallTag()
        val path = buildPath("/api/proxy/amap/place/around") {
            param("location", location)
            keywords?.takeIf { it.isNotBlank() }?.let { param("keywords", it) }
            radiusMeters?.let { param("radius", it.toString()) }
            param("pageNum", pageNum.toString())
            param("pageSize", pageSize.toString())
        }
        BadgerLog.d(TAG, "[$tag] amap.place.around: location=${SafeLog.url(location)}")
        return core.execute(core.request("GET", path)).use { resp ->
            core.ensureOk(resp, "amap.place.around")
            AmapPoiPage.from(parseBodyObject(resp, "amap.place.around"))
        }
    }

    /** GET /api/proxy/amap/geocode — 结构化地址 → 坐标。 */
    fun geocode(address: String, city: String? = null): AmapGeoPoint {
        val tag = core.nextCallTag()
        val path = buildPath("/api/proxy/amap/geocode") {
            param("address", address)
            city?.takeIf { it.isNotBlank() }?.let { param("city", it) }
        }
        BadgerLog.d(TAG, "[$tag] amap.geocode: addressLen=${address.length}")
        return core.execute(core.request("GET", path)).use { resp ->
            core.ensureOk(resp, "amap.geocode")
            AmapGeoPoint.from(parseBodyObject(resp, "amap.geocode"))
        }
    }

    // ========== 响应解析（裸 JSON + 防御） ==========

    private fun parseBodyObject(resp: top.mcxiafeng.badger.network.ApiHttpResponse, what: String): JsonObject {
        val bodyStr = resp.bodyText ?: throw ApiException(resp.code, "$what: empty body", what)
        val obj = BadgerJson.parseToJsonElement(bodyStr) as? JsonObject
            ?: throw ApiException(resp.code, "$what: not an object", what)
        return obj
    }

    /**
     * GET 查询串拼接 + UTF-8 百分号编码（keywords/location 携带中文与逗号，
     * ApiCore 不做编码，必须在本层完成）。encoding：非保留字符直通，其余逐字节 %XX。
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

/** 代理归一化 POI 行。distance 仅 regeo 附近 POI 返回（米）。 */
@Serializable
data class AmapPoi(
    val id: String? = null,
    val name: String = "",
    val address: String? = null,
    val longitude: Double? = null,
    val latitude: Double? = null,
    val country: String? = null,
    val province: String? = null,
    val city: String? = null,
    val district: String? = null,
    val distance: Int? = null,
) {
    companion object {
        fun from(o: JsonObject): AmapPoi = AmapPoi(
            id = stringOrNull(o, "id"),
            name = stringOrNull(o, "name").orEmpty(),
            address = stringOrNull(o, "address"),
            longitude = doubleOr(o, "longitude"),
            latitude = doubleOr(o, "latitude"),
            country = stringOrNull(o, "country"),
            province = stringOrNull(o, "province"),
            city = stringOrNull(o, "city"),
            district = stringOrNull(o, "district"),
            distance = intOr(o["distance"], 0).takeIf { it > 0 },
        )
    }
}

/** 文字/周边搜索结果页。 */
@Serializable
data class AmapPoiPage(val count: Int = 0, val pois: List<AmapPoi> = emptyList()) {
    companion object {
        fun from(o: JsonObject): AmapPoiPage = AmapPoiPage(
            count = intOr(o["count"], 0),
            pois = jsonArrayOrNull(o, "pois")?.mapNotNull { el ->
                (el as? JsonObject)?.let { AmapPoi.from(it) }
            } ?: emptyList(),
        )
    }
}

/** 逆地理编码结果（地址组件 + 附近 POI）。 */
@Serializable
data class RegeoResult(
    val formattedAddress: String? = null,
    val country: String? = null,
    val province: String? = null,
    val city: String? = null,
    val district: String? = null,
    val township: String? = null,
    val adcode: String? = null,
    val longitude: Double? = null,
    val latitude: Double? = null,
    val pois: List<AmapPoi> = emptyList(),
) {
    companion object {
        fun from(o: JsonObject): RegeoResult = RegeoResult(
            formattedAddress = stringOrNull(o, "formattedAddress"),
            country = stringOrNull(o, "country"),
            province = stringOrNull(o, "province"),
            city = stringOrNull(o, "city"),
            district = stringOrNull(o, "district"),
            township = stringOrNull(o, "township"),
            adcode = stringOrNull(o, "adcode"),
            longitude = doubleOr(o, "longitude"),
            latitude = doubleOr(o, "latitude"),
            pois = jsonArrayOrNull(o, "pois")?.mapNotNull { el ->
                (el as? JsonObject)?.let { AmapPoi.from(it) }
            } ?: emptyList(),
        )
    }
}

/** 地理编码结果。found=false 表示未命中（正常态，非错误）。 */
@Serializable
data class AmapGeoPoint(
    val found: Boolean = false,
    val longitude: Double? = null,
    val latitude: Double? = null,
    val level: String? = null,
    val province: String? = null,
    val city: String? = null,
    val district: String? = null,
    val adcode: String? = null,
) {
    companion object {
        fun from(o: JsonObject): AmapGeoPoint = AmapGeoPoint(
            found = boolOr(o["found"], false),
            longitude = doubleOr(o, "longitude"),
            latitude = doubleOr(o, "latitude"),
            level = stringOrNull(o, "level"),
            province = stringOrNull(o, "province"),
            city = stringOrNull(o, "city"),
            district = stringOrNull(o, "district"),
            adcode = stringOrNull(o, "adcode"),
        )
    }
}

/** /config 响应：代理可用性 + JS API 配置（jsKey 随页面下发，公开性设计）。 */
@Serializable
data class AmapMapConfig(
    val enabled: Boolean = false,
    val webKeySet: Boolean = false,
    val jsKey: String = "",
    val jsSecurityCode: String = "",
) {
    companion object {
        fun from(o: JsonObject): AmapMapConfig = AmapMapConfig(
            enabled = boolOr(o["enabled"], false),
            webKeySet = boolOr(o["webKeySet"], false),
            jsKey = stringOrNull(o, "jsKey").orEmpty(),
            jsSecurityCode = stringOrNull(o, "jsSecurityCode").orEmpty(),
        )
    }
}

/** primitive 浮点缺省（服务端归一化后恒为数值；字符串数字兜底）。 */
private fun doubleOr(o: JsonObject, key: String): Double? {
    val v = o[key] as? kotlinx.serialization.json.JsonPrimitive ?: return null
    if (v is kotlinx.serialization.json.JsonNull) return null
    return v.content.toDoubleOrNull()
}
