package top.mcxiafeng.badger.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlatformIdExtractorTest {

    // ========== normalizeToKey ==========

    @Test
    fun normalizeToKey_exactPresetMatch_returnsKey() {
        assertThat(PlatformIdExtractor.normalizeToKey("QQ")).isEqualTo("qq")
        assertThat(PlatformIdExtractor.normalizeToKey("微信")).isEqualTo("wechat")
        assertThat(PlatformIdExtractor.normalizeToKey("哔哩哔哩")).isEqualTo("bilibili")
        assertThat(PlatformIdExtractor.normalizeToKey("微博")).isEqualTo("weibo")
        assertThat(PlatformIdExtractor.normalizeToKey("抖音")).isEqualTo("douyin")
        assertThat(PlatformIdExtractor.normalizeToKey("小红书")).isEqualTo("xiaohongshu")
        assertThat(PlatformIdExtractor.normalizeToKey("GitHub")).isEqualTo("github")
        assertThat(PlatformIdExtractor.normalizeToKey("Telegram")).isEqualTo("telegram")
    }

    @Test
    fun normalizeToKey_qqAliases_returnsQq() {
        assertThat(PlatformIdExtractor.normalizeToKey("qq")).isEqualTo("qq")
        assertThat(PlatformIdExtractor.normalizeToKey("QQ号")).isEqualTo("qq")
    }

    @Test
    fun normalizeToKey_bilibiliAliases_returnsBilibili() {
        assertThat(PlatformIdExtractor.normalizeToKey("bilibili")).isEqualTo("bilibili")
        assertThat(PlatformIdExtractor.normalizeToKey("b站")).isEqualTo("bilibili")
        assertThat(PlatformIdExtractor.normalizeToKey("B站")).isEqualTo("bilibili")
    }

    @Test
    fun normalizeToKey_wechatAliases_returnsWechat() {
        assertThat(PlatformIdExtractor.normalizeToKey("wechat")).isEqualTo("wechat")
    }

    @Test
    fun normalizeToKey_telegramAliases_returnsTelegram() {
        assertThat(PlatformIdExtractor.normalizeToKey("tg")).isEqualTo("telegram")
        assertThat(PlatformIdExtractor.normalizeToKey("TG")).isEqualTo("telegram")
        assertThat(PlatformIdExtractor.normalizeToKey("telegram")).isEqualTo("telegram")
    }

    @Test
    fun normalizeToKey_unknown_returnsLowercase() {
        assertThat(PlatformIdExtractor.normalizeToKey("MyPlatform")).isEqualTo("myplatform")
    }

    @Test
    fun normalizeToKey_caseInsensitive() {
        assertThat(PlatformIdExtractor.normalizeToKey("GITHUB")).isEqualTo("github")
        assertThat(PlatformIdExtractor.normalizeToKey("TELEGRAM")).isEqualTo("telegram")
    }

    // ========== extractByKey validation ==========

    @Test
    fun extractByKey_blankLink_returnsError() = runTest {
        val result = PlatformIdExtractor.extractByKey("qq", "")
        assertThat(result.errorMessage).isNotNull()
        assertThat(result.value).isNull()
    }

    @Test
    fun extractByKey_nonHttpLink_returnsError() = runTest {
        val result = PlatformIdExtractor.extractByKey("qq", "mqq://card/show_pslcard?uin=123")
        assertThat(result.errorMessage).isNotNull()
    }

    @Test
    fun extractByKey_wrongDomain_returnsFormatError() = runTest {
        val result = PlatformIdExtractor.extractByKey("bilibili", "https://example.com/something")
        assertThat(result.errorMessage).contains("B站")
    }

    @Test
    fun extractByKey_wechat_returnsUnsupportedMessage() = runTest {
        val result = PlatformIdExtractor.extractByKey("wechat", "https://weixin.qq.com/xxx")
        assertThat(result.errorMessage).contains("微信暂不支持")
    }

    @Test
    fun extractByKey_github_wrongDomain_returnsFormatError() = runTest {
        val result = PlatformIdExtractor.extractByKey("github", "https://example.com/test")
        assertThat(result.errorMessage).contains("GitHub")
    }

    // ========== extractWeibo (indirect via extractByKey) ==========

    @Test
    fun extractWeibo_uidFormat_returnsUid() = runTest {
        // weibo.com/u/12345 格式
        val result = PlatformIdExtractor.extractByKey("weibo", "https://weibo.com/u/12345")
        assertThat(result.value).isEqualTo("12345")
    }

    @Test
    fun extractWeibo_nameFormat_returnsName() = runTest {
        val result = PlatformIdExtractor.extractByKey("weibo", "https://weibo.com/testuser?")
        assertThat(result.value).isEqualTo("testuser")
    }

    // ========== extractGithub (indirect via extractByKey) ==========

    @Test
    fun extractGithub_validPath_returnsUsername() = runTest {
        val result = PlatformIdExtractor.extractByKey("github", "https://github.com/testuser")
        assertThat(result.value).isEqualTo("testuser")
    }

    // ========== extractTelegram (indirect via extractByKey) ==========

    @Test
    fun extractTelegram_tmeUrl_returnsAtUsername() = runTest {
        val result = PlatformIdExtractor.extractByKey("telegram", "https://t.me/testuser")
        assertThat(result.value).isNotNull()
    }

    // ========== presets ==========

    @Test
    fun presets_containsExpectedPlatforms() {
        assertThat(PlatformIdExtractor.normalizeToKey("QQ")).isEqualTo("qq")
        assertThat(PlatformIdExtractor.normalizeToKey("微信")).isEqualTo("wechat")
        assertThat(PlatformIdExtractor.normalizeToKey("哔哩哔哩")).isEqualTo("bilibili")
        assertThat(PlatformIdExtractor.normalizeToKey("微博")).isEqualTo("weibo")
        assertThat(PlatformIdExtractor.normalizeToKey("抖音")).isEqualTo("douyin")
        assertThat(PlatformIdExtractor.normalizeToKey("小红书")).isEqualTo("xiaohongshu")
        assertThat(PlatformIdExtractor.normalizeToKey("GitHub")).isEqualTo("github")
        assertThat(PlatformIdExtractor.normalizeToKey("Telegram")).isEqualTo("telegram")
    }
}
