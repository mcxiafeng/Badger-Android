package top.mcxiafeng.badger.domain

import android.content.Context
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.network.ShortLinkService

/** 平台切换后的默认平台持久化与短链接更新。 */
class SelectPlatformUseCase(
    private val userProfileRepository: UserProfileRepository,
    private val shortLinkService: ShortLinkService,
) {
    private companion object {
        const val TAG = "SelectPlatformUseCase"
        const val DEBOUNCE_MS = 2_000L
    }

    private val switchMutex = Mutex()
    private var lastSwitchTime = 0L

    suspend operator fun invoke(
        context: Context,
        platformName: String,
        platformEntry: PlatformEntry,
    ): LinkUpdateResult {
        val accepted = switchMutex.withLock {
            val now = System.currentTimeMillis()
            if (now - lastSwitchTime < DEBOUNCE_MS) {
                Log.d(TAG, "切换过于频繁，忽略 (间隔 ${now - lastSwitchTime}ms)")
                false
            } else {
                lastSwitchTime = now
                true
            }
        }
        if (!accepted) return LinkUpdateResult.SKIPPED

        val profile = userProfileRepository.getUserProfileOnce()
        if (profile != null && profile.defaultPlatform != platformName) {
            userProfileRepository.saveUserProfile(
                profile.copy(
                    defaultPlatform = platformName,
                    updateTime = System.currentTimeMillis(),
                )
            )
            Log.d(TAG, "defaultPlatform 已更新: $platformName")
        }

        if (!shortLinkService.isConfigured(context)) {
            return LinkUpdateResult.NO_CONFIG
        }

        val result = shortLinkService.updateLinkDestination(context, platformEntry.jumpLink)
        return if (result.isSuccess) {
            Log.d(TAG, "短链接更新成功: ${platformEntry.jumpLink}")
            LinkUpdateResult.SUCCESS
        } else {
            Log.w(TAG, "短链接更新失败", result.exceptionOrNull())
            LinkUpdateResult.ERROR
        }
    }
}

enum class LinkUpdateResult {
    SUCCESS,
    ERROR,
    SKIPPED,
    NO_CONFIG,
}
