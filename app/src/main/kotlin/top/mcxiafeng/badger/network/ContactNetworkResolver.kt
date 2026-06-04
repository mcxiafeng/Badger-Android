package top.mcxiafeng.badger.network

import android.util.Log
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.ocr.ExtractedContactInfo
import top.mcxiafeng.badger.ocr.buildPlatformLink
import top.mcxiafeng.badger.network.adapter.PlatformAdapterRegistry

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
 * 底层网络方法已移至 [PlatformNetworkMethods]，
 * 本类仅保留门面方法 [getResultInfo] 和 [toContactAndInfo]。
 */
object ContactNetworkResolver {

    private const val TAG = "ContactNetworkResolver"

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
            Log.e(TAG, "getResultInfo failed", e)
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
