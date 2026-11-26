package top.mcxiafeng.badger.pages.social

import android.util.Log
import androidx.compose.runtime.Immutable
import top.mcxiafeng.badger.domain.platformNicknameOrNull
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
    val saved = platformNicknameOrNull(normalizedKey, entry.displayName, entry)
    val fetched = platformNicknameOrNull(normalizedKey, profile?.name, entry)
    val name = saved
        ?: fetched
        ?: entry.value?.takeIf { it.isNotBlank() }
        ?: FIELD_DEF_MAP[normalizedKey]?.displayName
        ?: platformKey.orEmpty()
    Log.d("SocialCardContentTester", "Card nickname selected; platform=$normalizedKey; saved=${saved != null}; fetched=${fetched != null}")
    return SocialCardContent(name = name, signature = profile?.signature?.takeIf { it.isNotBlank() })
}

/** QR captions contain a person's name; platform labels and account values have their own row. */
internal fun SocialUiState.qrUserNameFor(platformKey: String?): String? {
    val entry = platforms.firstOrNull { it.first == platformKey }?.second ?: return null
    val key = platformKey.orEmpty()
    val saved = platformNicknameOrNull(key, entry.displayName, entry)
    val fetched = platformNicknameOrNull(key, platformProfiles[platformKey]?.name, entry)
    val global = platformNicknameOrNull(key, profile?.name, entry)
    Log.d("SocialCardContentTester", "解析二维码昵称；saved=${saved != null}；fetched=${fetched != null}；global=${global != null}")
    return saved ?: fetched ?: global
}
