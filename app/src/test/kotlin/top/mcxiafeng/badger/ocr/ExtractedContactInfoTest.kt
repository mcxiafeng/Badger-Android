package top.mcxiafeng.badger.ocr

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExtractedContactInfoTest {

    @Test
    fun toFieldValues_phoneOnly_mapsToPhone() {
        val info = ExtractedContactInfo(phone = "13800138000")
        val result = info.toFieldValues()
        assertThat(result).containsEntry("phone", "13800138000")
        assertThat(result).hasSize(1)
    }

    @Test
    fun toFieldValues_emailOnly_mapsToEmail() {
        val info = ExtractedContactInfo(email = "test@example.com")
        val result = info.toFieldValues()
        assertThat(result).containsEntry("email", "test@example.com")
        assertThat(result).hasSize(1)
    }

    @Test
    fun toFieldValues_platformsOnly_mapsAllPlatforms() {
        val info = ExtractedContactInfo(platforms = mapOf("qq" to "12345", "bilibili" to "67890"))
        val result = info.toFieldValues()
        assertThat(result).containsEntry("qq", "12345")
        assertThat(result).containsEntry("bilibili", "67890")
        assertThat(result).hasSize(2)
    }

    @Test
    fun toFieldValues_allFields_mergesSystemAndPlatforms() {
        val info = ExtractedContactInfo(
            name = "张三",
            phone = "13800138000",
            email = "zhangsan@example.com",
            platforms = mapOf("qq" to "12345", "github" to "zhangsan")
        )
        val result = info.toFieldValues()
        assertThat(result).containsEntry("phone", "13800138000")
        assertThat(result).containsEntry("email", "zhangsan@example.com")
        assertThat(result).containsEntry("qq", "12345")
        assertThat(result).containsEntry("github", "zhangsan")
        assertThat(result).hasSize(4)
        assertThat(result).doesNotContainKey("name")
    }

    @Test
    fun toFieldValues_noFields_returnsEmpty() {
        val info = ExtractedContactInfo(name = "张三")
        val result = info.toFieldValues()
        assertThat(result).isEmpty()
    }

    @Test
    fun toFieldValues_platformsOverrideApplied() {
        // putAll(platforms) runs after phone/email, so platforms can override
        val info = ExtractedContactInfo(
            phone = "13800138000",
            platforms = mapOf("phone" to "99999")
        )
        val result = info.toFieldValues()
        assertThat(result["phone"]).isEqualTo("99999")
    }
}
