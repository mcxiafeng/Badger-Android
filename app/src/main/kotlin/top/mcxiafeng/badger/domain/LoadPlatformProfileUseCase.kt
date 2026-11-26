package top.mcxiafeng.badger.domain

import android.util.Log
import kotlinx.coroutines.CancellationException
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.network.PlatformIdExtractor
import top.mcxiafeng.badger.network.adapter.PlatformAdapterRegistry
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import javax.inject.Inject

/** 平台自己的公开昵称和签名；与联系人的全局姓名、个人介绍分开。 */
data class PlatformProfile(val name: String?, val signature: String?)

class LoadPlatformProfileUseCase @Inject constructor() {
    suspend operator fun invoke(fieldKey: String, entry: PlatformEntry): PlatformProfile? {
        val normalizedKey = PlatformIdExtractor.normalizeToKey(fieldKey)
        val type = FIELD_DEF_MAP[normalizedKey]?.contactType ?: return null
        val adapter = PlatformAdapterRegistry.getAdapter(type)
        if (adapter?.canSync != true) {
            Log.d(TAG, "Platform profile unavailable: platform=$normalizedKey")
            return null
        }
        val content = entry.originalLink?.takeIf { it.isNotBlank() }
            ?: entry.jumpLink.ifBlank { entry.value.orEmpty() }
        if (content.isBlank()) return null
        return try {
            val result = adapter.resolve(content) ?: return null
            // 部分适配器失败时把输入内容原样返回，不能把账号/链接冒充签名。
            val signature = result.signature?.takeIf { it.isNotBlank() && it != content }
            val name = platformNicknameOrNull(normalizedKey, result.name, entry)
            if (name == null && signature == null) {
                Log.d(TAG, "Platform profile has no usable nickname or signature: platform=$normalizedKey")
                return null
            }
            Log.d(TAG, "Platform profile loaded: platform=$normalizedKey, hasNickname=${name != null}, hasSignature=${signature != null}")
            PlatformProfile(name = name, signature = signature)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Platform profile load failed: platform=$normalizedKey", e)
            null
        }
    }

    private companion object {
        const val TAG = "LoadPlatformProfileUseCaseTester"
    }
}
