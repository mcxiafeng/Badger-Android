package top.mcxiafeng.badger.data.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.repository.ContactLocationStore

/**
 * 位置值三端契约的编解码面：
 * 字段值字符串 ↔ ContactLocation ↔ Profile.location JSON 对象（服务端 Profile.LocationInfo 同形）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ContactLocationTest {

    @Test
    fun encodeDecodeRoundTrip() {
        val location = ContactLocation(
            name = "望京SOHO",
            address = "北京市朝阳区阜通东大街6号",
            longitude = 116.481028,
            latitude = 39.989643,
            province = "北京市",
            city = "北京市",
            district = "朝阳区",
            poiId = "B000A8UIN8",
            source = ContactLocation.SOURCE_POI,
        )
        val decoded = ContactLocationStore.decode(ContactLocationStore.encode(location))
        assertThat(decoded).isEqualTo(location)
    }

    /** 服务端下行（嵌套 JSON 对象，数字可为字符串形态）→ ContactLocation。 */
    @Test
    fun fromServerJsonObject() {
        val json = buildJsonObject {
            put("name", "方恒国际中心")
            put("address", "阜通东大街6号")
            put("longitude", 116.481028)
            put("latitude", 39.989643)
            put("city", "北京市")
            put("source", "current")
        }
        val decoded = ContactLocation.fromJsonObject(json)
        assertThat(decoded).isNotNull()
        assertThat(decoded!!.name).isEqualTo("方恒国际中心")
        assertThat(decoded.longitude).isWithin(1e-9).of(116.481028)
        assertThat(decoded.source).isEqualTo(ContactLocation.SOURCE_CURRENT)
    }

    /** 非法/缺失坐标的 poi 行 → null（不产出半成品位置）。 */
    @Test
    fun fromPoiWithoutCoordinatesReturnsNull() {
        val json = buildJsonObject {
            put("id", "B000A8UIN8")
            put("name", "脏数据")
        }
        assertThat(ContactLocation.fromPoi(json, ContactLocation.SOURCE_POI)).isNull()
    }

    /** 脏字符串 / 空值解码 → null（防御，不抛异常）。 */
    @Test
    fun decodeDefensive() {
        assertThat(ContactLocationStore.decode(null)).isNull()
        assertThat(ContactLocationStore.decode("")).isNull()
        assertThat(ContactLocationStore.decode("not-json")).isNull()
        assertThat(ContactLocationStore.decode("""{"name":""}""")).isNull()
    }

    /** toJsonElement 输出键与服务端 LocationInfo 字段对齐（camelCase）。 */
    @Test
    fun toJsonElementUsesServerFieldNames() {
        val json = ContactLocationStore.toJsonElement(
            ContactLocation(name = "家", longitude = 116.0, latitude = 39.9),
        )
        assertThat(json.containsKey("name")).isTrue()
        assertThat(json.containsKey("longitude")).isTrue()
        assertThat(json.containsKey("latitude")).isTrue()
        assertThat(json.containsKey("poiId")).isFalse()
    }

    /** 字段值字符串 → Profile.location JSON 对象一步到位；脏值 → null（云端清空语义）。 */
    @Test
    fun fieldValueToJsonElementBridges() {
        val location = ContactLocation(name = "公司", address = "某路1号", longitude = 1.0, latitude = 2.0)
        val json = ContactLocationStore.fieldValueToJsonElement(ContactLocationStore.encode(location))
        assertThat(json).isNotNull()
        assertThat(json!!.getStringOrEmpty("name")).isEqualTo("公司")
        assertThat(ContactLocationStore.fieldValueToJsonElement("junk")).isNull()
    }

    private fun kotlinx.serialization.json.JsonObject.getStringOrEmpty(key: String): String =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
}
