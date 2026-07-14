package top.mcxiafeng.badger.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用级单例 tick 信号源。
 *
 * 任意位置修改了 user_profile 表后调 [tick],把递增的 tick 推给订阅者。
 * PersonRoute / PersonPage 会监听该 tick 触发 refreshUserProfile()。
 *
 * 拆成单例而不是放在 [top.mcxiafeng.badger.AppViewModel] 里,是因为
 * Hilt 不允许把 @HiltViewModel 注入到另一个 @HiltViewModel
 * （[Hilt ViewModel 校验](https://dagger.dev/hilt/quick-start)）。
 * 单例类无此限制,可以在任何 ViewModel 中注入。
 */
@Singleton
class UserProfileTicker @Inject constructor() {

    private val _tick = MutableStateFlow(0L)
    val tick: StateFlow<Long> = _tick.asStateFlow()

    /** 推一个新的 tick 值,所有订阅者会重新触发动作 */
    fun tick() {
        _tick.value = System.currentTimeMillis()
    }
}