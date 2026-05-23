package top.mcxiafeng.badger.network.adapter

import top.mcxiafeng.badger.network.ContactType
import top.mcxiafeng.badger.utils.HttpUtil

/**
 * 平台适配器注册中心
 *
 * 管理所有 [PlatformAdapter] 实例，提供按 [ContactType] 查找适配器。
 * 同时负责从原始内容判断平台类型（类型路由）。
 */
object PlatformAdapterRegistry {

    /** 已注册的适配器，按 ContactType 索引 */
    private val adapters: Map<ContactType, PlatformAdapter> = mapOf(
        ContactType.QQ to QqAdapter(),
        ContactType.QQGroup to QqGroupAdapter(),
        ContactType.Bilibili to BilibiliAdapter(),
        ContactType.WeChat to WeChatAdapter(),
        ContactType.TikTok to TikTokAdapter(),
        ContactType.Weibo to WeiboAdapter(),
        ContactType.GitHub to GithubAdapter(),
        ContactType.Telegram to TelegramAdapter(),
        ContactType.TelegramGroup to TelegramGroupAdapter(),
        ContactType.Xiaohongshu to XiaohongshuAdapter(),
        ContactType.Facebook to FacebookAdapter(),
        ContactType.X to XAdapter(),
        ContactType.Website to WebsiteAdapter()
    )

    /**
     * 根据 ContactType 获取对应的适配器
     */
    fun getAdapter(type: ContactType): PlatformAdapter? = adapters[type]

    /**
     * 从原始内容判断平台类型
     *
     * 解析逻辑：
     * 1. 先用原始 URL 做粗判断（避免 HEAD 请求被拦截）
     * 2. 非已知平台，尝试重定向跟踪后再判断
     *
     * @return 平台类型 + 解析后的实际 URL
     */
    suspend fun resolveContentType(originStr: String): Pair<ContactType, String> {
        // 先用原始 URL 做粗判断
        val roughType = detectTypeFromContent(originStr)
        if (roughType != null) return roughType to originStr

        // 非已知平台，尝试重定向跟踪
        val resolvedUrl = if (originStr.startsWith("http")) {
            HttpUtil.getFinalRedirectUrl(originStr) ?: originStr
        } else {
            originStr
        }

        val resolvedType = detectTypeFromContent(resolvedUrl)
        // 未知 HTTP/HTTPS 链接归为 Website 类型
        if (resolvedType == null && resolvedUrl.startsWith("http")) {
            return ContactType.Website to resolvedUrl
        }
        return (resolvedType ?: ContactType.None) to resolvedUrl
    }

    /**
     * 从内容/URL 中检测平台类型
     */
    private suspend fun detectTypeFromContent(content: String): ContactType? {
        return when {
            content.contains("bilibili.com") -> ContactType.Bilibili
            content.contains("qq.com") || content.contains("gljlw.com") -> {
                if (content.contains("qun.qq.com")) ContactType.QQGroup
                else if (content.contains("qm.qq.com/q/")) {
                    // qm.qq.com 短链接：通过 HTML 中 var sid 区分（sid=1 个人，sid=2 群）
                    val html = HttpUtil.get(content)
                    val sidMatch = html?.let { Regex("""var\s+sid\s*=\s*(\d)""").find(it)?.groupValues?.get(1) }
                    if (sidMatch == "2") ContactType.QQGroup else ContactType.QQ
                }
                else ContactType.QQ
            }
            content.startsWith("mqq://") -> {
                if (content.contains("card_type=group")) ContactType.QQGroup
                else ContactType.QQ
            }
            content.contains("wechat.com") -> ContactType.WeChat
            content.contains("douyin.com") -> ContactType.TikTok
            content.contains("weibo.com") -> ContactType.Weibo
            content.contains("github.com") -> ContactType.GitHub
            content.contains("t.me") -> detectTelegramType(content)
            content.contains("xiaohongshu.com") || content.contains("xhslink.com") -> ContactType.Xiaohongshu
            content.contains("facebook.com") || content.contains("fb.com") -> ContactType.Facebook
            content.contains("x.com") || content.contains("twitter.com") || content.contains("t.co") -> ContactType.X
            else -> null
        }
    }

    /**
     * 检测 Telegram 链接是个人号还是群/频道
     *
     * 判断规则：
     * - t.me/+xxx / t.me/joinchat/xxx → 私密群邀请 → TelegramGroup
     * - t.me/username → 需访问 HTML 判断：
     *   - "Join Group" / "members" → 群
     *   - "Preview channel" / "subscribers" → 频道
     *   - "Send Message" → 个人号
     * - 判断失败默认 Telegram（个人）
     */
    private suspend fun detectTelegramType(content: String): ContactType {
        // 私密邀请链接直接判定为群
        if (content.contains("t.me/+") || content.contains("t.me/joinchat/")) {
            return ContactType.TelegramGroup
        }
        // 公开链接：访问 HTML 判断
        val html = HttpUtil.get(content, headers = mapOf("Accept" to "text/html"))
        if (html != null) {
            val lower = html.lowercase()
            if (lower.contains("join group") || lower.contains("members,") || lower.contains("preview channel") || lower.contains("subscribers")) {
                return ContactType.TelegramGroup
            }
            if (lower.contains("send message")) {
                return ContactType.Telegram
            }
        }
        // 默认当个人号
        return ContactType.Telegram
    }

    /**
     * 获取平台标签信息
     *
     * @return 标签文字 + 颜色（ARGB Long），未知平台返回 null
     */
    fun getTagInfo(type: ContactType): Pair<String, Long>? {
        val adapter = adapters[type] ?: return null
        return adapter.label to adapter.tagColor
    }
}