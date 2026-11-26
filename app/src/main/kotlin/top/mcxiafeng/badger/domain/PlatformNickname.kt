package top.mcxiafeng.badger.domain

import android.util.Log
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.network.PlatformIdExtractor

private val QqAccountInLink = Regex("""(?:[?&](?:qq|uin|uid)=|qzone\.qq\.com/)([0-9]{5,11})(?=[&#/?]|$)""", RegexOption.IGNORE_CASE)

/** Generated account labels are not platform nicknames and must not overwrite real names. */
internal fun platformNicknameOrNull(platformKey: String, nickname: String?, entry: PlatformEntry): String? {
    val name = nickname?.trim()?.takeIf { it.isNotEmpty() && it != "未知" } ?: return null
    if (PlatformIdExtractor.normalizeToKey(platformKey) != "qq") return name
    val account = entry.value?.trim()?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
        ?: sequenceOf(entry.originalLink, entry.jumpLink, entry.value)
            .filterNotNull().mapNotNull { QqAccountInLink.find(it)?.groupValues?.get(1) }.firstOrNull()
    if (account != null && name == "QQ用户$account") {
        Log.d("PlatformNicknameTester", "Rejected generated QQ account label; nicknameUnavailable=true")
        return null
    }
    return name
}
