package top.mcxiafeng.badger.network.adapter

import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.network.ContactType

/**
 * Telegram 个人号适配器
 *
 * 仅处理个人用户（t.me/username，action_btn 为 "Send Message"）。
 * 通过 t.me 预览页 HTML 提取用户公开资料（无需 Token，需网络可访问 t.me）。
 */
class TelegramAdapter : PlatformAdapter {

    override val platformType = ContactType.Telegram
    override val label = "Telegram"
    override val tagColor = 0xFF26A5E4L

    override suspend fun resolve(content: String): PlatformResolveResult? {
        val username = extractUsername(content) ?: return null
        val data = ContactNetworkResolver.getTelegramUserInfo(username)

        return if (data != null && data["name"].isNullOrBlank().not()) {
            PlatformResolveResult(
                name = data["name"],
                avatarUrl = data["profile_image_url"]?.ifBlank { null },
                signature = data["description"]?.ifBlank { null },
                contactMap = mapOf("telegram" to (data["username"] ?: username))
            )
        } else {
            PlatformResolveResult(
                name = "Telegram 用户",
                avatarUrl = null,
                signature = content,
                contactMap = mapOf("telegram" to username)
            )
        }
    }

    /** 从 t.me URL 中提取用户名 */
    private fun extractUsername(content: String): String? {
        val match = Regex("""t\.me/([a-zA-Z0-9_]{5,32})""", RegexOption.IGNORE_CASE).find(content)
        return match?.groupValues?.get(1)
    }
}

/**
 * Telegram 群/频道适配器
 *
 * 处理三种格式：
 * - t.me/+invitecode（私密群邀请）
 * - t.me/joinchat/invitecode（旧版邀请）
 * - t.me/groupname（公开群/频道，需访问 HTML 判断是否为群）
 *
 * 通过 t.me 预览页 HTML 提取群名/头像/简介，区分依据：
 * - action_btn = "Join Group" → 私密群
 * - extra 含 "members" → 公开群
 * - 邀请链接格式 → 群
 * 获取失败时 fallback 为简单模式。
 */
class TelegramGroupAdapter : PlatformAdapter {

    override val platformType = ContactType.TelegramGroup
    override val label = "Telegram群"
    override val tagColor = 0xFF26A5E4L

    override suspend fun resolve(content: String): PlatformResolveResult? {
        // 私密邀请链接：直接访问获取信息
        val inviteCode = extractInviteCode(content)
        if (inviteCode != null) {
            val data = ContactNetworkResolver.getTelegramGroupInfo(content)
            return if (data != null && data["name"].isNullOrBlank().not()) {
                PlatformResolveResult(
                    name = data["name"],
                    avatarUrl = data["profile_image_url"]?.ifBlank { null },
                    signature = data["description"]?.ifBlank { null },
                    contactMap = mapOf("telegramGroup" to inviteCode)
                )
            } else {
                PlatformResolveResult(
                    name = "Telegram 群",
                    avatarUrl = null,
                    signature = null,
                    contactMap = mapOf("telegramGroup" to inviteCode)
                )
            }
        }

        // 公开群/频道：t.me/groupname
        val username = extractUsername(content) ?: return null
        val data = ContactNetworkResolver.getTelegramGroupInfo(username)
        return if (data != null && data["name"].isNullOrBlank().not()) {
            PlatformResolveResult(
                name = data["name"],
                avatarUrl = data["profile_image_url"]?.ifBlank { null },
                signature = data["description"]?.ifBlank { null },
                contactMap = mapOf("telegramGroup" to username)
            )
        } else {
            PlatformResolveResult(
                name = "Telegram 群",
                avatarUrl = null,
                signature = null,
                contactMap = mapOf("telegramGroup" to username)
            )
        }
    }

    /** 提取私密邀请码（t.me/+xxx 或 t.me/joinchat/xxx） */
    private fun extractInviteCode(content: String): String? {
        val joinMatch = Regex("""t\.me/\+([a-zA-Z0-9_-]+)""").find(content)
        if (joinMatch != null) return "+${joinMatch.groupValues[1]}"
        val joinchatMatch = Regex("""t\.me/joinchat/([a-zA-Z0-9_-]+)""").find(content)
        if (joinchatMatch != null) return joinchatMatch.groupValues[1]
        return null
    }

    /** 提取公开群用户名 */
    private fun extractUsername(content: String): String? {
        val match = Regex("""t\.me/([a-zA-Z0-9_]{5,32})""", RegexOption.IGNORE_CASE).find(content)
        return match?.groupValues?.get(1)
    }
}
