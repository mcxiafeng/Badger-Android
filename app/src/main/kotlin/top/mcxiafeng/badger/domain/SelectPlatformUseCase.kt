package top.mcxiafeng.badger.domain

import top.mcxiafeng.badger.utils.BadgerLog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.mcxiafeng.badger.data.model.PlatformEntry
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.network.ShortLinkService
import top.mcxiafeng.badger.shared.util.nowMs

/** 平台切换后的默认平台持久化与短链接更新。 */
class SelectPlatformUseCase(
    private val userProfileRepository: UserProfileRepository,
    private val shortLinkService: ShortLinkService,
) {
    private companion object {
        const val TAG = "SelectPlatformUseCase"
    }

    /** 防止多个调用方同时更新短链接；不再丢弃用户实际选择的平台。 */
    private val switchMutex = Mutex()

    suspend operator fun invoke(
        platformName: String,
        platformEntry: PlatformEntry,
    ): LinkUpdateResult = switchMutex.withLock {
        val profile = userProfileRepository.getUserProfileOnce()
        if (profile != null && profile.defaultPlatform != platformName) {
            userProfileRepository.saveUserProfile(
                profile.copy(
                    defaultPlatform = platformName,
                    updateTime = nowMs(),
                )
            )
            BadgerLog.d(TAG, "defaultPlatform 已更新: $platformName")
        }

        if (!shortLinkService.isConfigured()) {
            return@withLock LinkUpdateResult.NO_CONFIG
        }

        val result = shortLinkService.updateLinkDestination(platformEntry.jumpLink)
        if (result.isSuccess) {
            BadgerLog.d(TAG, "短链接更新成功: ${platformEntry.jumpLink}")
            LinkUpdateResult.SUCCESS
        } else {
            BadgerLog.w(TAG, "短链接更新失败", result.exceptionOrNull())
            LinkUpdateResult.ERROR
        }
    }
}

enum class LinkUpdateResult {
    SUCCESS,
    ERROR,
    NO_CONFIG,
}
