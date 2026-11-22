package top.mcxiafeng.badger.network

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import top.mcxiafeng.badger.utils.BILIBILI_HEADERS
import top.mcxiafeng.badger.utils.HttpUtil
import java.net.URLEncoder

/**
 * 各平台网络请求方法集合
 *
 * 从 ContactNetworkResolver 中提取的底层网络方法，
 * 供各 PlatformAdapter 和 PlatformIdExtractor 调用复用。
 */
object PlatformNetworkMethods {

    private const val TAG = "PlatformNetworkMethods"

    // ========== Bilibili ==========

    /**
     * 从 Bilibili 空间 URL 中提取 UID
     *
     * 支持格式：
     * - `space.bilibili.com/{UID}`
     * - `m.bilibili.com/space/{UID}`
     */
    fun extractBiliUid(urlStr: String): String? {
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
    suspend fun getBiliBiliInfo(urlStr: String): Map<String, String>? {
        val uid = extractBiliUid(urlStr)?.trim() ?: return null
        Log.i("Tester", "getBiliBiliInfo: uid=$uid")
        val json = HttpUtil.get(
            "https://api.bilibili.com/x/web-interface/card?mid=$uid",
            headers = BILIBILI_HEADERS + mapOf("Accept" to "application/json")
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
            Log.e(TAG, "getBiliBiliInfo failed", e)
            mapOf(
                "name" to ("未知"),
                "mid" to ("未知"),
                "face" to ("未知"),
                "sign" to ("未知")
            )
        }
    }

    // ========== QQ ==========

    private const val QQ_TAG = "PlatformNetworkMethodsTester"
    private const val QQ_PROFILE_URL = "https://xiaoapi.cn/v1/qzone_info.php"
    private const val QQ_PROFILE_SUCCESS = 200
    private val qqNumberPattern = Regex("[0-9]{5,11}")

    /**
     * 从扫描结果中提取 QQ 号
     *
     * 支持多种格式：
     * - QQ URL（qq.com）：先正则提取，失败则爬网页提取
     * - mqq:// 协议：从参数中提取 uid
     */
    suspend fun getQQCode(result: String): String? {
        // 纯 QQ 号
        if (qqNumberPattern.matches(result)) return result
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
    fun extractQQCodeFromUrl(urlStr: String): String? {
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

    /** 通过慕名 API 获取 QQ 昵称、空间简介和头像。 */
    suspend fun getQQProfile(qqCode: String): QQUser? {
        if (!qqNumberPattern.matches(qqCode)) {
            Log.d(QQ_TAG, "Skipping QQ profile lookup for invalid number")
            return null
        }

        val json = HttpUtil.get("$QQ_PROFILE_URL?qq=$qqCode")
        if (json == null) {
            Log.d(QQ_TAG, "QQ profile lookup returned no response body")
            return null
        }

        val root = try {
            JsonParser.parseString(json) as? JsonObject
        } catch (e: JsonParseException) {
            Log.d(QQ_TAG, "QQ profile response has invalid JSON (${e.javaClass.simpleName})")
            return null
        }

        val code = (root?.get("code") as? JsonPrimitive)?.asString?.toIntOrNull()
        val data = root?.get("data") as? JsonObject
        val user = data?.get("user_info") as? JsonObject
        if (code != QQ_PROFILE_SUCCESS || user == null) {
            Log.d(QQ_TAG, "QQ profile response unavailable: code=$code")
            return null
        }

        return QQUser(
            nickname = user.nonBlankString("name"),
            description = user.nonBlankString("qzone_desc"),
            avatarUrl = qqProfileAvatarUrl(user.nonBlankString("head_url"))
        ).also { Log.d(QQ_TAG, "QQ profile parsed: hasAvatar=${it.avatarUrl != null}") }
    }

    internal fun qqFallbackAvatarUrl(qqCode: String): String =
        "https://q1.qlogo.cn/g?b=qq&nk=$qqCode&s=100"

    private fun JsonObject.nonBlankString(key: String): String? =
        (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.asString?.trim()?.takeIf { it.isNotEmpty() }

    private fun qqProfileAvatarUrl(value: String?): String? {
        val url = value?.toHttpUrlOrNull() ?: return null
        if (!url.host.endsWith(".qlogo.cn") || url.username.isNotEmpty() || url.password.isNotEmpty()) {
            return null
        }
        // 慕名 API 返回 HTTP 头像地址，QQ 头像服务也支持 HTTPS。
        return url.newBuilder().scheme("https").build().toString()
    }

    // ========== X (Twitter) ==========

    /**
     * 从 URL 或用户名中提取 X (Twitter) 用户名
     *
     * 支持格式：x.com/username、twitter.com/username、纯用户名
     */
    fun extractTwitterUsername(content: String): String? {
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
    suspend fun getTwitterUserInfo(username: String): Map<String, String>? {
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
            Log.e(TAG, "getTwitterUserInfo failed: username=$username", e)
            null
        }
    }

    // ========== Telegram ==========

    /**
     * 获取 Telegram 用户信息
     *
     * 通过 t.me 预览页 HTML 提取用户公开资料（无需 Token，需网络可访问 t.me）。
     * 从 og:title/og:image/og:description meta 标签中提取信息。
     *
     * @return 包含 name/username/profile_image_url/description 的 Map，失败时返回 null
     */
    suspend fun getTelegramUserInfo(username: String): Map<String, String>? {
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
    suspend fun getTelegramGroupInfo(content: String): Map<String, String>? {
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
            Log.e(TAG, "parseTelegramHtml failed: username=$username", e)
            null
        }
    }
}
