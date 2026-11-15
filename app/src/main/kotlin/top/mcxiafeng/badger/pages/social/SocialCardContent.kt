package top.mcxiafeng.badger.pages.social

import androidx.compose.runtime.Immutable
import top.mcxiafeng.badger.network.PlatformIdExtractor
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP

@Immutable
data class SocialCardContent(val name: String, val signature: String? = null)

/** 只取当前平台的资料；“添加”页不继承上一平台或全局个人介绍。 */
internal fun SocialUiState.cardContentFor(platformKey: String?): SocialCardContent {
    val entry = platforms.firstOrNull { it.first == platformKey }?.second
        ?: return SocialCardContent(name = "添加")
    val profile = platformProfiles[platformKey]
    val normalizedKey = PlatformIdExtractor.normalizeToKey(platformKey.orEmpty())
    val name = entry.displayName?.takeIf { it.isNotBlank() }
        ?: profile?.name?.takeIf { it.isNotBlank() }
        ?: entry.value?.takeIf { it.isNotBlank() }
        ?: FIELD_DEF_MAP[normalizedKey]?.displayName
        ?: platformKey.orEmpty()
    return SocialCardContent(name = name, signature = profile?.signature?.takeIf { it.isNotBlank() })
}
