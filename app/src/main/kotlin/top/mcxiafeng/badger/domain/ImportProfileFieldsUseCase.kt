package top.mcxiafeng.badger.domain

import android.util.Log
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.ocr.ExtractedContactInfo
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ocr.buildPlatformLink

/**
 * Imports platform fields discovered by scanner/OCR into the local user profile.
 *
 * Compose only supplies extracted data and observes the result; network resolution and
 * repository writes stay outside the UI layer.
 */
class ImportProfileFieldsUseCase(
    private val userProfileRepository: UserProfileRepository,
    private val contactNetworkResolver: ContactNetworkResolver,
) {
    suspend operator fun invoke(items: List<ExtractedContactInfo>): Int {
        var importedCount = 0

        for (info in items) {
            for ((key, value) in info.toFieldValues()) {
                if (value.isBlank() || key == "phone" || key == "email") continue

                val displayName = FIELD_DEF_MAP[key]?.displayName ?: key
                val jumpLink = buildPlatformLink(key, value)
                val resolved = runCatching {
                    contactNetworkResolver.identify(jumpLink)
                }.onFailure { error ->
                    Log.w(TAG, "导入时平台信息解析失败", error)
                }.getOrNull()

                userProfileRepository.updatePlatformField(
                    fieldKey = displayName,
                    jumpLink = jumpLink,
                    value = value,
                    displayName = resolved?.name?.takeIf { it.isNotBlank() && it != "未知" },
                    avatarUrl = resolved?.avatarUrl?.takeIf { it.isNotBlank() },
                )
                importedCount++
            }
        }

        return importedCount
    }

    private companion object {
        const val TAG = "ImportProfileFields"
    }
}
