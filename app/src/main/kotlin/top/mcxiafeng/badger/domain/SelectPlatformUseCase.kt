package top.mcxiafeng.badger.domain

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.network.ShortLinkService
import javax.inject.Inject

/**
 * 平台选择 UseCase
 *
 * 处理平台切换时的防抖、持久化和短链接更新。
 */
class SelectPlatformUseCase @Inject constructor(
    private val userProfileRepository: UserProfileRepository
) {
    private val TAG = "SelectPlatformUseCase"
    private val DEBOUNCE_MS = 2000L
    private var lastSwitchTime = 0L
    private var currentUpdateJob: Job? = null

    /**
     * 切换选中的平台
     *
     * @return 短链接更新结果，null 表示跳过或无需更新
     */
    suspend operator fun invoke(
        context: Context,
        platformName: String,
        platformEntry: PlatformEntry
    ): LinkUpdateResult {
        // 防抖检查
        val now = System.currentTimeMillis()
        if (now - lastSwitchTime < DEBOUNCE_MS) {
            Log.d(TAG, "切换过于频繁，忽略 (间隔 ${now - lastSwitchTime}ms)")
            return LinkUpdateResult.SKIPPED
        }
        lastSwitchTime = now

        // 取消之前的更新任务
        currentUpdateJob?.cancel()

        // 持久化 defaultPlatform
        val profile = userProfileRepository.getUserProfileOnce()
        if (profile != null && profile.defaultPlatform != platformName) {
            userProfileRepository.saveUserProfile(profile.copy(
                defaultPlatform = platformName,
                updateTime = System.currentTimeMillis()
            ))
            Log.d(TAG, "defaultPlatform 已更新: $platformName")
        }

        // 检查短链接配置
        if (!ShortLinkService.isConfigured(context)) {
            return LinkUpdateResult.NO_CONFIG
        }

        // 更新短链接目标
        val result = ShortLinkService.updateLinkDestination(context, platformEntry.jumpLink)
        return if (result.isSuccess) {
            Log.d(TAG, "短链接更新成功: ${platformEntry.jumpLink}")
            delay(1500)
            LinkUpdateResult.SUCCESS
        } else {
            Log.w(TAG, "短链接更新失败", result.exceptionOrNull())
            delay(2000)
            LinkUpdateResult.ERROR
        }
    }

    fun cancelPending() {
        currentUpdateJob?.cancel()
    }
}

enum class LinkUpdateResult {
    SUCCESS, ERROR, SKIPPED, NO_CONFIG
}
