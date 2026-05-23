package top.mcxiafeng.badger.network.adapter

import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.network.ContactType

/**
 * X (Twitter) 适配器
 *
 * 通过 snaplytics.io CDN 代理接口获取用户公开资料（无需 Token）。
 * 接口可能不稳定，获取失败时 fallback 为简单模式。
 */
class XAdapter : PlatformAdapter {

    override val platformType = ContactType.X
    override val label = "X"
    override val tagColor = 0xFF000000L

    override suspend fun resolve(content: String): PlatformResolveResult {
        val username = ContactNetworkResolver.extractTwitterUsername(content)
        val data = username?.let { ContactNetworkResolver.getTwitterUserInfo(it) }

        return if (data != null && data["name"].isNullOrBlank().not()) {
            PlatformResolveResult(
                name = data["name"],
                avatarUrl = data["profile_image_url"]?.ifBlank { null },
                signature = data["description"]?.ifBlank { null },
                contactMap = mapOf("x" to (data["username"] ?: username))
            )
        } else {
            // fallback：接口失败时存入原始内容
            PlatformResolveResult(
                name = "X 用户",
                avatarUrl = null,
                signature = content,
                contactMap = mapOf("x" to content)
            )
        }
    }
}
