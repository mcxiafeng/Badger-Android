package top.mcxiafeng.badger.ocr

import top.mcxiafeng.badger.network.ContactType

enum class LinkSource {
    
    AUTO,
    
    LINK_ONLY,
    
    NO_LINK,
}

data class PlatformFieldDef(
    val fieldKey: String,
    val displayName: String,
    val contactType: ContactType,
    val iconName: String,
    val linkTemplate: String? = null,
    val deepLinkTemplate: String? = null,
    val packageName: String? = null,
    val qrcodeToScan: Boolean = false,
    val aliases: List<String> = emptyList(),
    val inputHint: String = "",
    val linkSource: LinkSource = LinkSource.AUTO,
)

val SYSTEM_FIELDS = listOf(
    PlatformFieldDef("phone", "电话", ContactType.None, "ic_phone"),
    PlatformFieldDef("email", "邮箱", ContactType.None, "ic_email"),
    
    PlatformFieldDef("gender", "性别", ContactType.None, "ic_phone"),
    PlatformFieldDef("birthday", "生日", ContactType.None, "ic_phone"),
    PlatformFieldDef("country", "国家", ContactType.None, "ic_phone"),
    PlatformFieldDef("region", "地区", ContactType.None, "ic_phone"),
)

val PLATFORM_FIELDS = listOf(
    PlatformFieldDef(
        "wechat", "微信", ContactType.WeChat, "ic_wechat",
        qrcodeToScan = true,
        aliases = listOf("微信", "wechat"),
        inputHint = "微信号",
        linkSource = LinkSource.NO_LINK,
    ),
    PlatformFieldDef(
        "qq", "QQ", ContactType.QQ, "ic_qq",
        linkTemplate = "https://tool.gljlw.com/qq/?qq=%s",
        deepLinkTemplate = "mqq://card/show_pslcard?src_type=internal&version=1&uin=%s&card_type=person&source=sharecard",
        packageName = "com.tencent.mobileqq",
        aliases = listOf("qq"),
        inputHint = "QQ号或加好友链接",
        linkSource = LinkSource.AUTO,
    ),
    PlatformFieldDef(
        "bilibili", "B站", ContactType.Bilibili, "ic_bilibili",
        linkTemplate = "https://space.bilibili.com/%s",
        deepLinkTemplate = "bilibili://space/%s",
        packageName = "tv.danmaku.bili",
        aliases = listOf("bilibili", "b站"),
        inputHint = "B站UID或主页链接",
        linkSource = LinkSource.AUTO,
    ),
    PlatformFieldDef(
        "xiaohongshu", "小红书", ContactType.Xiaohongshu, "ic_xiaohongshu",
        linkTemplate = "https://www.xiaohongshu.com/user/profile/%s",
        packageName = "com.xingin.xhs",
        aliases = listOf("小红书", "xiaohongshu"),
        inputHint = "小红书主页链接",
        linkSource = LinkSource.LINK_ONLY,
    ),
    PlatformFieldDef(
        "douyin", "抖音", ContactType.Douyin, "ic_douyin",
        linkTemplate = "https://www.douyin.com/user/%s",
        deepLinkTemplate = "snssdk1128://user/profile/%s",
        packageName = "com.ss.android.ugc.aweme",
        aliases = listOf("抖音", "douyin"),
        inputHint = "抖音主页链接",
        linkSource = LinkSource.LINK_ONLY,
    ),
    PlatformFieldDef(
        "weibo", "微博", ContactType.Weibo, "ic_weibo",
        linkTemplate = "https://weibo.com/u/%s",
        deepLinkTemplate = "sinaweibo://userinfo?uid=%s",
        packageName = "com.sina.weibo",
        aliases = listOf("微博", "weibo"),
        inputHint = "微博UID或主页链接",
        linkSource = LinkSource.AUTO,
    ),
    PlatformFieldDef(
        "github", "GitHub", ContactType.GitHub, "ic_github",
        linkTemplate = "https://github.com/%s",
        packageName = "com.github.android",
        aliases = listOf("github"),
        inputHint = "GitHub用户名或主页链接",
        linkSource = LinkSource.AUTO,
    ),
    PlatformFieldDef(
        "telegram", "Telegram", ContactType.Telegram, "ic_telegram",
        linkTemplate = "https://t.me/%s",
        deepLinkTemplate = "tg://resolve?domain=%s",
        packageName = "org.telegram.messenger",
        aliases = listOf("telegram", "tg"),
        inputHint = "Telegram用户名或主页链接",
        linkSource = LinkSource.AUTO,
    ),
    PlatformFieldDef(
        "facebook", "Facebook", ContactType.Facebook, "ic_facebook",
        linkTemplate = "https://www.facebook.com/%s",
        deepLinkTemplate = "fb://profile/%s",
        packageName = "com.facebook.katana",
        aliases = listOf("facebook", "fb"),
        inputHint = "Facebook用户名或主页链接",
        linkSource = LinkSource.AUTO,
    ),
    PlatformFieldDef(
        "x", "X", ContactType.X, "ic_x",
        linkTemplate = "https://x.com/%s",
        deepLinkTemplate = "twitter://user?screen_name=%s",
        packageName = "com.twitter.android",
        aliases = listOf("x", "twitter"),
        inputHint = "X用户名或主页链接",
        linkSource = LinkSource.AUTO,
    ),
    PlatformFieldDef(
        "website", "网站", ContactType.Website, "ic_website",
        aliases = listOf("网站", "website"),
        inputHint = "网站链接",
        linkSource = LinkSource.LINK_ONLY,
    ),
    PlatformFieldDef(
        "telegramGroup", "Telegram群", ContactType.TelegramGroup, "ic_telegram",
        packageName = "org.telegram.messenger",
        aliases = listOf("telegram群", "tg群"),
        inputHint = "",
    ),
    PlatformFieldDef(
        "qqGroup", "QQ群", ContactType.QQGroup, "ic_qq",
        packageName = "com.tencent.mobileqq",
        aliases = listOf("qq群"),
        inputHint = "",
    ),
)

val ALL_FIELDS = SYSTEM_FIELDS + PLATFORM_FIELDS
val FIELD_DEF_MAP = ALL_FIELDS.associateBy { it.fieldKey }

val SYSTEM_FIELD_KEYS: Set<String> = SYSTEM_FIELDS.map { it.fieldKey }.toSet()

val PLATFORM_FIELD_KEYS: Set<String> = PLATFORM_FIELDS.map { it.fieldKey }.toSet()

val ALIAS_TO_KEY_MAP: Map<String, String> = buildMap {
    for (def in ALL_FIELDS) {
        for (alias in def.aliases) {
            put(alias.lowercase(), def.fieldKey)
        }
    }
}

val SHORT_LINK_DOMAINS: Map<String, String> = mapOf(
    "v.douyin.com" to "douyin",
    "www.iesdouyin.com" to "douyin",
    "xhslink.com" to "xiaohongshu",
    "b23.tv" to "bilibili",
    "t.cn" to "weibo",
    "qm.qq.com" to "qq",
    "tool.gljlw.com" to "qq",
    "u.wechat.com" to "wechat",
)

fun isUrlInput(input: String): Boolean =
    input.startsWith("http://", ignoreCase = true) || input.startsWith("https://", ignoreCase = true)

fun buildPlatformLink(fieldKey: String, value: String): String {
    val def = FIELD_DEF_MAP[fieldKey] ?: return value
    val trimmed = value.trim()
    if (isUrlInput(trimmed)) return trimmed

    if (def.linkSource == LinkSource.LINK_ONLY) return trimmed
    if (def.linkSource == LinkSource.NO_LINK) return trimmed

    val clean = trimmed.removePrefix("@")
    return def.linkTemplate?.replace("%s", clean) ?: trimmed
}
