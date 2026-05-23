package top.mcxiafeng.badger.network.adapter

import top.mcxiafeng.badger.network.ContactType

/**
 * 小红书适配器
 *
 * 小红书需要登录 cookie 才能获取用户昵称/头像，无法通过公开 API 获取。
 * 统一存入 contactMap["xiaohongshu"]，后端自动处理是小红书 ID 还是链接。
 */
class XiaohongshuAdapter : PlatformAdapter {

    override val platformType = ContactType.Xiaohongshu
    override val label = "小红书"
    override val tagColor = 0xFFFF2442L
    override val canSync = false

    override suspend fun resolve(content: String): PlatformResolveResult {
        return PlatformResolveResult(
            name = null,
            avatarUrl = null,
            signature = content,
            contactMap = mapOf("xiaohongshu" to content)
        )
    }
}
