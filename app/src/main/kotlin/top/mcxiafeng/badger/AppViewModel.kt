package top.mcxiafeng.badger

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.UserProfile
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    val userProfileRepository: UserProfileRepository
) : ViewModel() {
    init {
        Log.d("Tester", "AppViewModel initialized")
    }

    private val _userProfileTick = MutableStateFlow(0L)
    val userProfileTick: StateFlow<Long> = _userProfileTick.asStateFlow()

    /**
     * 任意位置修改了 user_profile 表后调用，把递增的 tick 推给订阅者。
     * PersonRoute 会监听该 tick 触发 refreshUserProfile()。
     */
    fun refreshUserProfile() {
        _userProfileTick.value = System.currentTimeMillis()
    }

    /** 兜底：直接拉一次最新 profile 同步给所有订阅者（不依赖 Room Flow）。 */
    suspend fun reloadUserProfileNow(): UserProfile? {
        return userProfileRepository.getUserProfileOnce()
    }
}
