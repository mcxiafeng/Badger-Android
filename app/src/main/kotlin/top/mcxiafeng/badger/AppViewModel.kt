package top.mcxiafeng.badger

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity as UserProfile
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.data.repository.UserProfileTicker

/**
 * [§14.2] 移除 `@HiltViewModel @Inject`,改为普通 [ViewModel] + 字段注入。
 *
 * Koin 通过 `org.koin.android.ext.android.inject` 注入(`ComponentCallbacks` 接收器)。
 */
class AppViewModel : ViewModel() {

    val userProfileRepository: UserProfileRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val userProfileTicker: UserProfileTicker = top.mcxiafeng.badger.di.KoinComponentBy.get()
    val userAuthRepository: UserAuthRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()

    init {
        // Bootstrap auth once on cold start. The repository flips its state
        // to SignedIn / SignedOut — the App Composable observes that and
        // either navigates to MainTabs or to Login.
        viewModelScope.launch { userAuthRepository.bootstrap() }
    }

    /** 转发 [UserProfileTicker.tick],PersonPage 仍订阅此 StateFlow */
    val userProfileTick: StateFlow<Long> = userProfileTicker.tick

    /**
     * 任意位置修改了 user_profile 表后调用,把递增的 tick 推给订阅者。
     * PersonRoute 会监听该 tick 触发 refreshUserProfile()。
     */
    fun refreshUserProfile() {
        userProfileTicker.tick()
    }

    /** 兜底:直接拉一次最新 profile 同步给所有订阅者(不依赖 Room Flow)。 */
    suspend fun reloadUserProfileNow(): UserProfile? {
        return userProfileRepository.getUserProfileOnce()
    }
}