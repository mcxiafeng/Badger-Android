package top.mcxiafeng.badger.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.ocr.ExtractedContactInfo
import top.mcxiafeng.badger.ocr.buildPlatformLink
import top.mcxiafeng.badger.network.adapter.PlatformAdapterRegistry
import top.mcxiafeng.badger.utils.HttpUtil
import java.net.URLEncoder

/**
 * 联系人来源类型枚举
 *
 * 根据扫描到的二维码/链接内容自动识别的联系人类型。
 */
enum class ContactType {
    QQ,          // QQ 个人号
    QQGroup,     // QQ 群
    Bilibili,    // Bilibili 用户
    WeChat,      // 微信
    TikTok,      // 抖音
    Weibo,       // 微博
    GitHub,      // GitHub
    Telegram,    // Telegram
    TelegramGroup, // Telegram 群/频道
    Xiaohongshu, // 小红书
    Facebook,    // Facebook
    X,           // X (Twitter)
    Website,     // 未知网站/博客等
    None         // 未识别类型（普通文本、vCard 等）
}

/**
 * 网络解析结果
 *
 * 通过网络请求获取到的联系人详细信息。
 *
 * @property nickname 昵称/群名
 * @property description 签名/简介
 * @property avatarUrl 头像 URL
 * @property contactMap 从原始内容中提取的结构化字段（如 qq, qqGroup, bilibili 等）
 * @property type 联系人类型
 */
data class NetworkResolveResult(
    val nickname: String,
    val description: String,
    val avatarUrl: String,
    val contactMap: MutableMap<String, String>,
    val type: ContactType
)

/**
 * QQ 用户公开信息
 *
 * @property nickname QQ 昵称
 * @property longNick 个性签名
 */
data class QQUser(
    val nickname: String,
    val longNick: String
)

/**
 * 联系人网络信息解析器
 *
 * 根据二维码内容自动判断联系人类型（QQ/QQ群/B站/微信/抖音），
 * 通过 [PlatformAdapterRegistry] 路由到对应平台适配器获取信息。
 *
 * 底层网络方法（[getQQCode]、[getQQNick]、[getBiliBiliInfo] 等）
 * 供各 PlatformAdapter 调用复用。
 */
object ContactNetworkResolver {

    // ========== 底层网络方法（供 adapter 调用） ==========

    /**
     * 从 Bilibili 空间 URL 中提取 UID
     *
     * 支持格式：
     * - `space.bilibili.com/{UID}`
     * - `m.bilibili.com/space/{UID}`
     */
    internal fun extractBiliUid(urlStr: String): String? {
        val regex = Regex("""https?://(?:space\.bilibili\.com|m\.bilibili\.com/space)/(\d+)""", RegexOption.IGNORE_CASE)
        return regex.find(urlStr)?.groupValues?.get(1)
    }

    /**
     * 获取 Bilibili 用户信息
     *
     * 从 URL 中提取 UID，调用 card API 获取用户名片信息。
     *
     * @return 包含 name/mid/face/sign 的 Map，失败时返回 null
     */
    internal suspend fun getBiliBiliInfo(urlStr: String): Map<String, String>? {
        val uid = extractBiliUid(urlStr)?.trim() ?: return null
        Log.i("Tester", "getBiliBiliInfo: uid=$uid")
        val json = HttpUtil.get(
            "https://api.bilibili.com/x/web-interface/card?mid=$uid",
            headers = mapOf(
                "Referer" to "https://space.bilibili.com/",
                "Accept" to "application/json"
            )
        ) ?: return null
        Log.i("Tester", "getBiliBiliInfo HttpUtil: uid=${json}")
        return try {
            val gson = com.google.gson.Gson()
            val root = gson.fromJson(json, Map::class.java) as Map<*, *>
            if (root["code"]?.toString()?.toDoubleOrNull() != 0.0) return null
            val data = root["data"] as? Map<*, *> ?: return null
            val card = data["card"] as? Map<*, *> ?: return null

            mapOf(
                "name" to (card["name"]?.toString() ?: ""),
                "mid" to (card["mid"]?.toString() ?: uid),
                "face" to (card["face"]?.toString() ?: ""),
                "sign" to (card["sign"]?.toString() ?: "")
            )
        } catch (e: Exception) {
            e.printStackTrace()
            mapOf(
                "name" to ("未知"),
                "mid" to ("未知"),
                "face" to ("未知"),
                "sign" to ("未知")
            )
        }
    }

    /**
     * 从扫描结果中提取 QQ 号
     *
     * 支持多种格式：
     * - QQ URL（qq.com）：先正则提取，失败则爬网页提取
     * - mqq:// 协议：从参数中提取 uid
     */
    internal suspend fun getQQCode(result: String): String? {
        // 纯 QQ 号
        if (result.matches(Regex("\\d{5,11}"))) return result
        // tool.gljlw.com 链接模板：从 ?qq= 参数提取 QQ 号
        if (result.contains("gljlw.com")) {
            val paramMatch = Regex("""[?&]qq=(\d{5,11})""").find(result)
            if (paramMatch != null) return paramMatch.groupValues[1]
        }
        if (result.contains("qq.com")) {
            val code = extractQQCodeFromUrl(result)
            if (code != null) return code

            val extracted = extractQQCodeFromNetwork(result)
            return extracted?.toString()
        } else if (result.startsWith("mqq://")) {
            val uidMatch = Regex("""(?:uid|uin)=(\d+)""").find(result)
            return uidMatch?.groupValues?.get(1)
        }
        return null
    }

    /** 从 URL 中正则提取 QQ 号（不发起网络请求） */
    internal fun extractQQCodeFromUrl(urlStr: String): String? {
        val patterns = listOf(
            Regex("""qzone\.qq\.com/(\d+)"""),
            Regex("""uin=(\d{5,11})"""),
            Regex("""/(\d{5,11})(?:\?|/|$)""")
        )
        for (pattern in patterns) {
            val match = pattern.find(urlStr)
            if (match != null) return match.groupValues[1]
        }
        return null
    }

    /** 从 QQ 个人资料页 HTML 中提取 QQ 号（网络请求） */
    private suspend fun extractQQCodeFromNetwork(urlStr: String): Long? {
        val html = HttpUtil.get(urlStr) ?: return null
        val match = Regex("""uin\s*[:=]\s*["']?(\d{5,11})["']?""").find(html)
        return match?.groupValues?.get(1)?.toLongOrNull()
    }

    /**
     * 获取 QQ 用户的公开昵称信息
     *
     * 通过 uapis.cn 第三方 API 获取 QQ 用户资料。
     */
    internal suspend fun getQQNick(qqCode: String): QQUser? {
        val json = HttpUtil.get("https://uapis.cn/api/v1/social/qq/userinfo?qq=$qqCode")
            ?: return null

        return try {
            val gson = com.google.gson.Gson()
            val root = gson.fromJson(json, Map::class.java) as Map<*, *>

            val code = root["code"]?.toString()?.toIntOrNull()
            if (code != null && code != 200) return null

            val data = root["data"] as? Map<*, *> ?: root
            QQUser(
                nickname = data["nickname"]?.toString() ?: "QQ用户$qqCode",
                longNick = data["longnick"]?.toString()
                    ?: data["sign"]?.toString()
                    ?: data["longNick"]?.toString()
                    ?: ""
            )
        } catch (e: Exception) {
            e.printStackTrace()
            QQUser(nickname = "QQ用户$qqCode", longNick = "")
        }
    }

    /**
     * 从 URL 或用户名中提取 X (Twitter) 用户名
     *
     * 支持格式：x.com/username、twitter.com/username、纯用户名
     */
    internal fun extractTwitterUsername(content: String): String? {
        val regex = Regex("""https?://(?:x\.com|twitter\.com)/([a-zA-Z0-9_]+)""", RegexOption.IGNORE_CASE)
        return regex.find(content)?.groupValues?.get(1)
            ?: content.trim().takeIf { it.matches(Regex("[a-zA-Z0-9_]{1,15}")) }
    }

    /**
     * 获取 X (Twitter) 用户信息
     *
     * 通过 snaplytics.io CDN 代理接口获取用户公开资料，无需 Token。
     *
     * @return 包含 name/username/profile_image_url/description 的 Map，失败时返回 null
     */
    internal suspend fun getTwitterUserInfo(username: String): Map<String, String>? {
        val json = HttpUtil.get(
            "https://twittermedia.b-cdn.net/profile-pic/?username=${
                withContext(Dispatchers.IO) {
                    URLEncoder.encode(username, "UTF-8")
                }
            }",
            headers = mapOf(
                "Origin" to "https://snaplytics.io",
                "Referer" to "https://snaplytics.io/"
            )
        ) ?: return null
        Log.i("Tester", "getTwitterUserInfo: $json")
        return try {
            val gson = com.google.gson.Gson()
            val root = gson.fromJson(json, Map::class.java) as Map<*, *>
            mapOf(
                "name" to (root["name"]?.toString() ?: ""),
                "username" to (root["username"]?.toString() ?: username),
                "profile_image_url" to (root["profile_image_url"]?.toString() ?: ""),
                "description" to (root["description"]?.toString() ?: "")
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 获取 Telegram 用户信息
     *
     * 通过 t.me 预览页 HTML 提取用户公开资料（无需 Token，需网络可访问 t.me）。
     * 从 og:title/og:image/og:description meta 标签中提取信息。
     *
     * @return 包含 name/username/profile_image_url/description 的 Map，失败时返回 null
     */
    internal suspend fun getTelegramUserInfo(username: String): Map<String, String>? {
        val html = HttpUtil.get(
            "https://t.me/$username",
            headers = mapOf("Accept" to "text/html")
        ) ?: return null
        Log.i("Tester", "getTelegramUserInfo: html length=${html.length}")
        return parseTelegramHtml(html, username)
    }

    /**
     * 获取 Telegram 群/频道信息
     *
     * 通过 t.me 预览页 HTML 提取群/频道公开资料（无需 Token，需网络可访问 t.me）。
     * 支持三种入口：私密邀请链接（完整 URL）、公开群用户名、joinchat 邀请码。
     *
     * @param content 完整 t.me URL 或群用户名
     * @return 包含 name/username/profile_image_url/description 的 Map，失败时返回 null
     */
    internal suspend fun getTelegramGroupInfo(content: String): Map<String, String>? {
        val url = when {
            content.startsWith("http") -> content
            content.startsWith("t.me/") -> "https://$content"
            content.startsWith("+") -> "https://t.me/$content"
            content.startsWith("joinchat/") -> "https://t.me/$content"
            else -> "https://t.me/$content"  // 当作公开群用户名
        }
        val html = HttpUtil.get(url, headers = mapOf("Accept" to "text/html")) ?: return null
        Log.i("Tester", "getTelegramGroupInfo: url=$url, html length=${html.length}")
        // 提取用户名（如果有）
        val username = Regex("""t\.me/([a-zA-Z0-9_]{5,32})""").find(url)?.groupValues?.get(1) ?: ""
        return parseTelegramHtml(html, username)
    }

    /** 从 t.me HTML 提取 og:title/og:image/og:description */
    private fun parseTelegramHtml(html: String, username: String): Map<String, String>? {
        return try {
            val ogTitle = Regex("""property="og:title"\s+content="([^"]+)"""").find(html)?.groupValues?.get(1)
                ?: Regex("""content="([^"]+)"\s+property="og:title"""").find(html)?.groupValues?.get(1)
            val ogImage = Regex("""property="og:image"\s+content="([^"]+)"""").find(html)?.groupValues?.get(1)
                ?: Regex("""content="([^"]+)"\s+property="og:image"""").find(html)?.groupValues?.get(1)
            val ogDesc = Regex("""property="og:description"\s+content="([^"]+)"""").find(html)?.groupValues?.get(1)
                ?: Regex("""content="([^"]+)"\s+property="og:description"""").find(html)?.groupValues?.get(1)

            if (ogTitle.isNullOrBlank()) return null

            mapOf(
                "name" to ogTitle,
                "username" to username,
                "profile_image_url" to (ogImage ?: ""),
                "description" to (ogDesc ?: "")
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ========== 核心解析入口 ==========

    /**
     * 根据扫描内容从网络获取联系人详细信息
     *
     * 通过 [PlatformAdapterRegistry] 路由到对应平台适配器，
     * 统一获取名字、头像、签名。
     *
     * @param result 扫描到的原始内容
     * @param contactMap 可变的字段映射表（会被修改填充）
     * @param type 已知的联系人类型（如果为 null 则自动判断）
     * @return 网络解析结果，包含昵称、简介、头像和填充后的 contactMap
     */
    suspend fun getResultInfo(
        result: String,
        contactMap: MutableMap<String, String> = mutableMapOf(),
        type: ContactType? = null
    ): NetworkResolveResult? {
        // 解析类型和实际 URL
        val (contentType, resolvedUrl) = if (type != null) {
            type to result
        } else {
            PlatformAdapterRegistry.resolveContentType(result)
        }

        Log.i("Tester", "getResultInfo: type=$contentType, url=$resolvedUrl")

        if (contentType == ContactType.None) return null

        try {
            val adapter = PlatformAdapterRegistry.getAdapter(contentType)
            if (adapter != null) {
                val adapterResult = adapter.resolve(resolvedUrl) ?: return null
                contactMap.putAll(adapterResult.contactMap)

                val nickname = adapterResult.name ?: ""

                return NetworkResolveResult(
                    nickname = nickname,
                    description = adapterResult.signature ?: "",
                    avatarUrl = adapterResult.avatarUrl ?: "",
                    contactMap = contactMap,
                    type = contentType
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }

        return null
    }

    /**
     * 将网络解析结果转换为联系人实体和提取信息
     *
     * @param result 网络解析结果
     * @param rawContent 原始扫描内容
     * @return Pair（联系人实体，提取信息）
     */
    fun toContactAndInfo(result: NetworkResolveResult, rawContent: String): Pair<Contact, ExtractedContactInfo> {
        val knownKeys = setOf("qq", "qqGroup", "bilibili", "wechat", "douyin", "website", "weibo", "github", "telegram", "telegramGroup", "xiaohongshu", "facebook", "x")
        val platforms = mutableMapOf<String, String>()
        result.contactMap.forEach { (key, value) ->
            if (value.isNotBlank() && key in knownKeys && key != "qqGroup" && key != "telegramGroup") {
                platforms[key] = value
            }
        }
        result.contactMap["qqGroup"]?.let { if (it.isNotBlank()) platforms["qqGroup"] = it }
        result.contactMap["telegramGroup"]?.let { if (it.isNotBlank()) platforms["telegramGroup"] = it }

        val platformEntries = mutableMapOf<String, PlatformEntry>()
        for ((key, value) in platforms) {
            val jumpLink = buildPlatformLink(key, value)
            platformEntries[key] = PlatformEntry(jumpLink = jumpLink, value = value)
        }
        Log.d("Tester", "toContactAndInfo: platformEntries=$platformEntries")

        val contact = Contact(
            name = result.nickname.ifBlank { "未知联系人" },
            avatarUrl = result.avatarUrl.ifBlank { null },
            note = result.description.ifBlank { null },
            platforms = platformEntries.ifEmpty { null }
        )

        val info = ExtractedContactInfo(
            name = result.nickname,
            phone = null,
            email = null,
            platforms = platforms,
            rawText = rawContent,
            otherInfo = buildList {
                if (result.description.isNotBlank()) add(result.description)
                result.contactMap.forEach { (key, value) ->
                    if (key !in knownKeys && value.isNotBlank()) {
                        add("$key: $value")
                    }
                }
            }
        )

        return Pair(contact, info)
    }
}