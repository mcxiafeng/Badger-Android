package top.mcxiafeng.badger.network.adapter

import android.util.Log
import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.network.ContactType
import top.mcxiafeng.badger.utils.HttpUtil

/**
 * QQ 个人号适配器
 */
class QqAdapter : PlatformAdapter {

    override val platformType = ContactType.QQ
    override val label = "QQ"
    override val tagColor = 0xFF12B7F5L

    override suspend fun resolve(content: String): PlatformResolveResult? {
        val qqCode = ContactNetworkResolver.getQQCode(content) ?: return null
        val qqUser = ContactNetworkResolver.getQQNick(qqCode)

        val name = qqUser?.nickname ?: "QQ用户$qqCode"
        val avatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=$qqCode&s=100"
        val signature = qqUser?.longNick

        return PlatformResolveResult(
            name = name,
            avatarUrl = avatarUrl,
            signature = signature,
            contactMap = mapOf("qq" to qqCode)
        )
    }
}

/**
 * QQ 群适配器
 *
 * 支持两种链接格式：
 * - qm.qq.com/q/xxx 短链接：从 JS 变量 rawuin 提取群号
 * - qun.qq.com 等直接链接：从 HTML 提取群号/群名
 *
 * 群名获取：优先从 HTML 提取，失败则通过 API 查询（uapis.cn 优先，lanren-tools 备用）
 */
class QqGroupAdapter : PlatformAdapter {

    override val platformType = ContactType.QQGroup
    override val label = "QQ群"
    override val tagColor = 0xFF12B7F5L

    override suspend fun resolve(content: String): PlatformResolveResult? {
        val html = HttpUtil.get(content) ?: return null

        // 优先从 JS 变量 rawuin 提取群号（qm.qq.com 短链接格式）
        val groupNum = Regex("""var\s+rawuin\s*=\s*(\d{6,10})""").find(html)?.groupValues?.get(1)
            ?: Regex("""群号:\s*(\d{6,10})""").find(html)?.groupValues?.get(1)
            ?: return null

        // 优先从 HTML 提取群名/群简介，失败则调用 API
        val htmlGroupName = extractGroupName(html)
        val htmlGroupDesc = extractGroupDesc(html)
        val apiResult = if (htmlGroupName == null) fetchGroupInfoFromApi(groupNum) else null

        val groupName = htmlGroupName ?: apiResult?.first ?: "QQ群$groupNum"
        val groupDesc = htmlGroupDesc ?: apiResult?.second

        return PlatformResolveResult(
            name = groupName,
            avatarUrl = "https://p.qlogo.cn/gh/$groupNum/$groupNum/",
            signature = groupDesc,
            contactMap = mapOf("qqGroup" to groupNum)
        )
    }

    /** 通过 API 获取群信息，返回 (群名, 群简介)。优先 uapis.cn，失败用 lanren-tools 备用 */
    private suspend fun fetchGroupInfoFromApi(groupNum: String): Pair<String, String?>? {
        return fetchGroupInfoFromUapis(groupNum) ?: fetchGroupInfoFromLanren(groupNum)
    }

    /** uapis.cn API（GET，直接返回 data） */
    private suspend fun fetchGroupInfoFromUapis(groupNum: String): Pair<String, String?>? {
        return try {
            val json = HttpUtil.get("https://uapis.cn/api/v1/social/qq/groupinfo?group_id=$groupNum") ?: return null
            parseGroupInfoJson(json)
        } catch (e: Exception) {
            Log.d("QqGroupAdapter", "uapis.cn failed: ${e.message}")
            null
        }
    }

    /** lanren-tools API（POST，返回 {code, msg, data}，备用） */
    private suspend fun fetchGroupInfoFromLanren(groupNum: String): Pair<String, String?>? {
        return try {
            val body = """{"group_id":"$groupNum"}"""
            val json = HttpUtil.post(
                "https://www.lanren-tools.com/qq_group/queryInfoByQq",
                body
            ) ?: return null
            parseGroupInfoJson(json)
        } catch (e: Exception) {
            Log.d("QqGroupAdapter", "lanren-tools failed: ${e.message}")
            null
        }
    }

    /** 从 JSON 中提取 group_name 和 description */
    private fun parseGroupInfoJson(json: String): Pair<String, String?>? {
        val nameRegex = Regex(""""group_name"\s*:\s*"([^"]+)"""")
        val name = nameRegex.find(json)?.groupValues?.get(1)
        if (name.isNullOrBlank()) return null
        val descRegex = Regex(""""description"\s*:\s*"([^"]+)"""")
        val desc = descRegex.find(json)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        return Pair(name, desc)
    }

    private fun extractGroupName(html: String): String? {
        // 从 <title> 提取（qun.qq.com 格式）
        val titleMatch = Regex("""<title>([^<]+)</title>""").find(html)
        if (titleMatch != null) {
            val title = titleMatch.groupValues[1].trim()
            if (title != "正在跳转") {
                return title.removeSuffix(" - QQ群")
            }
        }
        // 从 JSON 提取
        val jsonMatch = Regex(""""groupName"\s*:\s*"([^"]+)"""").find(html)
        return jsonMatch?.groupValues?.get(1)
    }

    private fun extractGroupDesc(html: String): String? {
        val descMatch = Regex(""""groupDescription"\s*:\s*"([^"]+)"""").find(html)
        return descMatch?.groupValues?.get(1)
    }
}
