package top.mcxiafeng.badger.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.mcxiafeng.badger.data.AuthPrefs
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-singleton holder for the current Badger-Server base URL.
 *
 * Why a StateFlow and not just [AuthPrefs.readServerUrl]:
 * - Multiple ViewModels ([SettingsHomeViewModel], [AccountSettingsViewModel],
 *   [UserProfileDetailViewModel] 等) 都需要"显示当前服务器地址"。
 * - 之前每个 VM 都自己读 prefs,改了地址只有写入方自己的 state 刷新,其他 VM
 *   / 页面看到的还是旧值。
 * - 换成 StateFlow 之后,所有订阅者立即收到新值,UI 实时刷新。
 *
 * Source-of-truth 仍是 [AuthPrefs] (持久化)。本 holder 在:
 *   1. 构造时:读 prefs 的当前值作为初值
 *   2. [set] 时:写 prefs + 更新 flow。**两步都必须做**,否则下次启动丢配置。
 *
 * 热更:本 holder 的 flow 在 ServerApiFactory.updateBaseUrl 时也会被推,
 *      见 [top.mcxiafeng.badger.di.NetworkModule]。
 */
@Singleton
class ServerUrlHolder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _url = MutableStateFlow(AuthPrefs.readServerUrl(context))
    val url: StateFlow<String> = _url.asStateFlow()

    /**
     * Persist the new URL and broadcast. Both sides MUST be touched:
     * - prefs first (so a kill between this and the broadcast doesn't lose config)
     * - then the flow (so subscribers refresh immediately)
     */
    fun set(newUrl: String) {
        AuthPrefs.writeServerUrl(context, newUrl)
        _url.value = newUrl
    }
}
