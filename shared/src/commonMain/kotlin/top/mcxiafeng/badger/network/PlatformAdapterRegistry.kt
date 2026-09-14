package top.mcxiafeng.badger.network

enum class ContactType {
    QQ, QQGroup, Bilibili, WeChat, Douyin, Weibo, GitHub,
    Telegram, TelegramGroup, Xiaohongshu, Facebook, X, Website, None,
}

val SYNCABLE_KINDS: Set<String> = setOf(
    "github",
    "bilibili",
    "qq",
    "x",
    "telegram",
)

val String.kindCanSync: Boolean
    get() = this in SYNCABLE_KINDS

fun kindToContactType(kind: String): ContactType? = when (kind) {
    "qq" -> ContactType.QQ
    
    "qqNapcat", "qqnapcat" -> ContactType.QQ
    "qqGroup" -> ContactType.QQGroup
    "bilibili" -> ContactType.Bilibili
    "wechat" -> ContactType.WeChat
    "douyin" -> ContactType.Douyin
    "weibo" -> ContactType.Weibo
    "github" -> ContactType.GitHub
    "telegram" -> ContactType.Telegram
    "telegramGroup" -> ContactType.TelegramGroup
    "xiaohongshu" -> ContactType.Xiaohongshu
    "facebook" -> ContactType.Facebook
    "x" -> ContactType.X
    "website" -> ContactType.Website
    "unknown", "" -> null
    else -> null
}

object PlatformAdapterRegistry {

    
    data class TagInfo(val type: ContactType, val label: String, val color: Long)

    private val TAG_COLORS = mapOf(
        ContactType.QQ to TagInfo(ContactType.QQ, "QQ", 0xFF1296DBL),
        ContactType.QQGroup to TagInfo(ContactType.QQGroup, "QQ群", 0xFF12B7F5L),
        ContactType.Bilibili to TagInfo(ContactType.Bilibili, "B站", 0xFFFB7299L),
        ContactType.WeChat to TagInfo(ContactType.WeChat, "微信", 0xFF07C160L),
        ContactType.Douyin to TagInfo(ContactType.Douyin, "抖音", 0xFFFE2C55L),
        ContactType.Weibo to TagInfo(ContactType.Weibo, "微博", 0xFFE6162DL),
        ContactType.GitHub to TagInfo(ContactType.GitHub, "GitHub", 0xFF24292EL),
        ContactType.Telegram to TagInfo(ContactType.Telegram, "Telegram", 0xFF0088CCL),
        ContactType.TelegramGroup to TagInfo(ContactType.TelegramGroup, "Telegram群", 0xFF0088CCL),
        ContactType.Xiaohongshu to TagInfo(ContactType.Xiaohongshu, "小红书", 0xFFFF2442L),
        ContactType.Facebook to TagInfo(ContactType.Facebook, "Facebook", 0xFF1877F2L),
        ContactType.X to TagInfo(ContactType.X, "X", 0xFF000000L),
        ContactType.Website to TagInfo(ContactType.Website, "网站", 0xFF607D8BL),
    )

    fun getTagInfo(type: ContactType): TagInfo? = TAG_COLORS[type]
}
