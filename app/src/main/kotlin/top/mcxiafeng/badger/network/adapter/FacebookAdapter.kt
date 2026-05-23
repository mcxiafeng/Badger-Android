package top.mcxiafeng.badger.network.adapter

import top.mcxiafeng.badger.network.ContactType

/**
 * Facebook 适配器
 *
 * Facebook 无法通过公开 API 获取用户信息。
 * 统一存入 contactMap["facebook"]，后端自动处理是用户名还是链接。
 */
class FacebookAdapter : PlatformAdapter {

    override val platformType = ContactType.Facebook
    override val label = "Facebook"
    override val tagColor = 0xFF1877F2L
    override val canSync = false

    override suspend fun resolve(content: String): PlatformResolveResult {
        return PlatformResolveResult(
            name = "Facebook 用户",
            avatarUrl = null,
            signature = content,
            contactMap = mapOf("facebook" to content)
        )
    }
}
