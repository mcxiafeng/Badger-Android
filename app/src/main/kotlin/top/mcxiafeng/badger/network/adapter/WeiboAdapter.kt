package top.mcxiafeng.badger.network.adapter

import top.mcxiafeng.badger.network.ContactType

/**
 * 微博适配器
 *
 * 微博无稳定的公开 API 获取用户信息。
 * 从链接中提取用户 ID/昵称，存入 contactMap["weibo"]。
 */
class WeiboAdapter : PlatformAdapter {

    override val platformType = ContactType.Weibo
    override val label = "微博"
    override val tagColor = 0xFFE6162DL // 微博红
    override val canSync = false

    override suspend fun resolve(content: String): PlatformResolveResult? {
        val weiboId = extractWeiboId(content) ?: return null

        return PlatformResolveResult(
            name = "微博用户",
            avatarUrl = null,
            signature = content,
            contactMap = mapOf("weibo" to weiboId)
        )
    }

    /** 从微博 URL 中提取用户 ID 或昵称 */
    private fun extractWeiboId(content: String): String? {
        // weibo.com/u/数字ID
        val uidMatch = Regex("""weibo\.com/u/(\d+)""").find(content)
        if (uidMatch != null) return uidMatch.groupValues[1]
        // weibo.com/昵称
        val nameMatch = Regex("""weibo\.com/([a-zA-Z0-9_]+)(?:\?|$|/)""").find(content)
        if (nameMatch != null) {
            val name = nameMatch.groupValues[1]
            if (name !in listOf("u", "p", "n", "share", "signup", "login", "pub")) {
                return name
            }
        }
        return null
    }
}