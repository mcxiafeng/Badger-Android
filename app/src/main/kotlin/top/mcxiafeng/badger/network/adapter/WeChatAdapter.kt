package top.mcxiafeng.badger.network.adapter

import top.mcxiafeng.badger.network.ContactType

/**
 * 微信适配器
 *
 * 微信无法通过公开 API 获取用户信息。
 * 统一存入 contactMap["wechat"]，后端自动处理是微信号还是微信链接。
 */
class WeChatAdapter : PlatformAdapter {

    override val platformType = ContactType.WeChat
    override val label = "微信"
    override val tagColor = 0xFF07C160L
    override val canSync = false

    override suspend fun resolve(content: String): PlatformResolveResult {
        return PlatformResolveResult(
            name = null,
            avatarUrl = null,
            signature = content,
            contactMap = mapOf("wechat" to content)
        )
    }
}
