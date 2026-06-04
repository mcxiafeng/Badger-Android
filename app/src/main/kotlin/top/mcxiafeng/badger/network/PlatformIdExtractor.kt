package top.mcxiafeng.badger.network

import android.util.Log
import top.mcxiafeng.badger.ocr.ALIAS_TO_KEY_MAP
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ocr.SHORT_LINK_DOMAINS

/**
 * 从平台链接中自动提取用户 ID、昵称和头像
 *
 * @property value 提取到的用户 ID/账号，提取失败为 null
 * @property displayName 提取到的平台昵称，提取失败为 null
 * @property avatarUrl 提取到的平台头像 URL，提取失败为 null
 * @property errorMessage 解析过程中的错误信息
 */
data class ExtractResult(
    val value: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val errorMessage: String? = null
)

/**
 * 从平台链接中自动提取用户 ID、昵称和头像
 *
 * 通过 fieldKey 提取：`extractByKey(key, link)`
 */
object PlatformIdExtractor {

    private const val TAG = "PlatformIdExtractor"

    /**
     * 通过标准化的平台 key 提取用户信息
     *
     * @param key 平台标准标识符（如 "qq"、"bilibili"）
     * @param link 平台链接 URL
     * @return 提取结果（ID、昵称、头像），失败时 errorMessage 说明原因
     */
    suspend fun extractByKey(key: String, link: String): ExtractResult {
        val url = link.trim()

        // 基础格式验证
        if (url.isBlank()) return ExtractResult(errorMessage = "链接不能为空")
        if (!url.startsWith("http://") && !url.startsWith("https://"))
            return ExtractResult(errorMessage = "链接必须以 http:// 或 https:// 开头")

        // 平台特定链接验证
        val pattern = PLATFORM_PATTERNS[key]
        if (pattern != null && !pattern.matches(url)) {
            return ExtractResult(errorMessage = PLATFORM_HINTS[key] ?: "链接格式不正确")
        }

        // 提取用户信息
        return when (key) {
            "qq" -> extractQQ(url)
            "bilibili" -> extractBilibili(url)
            "wechat" -> ExtractResult(value = null, errorMessage = "微信暂不支持自动提取信息")
            "weibo" -> {
                val id = extractWeibo(url)
                if (id.isNullOrBlank()) ExtractResult(errorMessage = "无法从链接中提取微博账号")
                else ExtractResult(value = id)
            }
            "douyin" -> {
                val id = extractDouyin(url)
                if (id.isNullOrBlank()) ExtractResult(errorMessage = "无法从链接中提取抖音账号")
                else ExtractResult(value = id)
            }
            "xiaohongshu" -> {
                val id = extractXiaohongshu(url)
                if (id.isNullOrBlank()) ExtractResult(value = url)
                else ExtractResult(value = id)
            }
            "github" -> {
                val id = extractGithub(url)
                if (id.isNullOrBlank()) ExtractResult(errorMessage = "无法从链接中提取 GitHub 用户名")
                else ExtractResult(value = id)
            }
            "telegram" -> {
                val id = extractTelegram(url)
                if (id.isNullOrBlank()) ExtractResult(errorMessage = "无法从链接中提取 Telegram 账号")
                else ExtractResult(value = id)
            }
            "facebook" -> {
                val id = extractFacebook(url)
                if (id.isNullOrBlank()) ExtractResult(errorMessage = "无法从链接中提取 Facebook 用户名")
                else ExtractResult(value = id)
            }
            "x" -> {
                val id = extractX(url)
                if (id.isNullOrBlank()) ExtractResult(errorMessage = "无法从链接中提取 X 用户名")
                else ExtractResult(value = id)
            }
            else -> {
                val id = extractGeneric(url)
                ExtractResult(value = id?.takeIf { it.isNotBlank() })
            }
        }
    }

    /**
     * 将用户输入的平台名称标准化为 fieldKey
     *
     * 优先从 ALIAS_TO_KEY_MAP 查找，找不到则用 FIELD_DEF_MAP 的 displayName 匹配，
     * 最后 fallback 到小写名称。
     */
    fun normalizeToKey(name: String): String {
        // 1. 精确匹配 fieldKey
        if (FIELD_DEF_MAP.containsKey(name)) return name

        // 2. 从别名表查找
        val lower = name.lowercase()
        ALIAS_TO_KEY_MAP[lower]?.let { return it }

        // 3. 从 FIELD_DEF_MAP 的 displayName 查找
        val match = FIELD_DEF_MAP.entries.find { it.value.displayName.equals(name, ignoreCase = true) }
        if (match != null) return match.key

        // 4. 模糊匹配（兼容老数据的各种写法）
        return when {
            lower.contains("qq群") -> "qqGroup"
            lower.contains("qq") -> "qq"
            lower.contains("哔哩") || lower.contains("bilibili") || lower.contains("b站") || lower == "b" -> "bilibili"
            lower.contains("微信") || lower.contains("wechat") -> "wechat"
            lower.contains("微博") || lower.contains("weibo") -> "weibo"
            lower.contains("抖音") || lower.contains("douyin") || lower.contains("tiktok") -> "douyin"
            lower.contains("小红书") || lower.contains("xiaohongshu") -> "xiaohongshu"
            lower.contains("github") -> "github"
            lower.contains("telegram群") || lower.contains("tg群") -> "telegramGroup"
            lower.contains("telegram") || lower.contains("tg") -> "telegram"
            lower.contains("facebook") || lower.contains("fb") -> "facebook"
            lower.contains("twitter") || lower == "x" -> "x"
            lower.contains("网站") || lower.contains("website") -> "website"
            else -> lower
        }
    }

    /**
     * 从 URL 中检测平台 fieldKey
     *
     * 支持短链域名和长链接域名。
     */
    fun detectFieldKeyFromUrl(url: String): String? {
        val host = try { java.net.URI(url).host?.lowercase() ?: return null } catch (_: Exception) { return null }

        // 先查短链域名
        SHORT_LINK_DOMAINS[host]?.let { return it }

        // 再查长链接域名
        return when {
            host.contains("bilibili.com") || host == "b23.tv" -> "bilibili"
            host.contains("qq.com") || host.contains("gljlw.com") -> if (url.contains("qun.qq.com")) "qqGroup" else "qq"
            host.contains("wechat.com") -> "wechat"
            host.contains("douyin.com") || host.contains("iesdouyin.com") -> "douyin"
            host.contains("weibo.com") || host == "t.cn" -> "weibo"
            host.contains("github.com") -> "github"
            host.contains("t.me") -> if (url.contains("/+") || url.contains("/joinchat/")) "telegramGroup" else "telegram"
            host.contains("xiaohongshu.com") || host == "xhslink.com" -> "xiaohongshu"
            host.contains("facebook.com") || host.contains("fb.com") -> "facebook"
            host.contains("x.com") || host.contains("twitter.com") || host == "t.co" -> "x"
            else -> null
        }
    }

    /** 平台链接正则匹配规则 */
    private val PLATFORM_PATTERNS: Map<String, Regex> = mapOf(
        "qq" to Regex("""https?://.*\.qq\.com.*|https?://tool\.gljlw\.com/qq/.*"""),
        "bilibili" to Regex("""https?://(.*\.)?(bilibili\.com|b23\.tv)/.*"""),
        "wechat" to Regex("""https?://.*"""),
        "weibo" to Regex("""https?://(.*\.)?weibo\.com/.*|https?://t\.cn/.*"""),
        "douyin" to Regex("""https?://(.*\.)?(douyin|iesdouyin|tiktok)\.com/.*"""),
        "xiaohongshu" to Regex("""https?://(.*\.)?xiaohongshu\.com/.*|https?://xhslink\.com/.*"""),
        "github" to Regex("""https?://(.*\.)?github\.com/.*"""),
        "telegram" to Regex("""https?://t\.me/.*"""),
        "facebook" to Regex("""https?://(.*\.)?(facebook|fb)\.com/.*"""),
        "x" to Regex("""https?://(.*\.)?(x\.com|twitter\.com)/.*|https?://t\.co/.*"""),
    )

    /** 链接格式错误时的提示文案 */
    private val PLATFORM_HINTS: Map<String, String> = mapOf(
        "qq" to "QQ 链接应为 qq.com 或 tool.gljlw.com 域名下的链接",
        "bilibili" to "B站链接应为 bilibili.com 或 b23.tv 域名下的链接",
        "weibo" to "微博链接应为 weibo.com 或 t.cn 域名下的链接",
        "douyin" to "抖音链接应为 douyin.com 域名下的链接",
        "xiaohongshu" to "小红书链接应为 xiaohongshu.com 或 xhslink.com 域名下的链接",
        "github" to "GitHub 链接应为 github.com 域名下的链接",
        "telegram" to "Telegram 链接应为 t.me 域名下的链接",
        "facebook" to "Facebook 链接应为 facebook.com 域名下的链接",
        "x" to "X 链接应为 x.com 或 twitter.com 域名下的链接",
    )

    // ========== QQ 提取（正则 + 网络 + 昵称 + 头像） ==========

    private suspend fun extractQQ(url: String): ExtractResult {
        val qqCode = PlatformNetworkMethods.getQQCode(url)?.takeIf { it.isNotBlank() }
        if (qqCode == null) return ExtractResult()

        val qqUser = PlatformNetworkMethods.getQQNick(qqCode)
        val displayName = qqUser?.nickname
        val avatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=$qqCode&s=100"

        return ExtractResult(
            value = qqCode,
            displayName = displayName,
            avatarUrl = avatarUrl
        )
    }

    // ========== B站 提取（正则 + 网络 + 昵称 + 头像） ==========

    private suspend fun extractBilibili(url: String): ExtractResult {
        val uid = PlatformNetworkMethods.extractBiliUid(url)
        if (uid.isNullOrBlank()) return ExtractResult()

        val biliInfo = PlatformNetworkMethods.getBiliBiliInfo(url)
        val displayName = biliInfo?.get("name")
        val avatarUrl = biliInfo?.get("face")

        return ExtractResult(
            value = uid,
            displayName = displayName,
            avatarUrl = avatarUrl
        )
    }

    // ========== 各平台本地正则提取 ==========

    private fun extractWeibo(url: String): String? {
        val match = Regex("""weibo\.com/u/(\d+)""").find(url)
        if (match != null) return match.groupValues[1]
        val nameMatch = Regex("""weibo\.com/([a-zA-Z0-9_]+)(?:\?|$)""").find(url)
        if (nameMatch != null && nameMatch.groupValues[1] !in listOf("u", "p", "n", "share")) {
            return nameMatch.groupValues[1]
        }
        return null
    }

    private fun extractDouyin(url: String): String? {
        val secMatch = Regex("""sec_uid=([A-Za-z0-9_-]+)""").find(url)
        if (secMatch != null) return secMatch.groupValues[1]
        val userMatch = Regex("""/user/([A-Za-z0-9_-]+)""").find(url)
        if (userMatch != null) return userMatch.groupValues[1]
        return null
    }

    private fun extractXiaohongshu(url: String): String? {
        val uidMatch = Regex("""xiaohongshu\.com/user/profile/([a-f0-9]+)""").find(url)
        if (uidMatch != null) return uidMatch.groupValues[1]
        return null
    }

    private fun extractGithub(url: String): String? {
        val match = Regex("""github\.com/([a-zA-Z0-9_-]+)(?:/|$)""").find(url)
        if (match != null) {
            val username = match.groupValues[1]
            if (username !in listOf("orgs", "repos", "settings", "notifications", "explore", "trending", "search")) {
                return username
            }
        }
        return null
    }

    private fun extractTelegram(url: String): String? {
        val match = Regex("""t\.me/([a-zA-Z0-9_]{5,32})""").find(url)
        if (match != null) return "@${match.groupValues[1]}"
        val joinMatch = Regex("""t\.me/joinchat/([a-zA-Z0-9_-]+)""").find(url)
        if (joinMatch != null) return joinMatch.groupValues[1]
        return null
    }

    private fun extractFacebook(url: String): String? {
        val match = Regex("""facebook\.com/([a-zA-Z0-9.]+)(?:[/?]|$)""").find(url)
        if (match != null) {
            val username = match.groupValues[1]
            if (username !in listOf("profile.php", "watch", "groups", "events", "marketplace")) {
                return username
            }
        }
        return null
    }

    private fun extractX(url: String): String? {
        val match = Regex("""(?:x\.com|twitter\.com)/([a-zA-Z0-9_]+)(?:[/?]|$)""").find(url)
        if (match != null) {
            val username = match.groupValues[1]
            if (username !in listOf("home", "explore", "search", "i", "status")) {
                return username
            }
        }
        return null
    }

    private fun extractGeneric(url: String): String? {
        if (!url.startsWith("http")) return null
        val match = Regex("""https?://[^/]+/([a-zA-Z0-9_-]+)(?:\?|/|$)""").find(url)
        return match?.groupValues?.get(1)
    }
}
