package top.mcxiafeng.badger.ocr

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlatformFieldsTest {

    @Before
    fun setUp() {
        // [§14.2] 即使本测试不直接调 Koin,但 HttpUtil/AppDatabase 静态层会
        // 通过 KoinComponentBy 拿依赖。统一在 setup 阶段 stop+start Koin,
        // 避免依赖其它测试 setUp 顺序。
        runCatching { GlobalContext.stopKoin() }
        GlobalContext.startKoin {
            modules(
                module {
                    single { org.robolectric.RuntimeEnvironment.getApplication() }
                },
            )
        }
    }

    @Test
    fun buildPlatformLink_bilibili_withTemplate_returnsLink() {
        val result = buildPlatformLink("bilibili", "12345")
        assertThat(result).isEqualTo("https://space.bilibili.com/12345")
    }

    @Test
    fun buildPlatformLink_qq_withTemplate_returnsLink() {
        val result = buildPlatformLink("qq", "123456")
        assertThat(result).isEqualTo("https://tool.gljlw.com/qq/?qq=123456")
    }

    @Test
    fun buildPlatformLink_weibo_withTemplate_returnsLink() {
        val result = buildPlatformLink("weibo", "12345")
        assertThat(result).isEqualTo("https://weibo.com/u/12345")
    }

    @Test
    fun buildPlatformLink_telegram_withTemplate_returnsLink() {
        val result = buildPlatformLink("telegram", "testuser")
        assertThat(result).isEqualTo("https://t.me/testuser")
    }

    @Test
    fun buildPlatformLink_removesAtPrefix() {
        val result = buildPlatformLink("telegram", "@testuser")
        assertThat(result).isEqualTo("https://t.me/testuser")
    }

    @Test
    fun buildPlatformLink_unknownKey_returnsValue() {
        val result = buildPlatformLink("custom_platform", "some_value")
        assertThat(result).isEqualTo("some_value")
    }

    @Test
    fun buildPlatformLink_wechat_noTemplate_returnsValue() {
        val result = buildPlatformLink("wechat", "wxid_abc")
        assertThat(result).isEqualTo("wxid_abc")
    }

    @Test
    fun buildLaunchAction_qq_returnsIntents() {
        val action = buildLaunchAction("qq", "123456")
        assertThat(action).isInstanceOf(LaunchAction.Intents::class.java)
        val intents = action as LaunchAction.Intents
        assertThat(intents.intents).isNotEmpty()
    }

    @Test
    fun buildLaunchAction_email_returnsCopyAndOpen() {
        val action = buildLaunchAction("email", "test@example.com")
        assertThat(action).isInstanceOf(LaunchAction.CopyAndOpen::class.java)
        val copyAndOpen = action as LaunchAction.CopyAndOpen
        assertThat(copyAndOpen.copyText).isEqualTo("test@example.com")
    }

    @Test
    fun buildLaunchAction_wechat_httpLink_returnsWechatQrScan() {
        val action = buildLaunchAction("wechat", "wxid_abc", "https://weixin.qq.com/xxx")
        assertThat(action).isInstanceOf(LaunchAction.WechatQrScan::class.java)
        val qrScan = action as LaunchAction.WechatQrScan
        assertThat(qrScan.qrContent).isEqualTo("https://weixin.qq.com/xxx")
    }

    @Test
    fun buildLaunchAction_wechat_plainId_returnsCopyAndOpen() {
        val action = buildLaunchAction("wechat", "wxid_abc")
        assertThat(action).isInstanceOf(LaunchAction.CopyAndOpen::class.java)
    }

    @Test
    fun buildLaunchAction_bilibili_returnsIntents() {
        val action = buildLaunchAction("bilibili", "12345")
        assertThat(action).isInstanceOf(LaunchAction.Intents::class.java)
        val intents = action as LaunchAction.Intents
        assertThat(intents.intents).isNotEmpty()
    }

    @Test
    fun buildLaunchAction_unknownKey_returnsNone() {
        val action = buildLaunchAction("nonexistent", "value")
        assertThat(action).isEqualTo(LaunchAction.None)
    }

    @Test
    fun aliasToKeyMap_allAliasesResolve() {
        assertThat(ALIAS_TO_KEY_MAP["微信"]).isEqualTo("wechat")
        assertThat(ALIAS_TO_KEY_MAP["wechat"]).isEqualTo("wechat")
        assertThat(ALIAS_TO_KEY_MAP["qq"]).isEqualTo("qq")
        assertThat(ALIAS_TO_KEY_MAP["bilibili"]).isEqualTo("bilibili")
        assertThat(ALIAS_TO_KEY_MAP["b站"]).isEqualTo("bilibili")
        assertThat(ALIAS_TO_KEY_MAP["微博"]).isEqualTo("weibo")
        assertThat(ALIAS_TO_KEY_MAP["weibo"]).isEqualTo("weibo")
        assertThat(ALIAS_TO_KEY_MAP["抖音"]).isEqualTo("douyin")
        assertThat(ALIAS_TO_KEY_MAP["douyin"]).isEqualTo("douyin")
        assertThat(ALIAS_TO_KEY_MAP["github"]).isEqualTo("github")
        assertThat(ALIAS_TO_KEY_MAP["telegram"]).isEqualTo("telegram")
        assertThat(ALIAS_TO_KEY_MAP["tg"]).isEqualTo("telegram")
        assertThat(ALIAS_TO_KEY_MAP["小红书"]).isEqualTo("xiaohongshu")
        assertThat(ALIAS_TO_KEY_MAP["xiaohongshu"]).isEqualTo("xiaohongshu")
    }

    @Test
    fun fieldDefMap_allKeysPresent() {
        val expectedKeys = setOf("phone", "email", "gender", "birthday", "country", "region",
            "wechat", "qq", "bilibili", "weibo",
            "douyin", "github", "telegram", "telegramGroup", "qqGroup",
            "xiaohongshu", "facebook", "x", "website")
        assertThat(FIELD_DEF_MAP.keys).containsExactlyElementsIn(expectedKeys)
    }

    @Test
    fun allFields_containsSystemAndPlatformFields() {
        assertThat(ALL_FIELDS).hasSize(SYSTEM_FIELDS.size + PLATFORM_FIELDS.size)
        assertThat(ALL_FIELDS.take(SYSTEM_FIELDS.size)).isEqualTo(SYSTEM_FIELDS)
        assertThat(ALL_FIELDS.drop(SYSTEM_FIELDS.size)).isEqualTo(PLATFORM_FIELDS)
    }
}