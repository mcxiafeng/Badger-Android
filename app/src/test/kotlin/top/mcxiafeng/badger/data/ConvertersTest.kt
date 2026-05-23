package top.mcxiafeng.badger.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun fromPlatformsMap_null_returnsNull() {
        assertThat(converters.fromPlatformsMap(null)).isNull()
    }

    @Test
    fun fromPlatformsMap_emptyMap_returnsJson() {
        val result = converters.fromPlatformsMap(emptyMap())
        assertThat(result).isNotNull()
    }

    @Test
    fun fromPlatformsMap_validMap_serializesAndDeserializes() {
        val map = mapOf(
            "qq" to PlatformEntry(displayName = "测试", jumpLink = "https://qq.com/123", value = "123")
        )
        val json = converters.fromPlatformsMap(map)
        assertThat(json).isNotNull()
        val restored = converters.toPlatformsMap(json)
        assertThat(restored).hasSize(1)
        assertThat(restored!!["qq"]?.jumpLink).isEqualTo("https://qq.com/123")
    }

    @Test
    fun toPlatformsMap_invalidJson_throwsException() {
        try {
            converters.toPlatformsMap("not valid json")
            assertThat(true).isFalse()
        } catch (e: Exception) {
            assertThat(e).isInstanceOf(com.google.gson.JsonSyntaxException::class.java)
        }
    }

    @Test
    fun toPlatformsMap_null_returnsNull() {
        assertThat(converters.toPlatformsMap(null)).isNull()
    }
}