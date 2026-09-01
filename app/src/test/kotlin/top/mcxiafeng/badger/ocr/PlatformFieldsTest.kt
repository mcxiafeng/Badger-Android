package top.mcxiafeng.badger.ocr

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlatformFieldsTest {

    @Test
    fun `AUTO platform builds URL from account`() {
        assertThat(buildPlatformLink("github", "octocat"))
            .isEqualTo("https://github.com/octocat")
    }

    @Test
    fun `AUTO platform preserves an already complete URL`() {
        val input = "https://github.com/octocat"
        assertThat(buildPlatformLink("github", input)).isEqualTo(input)
    }

    @Test
    fun `AUTO platform still strips leading at sign from account`() {
        assertThat(buildPlatformLink("telegram", "@octocat"))
            .isEqualTo("https://t.me/octocat")
    }

    @Test
    fun `LINK_ONLY platform keeps non-url input unchanged`() {
        assertThat(buildPlatformLink("douyin", "not-a-url"))
            .isEqualTo("not-a-url")
    }

    @Test
    fun `NO_LINK platform keeps raw identifier unchanged`() {
        assertThat(buildPlatformLink("wechat", "wxid_123"))
            .isEqualTo("wxid_123")
    }
}
