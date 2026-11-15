package top.mcxiafeng.badger.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ParsePlatformInputUseCaseTest {
    private val parser = ParsePlatformInputUseCase()

    @Test
    fun multilineRecognizesLabelsLinksAndKeepsOriginalLineNumbers() {
        val results = parser("微信：wxid_test\n\nQQ: 123456\nhttps://space.bilibili.com/42\nGitHub: octocat")

        assertThat(results.map { it.fieldKey }).containsExactly("wechat", "qq", "bilibili", "github").inOrder()
        assertThat(results.map { it.lineNumber }).containsExactly(1, 3, 4, 5).inOrder()
        assertThat(results[0].entry?.value).isEqualTo("wxid_test")
        assertThat(results[0].entry?.jumpLink).isEmpty()
        assertThat(results[1].entry?.jumpLink).isEqualTo("https://tool.gljlw.com/qq/?qq=123456")
        assertThat(results[2].entry?.originalLink).isEqualTo("https://space.bilibili.com/42")
    }

    @Test
    fun shareTextAndShortLinksAreRecognizedWithoutResolvingThem() {
        val results = parser("这是我的主页 https://xhslink.com/abc 。\nhttps://b23.tv/abc\nhttps://v.douyin.com/abc/")

        assertThat(results.map { it.fieldKey }).containsExactly("xiaohongshu", "bilibili", "douyin").inOrder()
        assertThat(results[0].entry?.jumpLink).isEqualTo("https://xhslink.com/abc")
        assertThat(results[1].entry?.jumpLink).isEqualTo("https://b23.tv/abc")
    }

    @Test
    fun ambiguousAccountsAndMultipleLinksRequireCorrection() {
        val results = parser("123456\n@someone\nhttps://github.com/a https://x.com/b")

        assertThat(results.all { it.entry == null && it.error != null }).isTrue()
    }

    @Test
    fun hostMatchingUsesDomainBoundariesAndNeverCredentialsOrQueryText() {
        val results = parser("https://github.com.example.org/a\nhttps://example.org/?url=github.com\nhttps://github.com@example.org/a")

        assertThat(results[0].fieldKey).isEqualTo("website")
        assertThat(results[1].fieldKey).isEqualTo("website")
        assertThat(results[2].entry).isNull()
    }

    @Test
    fun moreSpecificWechatHostTakesPriorityOverTheQqParentDomain() {
        assertThat(parser("https://weixin.qq.com/r/example").single().fieldKey).isEqualTo("wechat")
        assertThat(parser("https://qm.qq.com/q/example").single().fieldKey).isEqualTo("qq")
    }

    @Test
    fun uppercaseSchemesAndHostsAreSupported() {
        assertThat(parser("HTTPS://GITHUB.COM/octocat").single().fieldKey).isEqualTo("github")
    }

    @Test
    fun manualLinkOnlyAccountDoesNotGenerateAnInventedUrl() {
        val result = parser.manual("douyin", "my_account", "我的昵称")

        assertThat(result.entry?.value).isEqualTo("my_account")
        assertThat(result.entry?.jumpLink).isEmpty()
        assertThat(result.entry?.displayName).isEqualTo("我的昵称")
    }

    @Test
    fun manualWebsiteAndUnsafeSchemesAreValidated() {
        assertThat(parser.manual("website", "example.org").error).isNotNull()
        assertThat(parser.manual("github", "javascript://alert(1)").error).isNotNull()
        assertThat(parser.manual("github", "https://").error).isNotNull()
        assertThat(parser.manual("website", "https://example.org").entry?.jumpLink).isEqualTo("https://example.org")
    }
}
