package top.mcxiafeng.badger.network.adapter

import top.mcxiafeng.badger.network.PlatformNetworkMethods
import top.mcxiafeng.badger.network.ContactType

/**
 * Bilibili 用户适配器
 */
class BilibiliAdapter : PlatformAdapter {

    override val platformType = ContactType.Bilibili
    override val label = "B站"
    override val tagColor = 0xFF00A1D6L

    override suspend fun resolve(content: String): PlatformResolveResult? {
        val data = PlatformNetworkMethods.getBiliBiliInfo(content) ?: return null
        val name = data["name"] ?: return null
        val mid = data["mid"] ?: return null

        return PlatformResolveResult(
            name = name,
            avatarUrl = data["face"]?.ifBlank { null },
            signature = data["sign"]?.ifBlank { null },
            contactMap = mapOf("bilibili" to mid)
        )
    }
}