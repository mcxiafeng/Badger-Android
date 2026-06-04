package top.mcxiafeng.badger.network

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.ocr.ExtractedContactInfo

@RunWith(RobolectricTestRunner::class)
class ContactNetworkResolverTest {

    // ========== extractBiliUid ==========

    @Test
    fun extractBiliUid_validUrl_returnsUid() {
        val result = PlatformNetworkMethods.extractBiliUid("https://space.bilibili.com/12345")
        assertThat(result).isEqualTo("12345")
    }

    @Test
    fun extractBiliUid_httpUrl_returnsUid() {
        val result = PlatformNetworkMethods.extractBiliUid("http://space.bilibili.com/67890")
        assertThat(result).isEqualTo("67890")
    }

    @Test
    fun extractBiliUid_urlWithExtraPath_returnsUid() {
        val result = PlatformNetworkMethods.extractBiliUid("https://space.bilibili.com/12345/favlist")
        assertThat(result).isEqualTo("12345")
    }

    @Test
    fun extractBiliUid_invalidUrl_returnsNull() {
        val result = PlatformNetworkMethods.extractBiliUid("https://www.bilibili.com/video/BV12345")
        assertThat(result).isNull()
    }

    @Test
    fun extractBiliUid_nonNumericId_returnsNull() {
        val result = PlatformNetworkMethods.extractBiliUid("https://space.bilibili.com/abc")
        assertThat(result).isNull()
    }

    // ========== extractQQCodeFromUrl ==========

    @Test
    fun extractQQCodeFromUrl_qzoneUrl_returnsCode() {
        val result = PlatformNetworkMethods.extractQQCodeFromUrl("https://qzone.qq.com/123456789")
        assertThat(result).isEqualTo("123456789")
    }

    @Test
    fun extractQQCodeFromUrl_uinParam_returnsCode() {
        val result = PlatformNetworkMethods.extractQQCodeFromUrl("https://example.com?uin=123456789")
        assertThat(result).isEqualTo("123456789")
    }

    @Test
    fun extractQQCodeFromUrl_trailingDigits_returnsCode() {
        val result = PlatformNetworkMethods.extractQQCodeFromUrl("https://some.qq.com/1234567/")
        assertThat(result).isEqualTo("1234567")
    }

    @Test
    fun extractQQCodeFromUrl_noMatch_returnsNull() {
        val result = PlatformNetworkMethods.extractQQCodeFromUrl("https://example.com/no-qq-here")
        assertThat(result).isNull()
    }

    // ========== extractTwitterUsername ==========

    @Test
    fun extractTwitterUsername_xUrl_returnsUsername() {
        val result = PlatformNetworkMethods.extractTwitterUsername("https://x.com/elonmusk")
        assertThat(result).isEqualTo("elonmusk")
    }

    @Test
    fun extractTwitterUsername_twitterUrl_returnsUsername() {
        val result = PlatformNetworkMethods.extractTwitterUsername("https://twitter.com/elonmusk")
        assertThat(result).isEqualTo("elonmusk")
    }

    @Test
    fun extractTwitterUsername_pureUsername_returnsUsername() {
        val result = PlatformNetworkMethods.extractTwitterUsername("elonmusk")
        assertThat(result).isEqualTo("elonmusk")
    }

    @Test
    fun extractTwitterUsername_tooLong_returnsNull() {
        val result = PlatformNetworkMethods.extractTwitterUsername("a".repeat(16))
        assertThat(result).isNull()
    }

    @Test
    fun extractTwitterUsername_invalidChars_returnsNull() {
        val result = PlatformNetworkMethods.extractTwitterUsername("user-name!")
        assertThat(result).isNull()
    }

    // ========== toContactAndInfo ==========

    @Test
    fun toContactAndInfo_buildsContactAndExtractedInfo() {
        val result = NetworkResolveResult(
            nickname = "张三",
            description = "测试签名",
            avatarUrl = "https://example.com/avatar.jpg",
            contactMap = mutableMapOf("qq" to "123456"),
            type = ContactType.QQ
        )
        val (contact, info) = ContactNetworkResolver.toContactAndInfo(result, "raw_content")

        assertThat(contact.name).isEqualTo("张三")
        assertThat(contact.avatarUrl).isEqualTo("https://example.com/avatar.jpg")
        assertThat(contact.note).isEqualTo("测试签名")
        assertThat(info.name).isEqualTo("张三")
        assertThat(info.platforms).containsEntry("qq", "123456")
    }

    @Test
    fun toContactAndInfo_blankNickname_usesDefaultName() {
        val result = NetworkResolveResult(
            nickname = "",
            description = "",
            avatarUrl = "",
            contactMap = mutableMapOf(),
            type = ContactType.QQ
        )
        val (contact, _) = ContactNetworkResolver.toContactAndInfo(result, "raw")
        assertThat(contact.name).isEqualTo("未知联系人")
    }

    @Test
    fun toContactAndInfo_includesGroupPlatforms() {
        val result = NetworkResolveResult(
            nickname = "测试群",
            description = "",
            avatarUrl = "",
            contactMap = mutableMapOf("qqGroup" to "987654321", "telegramGroup" to "testgroup"),
            type = ContactType.QQGroup
        )
        val (_, info) = ContactNetworkResolver.toContactAndInfo(result, "raw")
        assertThat(info.platforms).containsEntry("qqGroup", "987654321")
        assertThat(info.platforms).containsEntry("telegramGroup", "testgroup")
    }

    @Test
    fun toContactAndInfo_blankValuesExcludedFromPlatforms() {
        val result = NetworkResolveResult(
            nickname = "张三",
            description = "",
            avatarUrl = "",
            contactMap = mutableMapOf("qq" to "", "bilibili" to "   "),
            type = ContactType.QQ
        )
        val (_, info) = ContactNetworkResolver.toContactAndInfo(result, "raw")
        assertThat(info.platforms).doesNotContainKey("qq")
    }
}