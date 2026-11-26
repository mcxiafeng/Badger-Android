package top.mcxiafeng.badger.pages.social

import android.util.Log
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

/** QR captions contain a person's name; platform labels and account values have their own row. */
internal fun SocialUiState.qrUserNameFor(platformKey: String?): String? {
    val entry = platforms.firstOrNull { it.first == platformKey }?.second ?: return null
    val saved = entry.displayName?.trim()?.takeIf { it.isNotEmpty() }
    val fetched = platformProfiles[platformKey]?.name?.trim()?.takeIf { it.isNotEmpty() }
        ?.takeUnless { platformKey == "qq" && it == "QQ用户${entry.value}" }
    val global = profile?.name?.trim()?.takeIf { it.isNotEmpty() }
    Log.d("SocialCardContentTester", "解析二维码昵称；saved=${saved != null}；fetched=${fetched != null}；global=${global != null}")
    return saved ?: fetched ?: global
}
