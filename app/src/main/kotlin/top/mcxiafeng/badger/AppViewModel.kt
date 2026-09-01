package top.mcxiafeng.badger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity as UserProfile
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.NotificationRepository
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.data.repository.UserProfileTicker
import top.mcxiafeng.badger.domain.ImportProfileFieldsUseCase
import top.mcxiafeng.badger.ocr.ExtractedContactInfo

/**
 * App-level state and operations shared by the application composition root.
 *
 * UI code observes state and calls intent-like methods; repository and network access stay here
 * or below the ViewModel/use-case boundary.
 */
class AppViewModel(
    val userProfileRepository: UserProfileRepository,
    private val userProfileTicker: UserProfileTicker,
    val userAuthRepository: UserAuthRepository,
    private val notificationRepository: NotificationRepository,
    val contactRepository: ContactRepository,
    private val importProfileFieldsUseCase: ImportProfileFieldsUseCase,
) : ViewModel() {

    /** [B2] 未读角标：60s 轮询来自 [NotificationRepository]，MainTabs / Settings TopBar 共用。 */
    val unreadNotificationCount: StateFlow<Int> = notificationRepository.unreadCount

    init {
        viewModelScope.launch { userAuthRepository.bootstrap() }
    }

    /** 转发 [UserProfileTicker.tick]，PersonPage 仍订阅此 StateFlow。 */
    val userProfileTick: StateFlow<Long> = userProfileTicker.tick

    /** 任意位置修改 user_profile 后调用，让订阅者主动刷新。 */
    fun refreshUserProfile() {
        userProfileTicker.tick()
    }

    /** Import scanner-discovered platform fields into the current user profile. */
    suspend fun importProfileFields(items: List<ExtractedContactInfo>): Int =
        importProfileFieldsUseCase(items)

    /** 兜底：直接拉一次最新 profile，同步给需要即时读取的调用方。 */
    suspend fun reloadUserProfileNow(): UserProfile? =
        userProfileRepository.getUserProfileOnce()
}
