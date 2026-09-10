package top.mcxiafeng.badger.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import top.mcxiafeng.badger.network.stringOrNull
import top.mcxiafeng.badger.utils.BadgerLog

/**
 * 联系人「位置」字段值（高德选点，GCJ-02 坐标系）。
 *
 * 三端共享契约（`Profile.location`，服务端 `Profile.LocationInfo` 同形）：
 * - 本地存储：`contact_field_value_cache`，fieldKey="location"，值为 [encode] 的 JSON 字符串；
 * - 云端存储：`Person.Profile.location` 整段 JSON 对象（`ProfileDto.location` JsonObject 透传）；
 * - Web 前端：同形对象直读直写。
 *
 * @property name     位置名称（POI 名或用户标记）
 * @property address  详细地址
 * @property longitude 经度（GCJ-02）
 * @property latitude  纬度（GCJ-02）
 * @property province 省/直辖市
 * @property city     城市（直辖市可能为空）
 * @property district 区县
 * @property poiId    高德 POI ID（搜索选点时存在）
 * @property source   来源：poi=搜索选点 / current=当前定位 / manual=手动输入
 */
@Serializable
data class ContactLocation(
    val name: String = "",
    val address: String = "",
    val longitude: Double? = null,
    val latitude: Double? = null,
    val province: String? = null,
    val city: String? = null,
    val district: String? = null,
    /** 国家（国内=中国；OSM 国际结果带原语言国名）。 */
    val country: String? = null,
    val poiId: String? = null,
    /** 距定位中心的距离（米，周边搜索时存在；仅选择器展示用，不写入契约）。 */
    val distanceMeters: Int? = null,
    val source: String = SOURCE_POI,
) {
    companion object {
        const val SOURCE_POI = "poi"
        const val SOURCE_CURRENT = "current"
        const val SOURCE_MANUAL = "manual"

        private const val TAG = "ContactLocation"

        /**
         * 防御解析：服务端 Profile.location 是嵌套 JSON 对象（不可信第三方/历史数据）。
         * 解析失败返回 null（字段退化为未设置，不炸调用方）。
         */
        fun fromJsonObject(o: JsonObject?): ContactLocation? {
            if (o == null) return null
            return runCatching {
                val longitude = (o["longitude"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
                val latitude = (o["latitude"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
                ContactLocation(
                    name = stringOrNull(o, "name").orEmpty(),
                    address = stringOrNull(o, "address").orEmpty(),
                    longitude = longitude,
                    latitude = latitude,
                    province = stringOrNull(o, "province"),
                    city = stringOrNull(o, "city"),
                    district = stringOrNull(o, "district"),
                    country = stringOrNull(o, "country"),
                    poiId = stringOrNull(o, "poiId"),
                    distanceMeters = (o["distanceMeters"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull(),
                    source = stringOrNull(o, "source").takeIf { it in setOf(SOURCE_POI, SOURCE_CURRENT, SOURCE_MANUAL) }
                        ?: SOURCE_POI,
                )
            }.onFailure {
                BadgerLog.w(TAG, "fromJsonObject 解析失败: ${it.message}")
            }.getOrNull()?.takeIf { it.isNotBlankLocation() }
        }

        /** 代理 POI 归一化行（GeoApi 返回）→ ContactLocation。 */
        fun fromPoi(o: JsonObject, source: String): ContactLocation? {
            val longitude = doubleOf(o, "longitude") ?: return null
            val latitude = doubleOf(o, "latitude") ?: return null
            return ContactLocation(
                name = stringOrNull(o, "name").orEmpty(),
                address = stringOrNull(o, "address").orEmpty(),
                longitude = longitude,
                latitude = latitude,
                province = stringOrNull(o, "province"),
                city = stringOrNull(o, "city"),
                district = stringOrNull(o, "district"),
                country = stringOrNull(o, "country"),
                poiId = stringOrNull(o, "id"),
                distanceMeters = intOrNullOf(o, "distance"),
                source = source,
            )
        }
    }

    /** 无任何有效内容的位置值视为空（脏数据兜底）。 */
    fun isNotBlankLocation(): Boolean = name.isNotBlank() || address.isNotBlank() || (longitude != null && latitude != null)

    /** 列表行主标题：名称优先，退地址。 */
    fun displayTitle(): String = name.ifBlank { address.ifBlank { "未知位置" } }

    /** 列表行副标题：名称存在时展示地址，否则展示行政区；有距离时前置。 */
    fun displaySubtitle(): String? {
        // name 存在时优先展示 address（服务端已组合 国家+省+市+区）；否则自组合含国家
        val region = if (name.isNotBlank() && address.isNotBlank()) address
        else listOfNotNull(country, province, city?.takeIf { it != province }, district).joinToString("")
        val distance = distanceMeters?.let { formatDistance(it) }
        return when {
            distance != null && region != null -> "$distance · $region"
            distance != null -> distance
            else -> region
        }
    }

    override fun toString(): String = displayTitle()
}

/** 距离文案：<1km 显示米，≥1km 显示公里（一位小数内）。 */
fun formatDistance(meters: Int): String = when {
    meters < 1000 -> "${meters}米"
    meters < 10_000 -> {
        val km = meters / 100.0
        val rounded = (kotlin.math.round(km) / 10.0)
        if (rounded == kotlin.math.floor(rounded)) "${rounded.toInt()}公里" else "${rounded}公里"
    }
    else -> "${meters / 1000}公里"
}

/** JsonObject 数字字段防御取值（上游/历史数据可能是字符串数字或脏类型）。 */
private fun doubleOf(o: JsonObject, key: String): Double? =
    (o[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()

/** JsonObject 整数字段防御取值（"123.0" 形态收敛为整数）。 */
private fun intOrNullOf(o: JsonObject, key: String): Int? =
    (o[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()
