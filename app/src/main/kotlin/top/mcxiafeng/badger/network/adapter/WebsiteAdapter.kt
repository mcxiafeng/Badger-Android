package top.mcxiafeng.badger.network.adapter

import top.mcxiafeng.badger.network.ContactType
import java.net.URL

/**
 * 未知网站/博客适配器
 *
 * 对于无法识别为特定平台的 HTTP/HTTPS 链接：
 * - 名字使用域名
 * - 头像使用网站 favicon
 * - contactMap 存储 website 链接
 */
class WebsiteAdapter : PlatformAdapter {

    override val platformType = ContactType.Website
    override val label = "网站"
    override val tagColor = 0xFF607D8BL // 蓝灰色

    override suspend fun resolve(content: String): PlatformResolveResult? {
        if (!content.startsWith("http://") && !content.startsWith("https://")) return null

        val host = try {
            URL(content).host
        } catch (_: Exception) {
            return null
        }

        val faviconUrl = "https://www.google.com/s2/favicons?domain=$host&sz=64"

        return PlatformResolveResult(
            name = host,
            avatarUrl = faviconUrl,
            signature = content,
            contactMap = mapOf("website" to content)
        )
    }
}