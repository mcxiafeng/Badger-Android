package top.mcxiafeng.badger.data.repository

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import top.mcxiafeng.badger.data.repository.ContactMapper.toPersonProfileEntity
import top.mcxiafeng.badger.network.ProfileDto

/**
 * [Phase 2] ContactMapper 单元测试。
 *
 * 覆盖 `ProfileDto.toPersonProfileEntity` 映射：
 * - 全字段非 null → PersonProfileCacheEntity 字段一一对应
 * - null 字段 → PersonProfileCacheEntity 对应字段为 null
 * - extra JsonObject → toString() 序列化
 */
class ContactMapperTest {

    @Test
    fun toPersonProfileEntity_allFields_mapsCorrectly() {
        val extra = buildJsonObject {
            put("key", buildJsonObject { put("nested", "value") })
        }
        val profile = ProfileDto(
            sex = "male",
            avatarURL = "https://example.com/avatar.jpg",
            backgroundURL = "https://example.com/bg.jpg",
            description = "test bio",
            country = "CN",
            region = "Beijing",
            birthday = "2000-01-01",
            contactMap = mapOf("qq" to "123"),
            extra = extra,
        )

        val entity = profile.toPersonProfileEntity("uuid-1234")

        assertThat(entity.contactServerId).isEqualTo("uuid-1234")
        assertThat(entity.sex).isEqualTo("male")
        assertThat(entity.country).isEqualTo("CN")
        assertThat(entity.region).isEqualTo("Beijing")
        assertThat(entity.birthday).isEqualTo("2000-01-01")
        assertThat(entity.backgroundURL).isEqualTo("https://example.com/bg.jpg")
        assertThat(entity.extra).isNotNull()
        assertThat(entity.extra).contains("key")
    }

    @Test
    fun toPersonProfileEntity_nullFields_mapsToNull() {
        val profile = ProfileDto(
            sex = null,
            avatarURL = null,
            backgroundURL = null,
            description = null,
            country = null,
            region = null,
            birthday = null,
            contactMap = emptyMap(),
            extra = null,
        )

        val entity = profile.toPersonProfileEntity("uuid-5678")

        assertThat(entity.contactServerId).isEqualTo("uuid-5678")
        assertThat(entity.sex).isNull()
        assertThat(entity.country).isNull()
        assertThat(entity.region).isNull()
        assertThat(entity.birthday).isNull()
        assertThat(entity.backgroundURL).isNull()
        assertThat(entity.extra).isNull()
    }

    @Test
    fun toPersonProfileEntity_extraToString_preservesJson() {
        val extra = buildJsonObject { put("foo", "bar") }
        val profile = ProfileDto(extra = extra)

        val entity = profile.toPersonProfileEntity("uuid-test")

        assertThat(entity.extra).isNotNull()
        assertThat(entity.extra).contains("foo")
        assertThat(entity.extra).contains("bar")
    }
}
