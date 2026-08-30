package top.mcxiafeng.badger.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用级单例 tick 信号源。
 *
 * 任意位置修改了 user_profile 表后调 [tick],把递增的 tick 推给订阅者。
 * PersonRoute / PersonPage 会监听该 tick 触发 refreshUserProfile()。
 *
 * 拆成单例而不是放在 [top.mcxiafeng.badger.AppViewModel] 里,是因为
 * Hilt 不允许把 @HiltViewModel 注入到另一个 @HiltViewModel(原 Hilt 限制)。
 * Koin 没有这个限制,但保留单例以保持原契约不变 —— 单例类可在任何 VM 中
 * 被 koinViewModel 解析使用,避免多人多 VM 各自维护一个 tick 流。
 *
 * [§14.2] Hilt `@Singleton @Inject constructor` → Koin `singleOf(::UserProfileTicker)`。
 */
class UserProfileTicker {

    private val _tick = MutableStateFlow(0L)
    val tick: StateFlow<Long> = _tick.asStateFlow()

    /** 推一个新的 tick 值,所有订阅者会重新触发动作 */
    fun tick() {
        _tick.value = System.currentTimeMillis()
    }
}