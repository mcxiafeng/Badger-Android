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

/**
 * [§14.2] 普通 [ViewModel] + Koin 构造器注入。
 *
 * 所有依赖由 [top.mcxiafeng.badger.di.viewModelModule] 提供，ViewModel 自身不再直接访问
 * Koin 全局容器，避免 Service Locator 遗留引用和测试耦合。
 */
class AppViewModel(
    val userProfileRepository: UserProfileRepository,
    private val userProfileTicker: UserProfileTicker,
    val userAuthRepository: UserAuthRepository,
    private val notificationRepository: NotificationRepository,
    val contactRepository: ContactRepository,
) : ViewModel() {

    /** [B2] 未读角标：60s 轮询来自 [NotificationRepository]，MainTabs / Settings TopBar 共用。 */
    val unreadNotificationCount: StateFlow<Int> = notificationRepository.unreadCount

    init {
        // Bootstrap auth once on cold start. The repository flips its state
        // to SignedIn / SignedOut — the App Composable observes the state.
        viewModelScope.launch { userAuthRepository.bootstrap() }
    }

    /** 转发 [UserProfileTicker.tick], PersonPage 仍订阅此 StateFlow。 */
    val userProfileTick: StateFlow<Long> = userProfileTicker.tick

    /**
     * 任意位置修改了 user_profile 表后调用，把递增的 tick 推给订阅者。
     * PersonRoute 会监听该 tick 触发 refreshUserProfile()。
     */
    fun refreshUserProfile() {
        userProfileTicker.tick()
    }

    /** 兜底：直接拉一次最新 profile 同步给所有订阅者（不依赖 Room Flow）。 */
    suspend fun reloadUserProfileNow(): UserProfile? {
        return userProfileRepository.getUserProfileOnce()
    }
}
