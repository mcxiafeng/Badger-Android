package top.mcxiafeng.badger.network.adapter

import top.mcxiafeng.badger.network.ContactType

/**
 * 抖音适配器
 *
 * 抖音无法通过公开 API 获取用户信息。
 * 统一存入 contactMap["douyin"]，后端自动处理是抖音号还是抖音链接。
 */
class TikTokAdapter : PlatformAdapter {

    override val platformType = ContactType.TikTok
    override val label = "抖音"
    override val tagColor = 0xFF111111L
    override val canSync = false

    override suspend fun resolve(content: String): PlatformResolveResult {
        return PlatformResolveResult(
            name = "抖音用户",
            avatarUrl = null,
            signature = content,
            contactMap = mapOf("douyin" to content)
        )
    }
}
