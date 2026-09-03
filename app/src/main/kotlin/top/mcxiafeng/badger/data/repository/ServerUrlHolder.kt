package top.mcxiafeng.badger.data.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.mcxiafeng.badger.data.prefs.AuthPrefs

private const val TAG = "ServerUrlHolder"

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
 *
 * [§14.2] Hilt `@Singleton @Inject constructor(@ApplicationContext ...)` → Koin
 * `singleOf(::ServerUrlHolder)`。`@ApplicationContext` 在 Koin module 里通过 `get()` 解析为
 * `android.content.Context`(Koin androidContext() 注册的顶级依赖)。
 */
class ServerUrlHolder(
    private val context: Context,
) {
    private val _url = MutableStateFlow(AuthPrefs.readServerUrl(context))
    val url: StateFlow<String> = _url.asStateFlow()

    /**
     * [UX-Gap#2]: 自上次 [set] 以来,当前 URL 是否被成功登录验证过。
     *
     * 设计要点:
     * - 初始 false。set(newUrl) → 自动重置 false（URL 一改,验证失效）
     * - 登录成功 → 调用方 ([AuthViewModel.signIn/register] onSuccess) 调 [markUrlVerified] → true
     * - banner 常驻的判定基础: !isUrlVerified → 显示; true → 隐藏
     *
     * 覆盖上一版判定 (serverUrl == DEFAULT) 的盲点 —— 用户填了非默认 URL 但填错
     * (老版立即判定 hide 让用户再丢入口;新版只有验证通过才 hide)。
     *
     * 暂未持久化 —— 重启 App 后回到 false, banner 又常驻一次。
     * 看似保守,实际合理: 重启后无法确认上次验证的网络环境仍有效,
     * banner 重挂让用户有机会再次确认。MVP 不上 prefs key 避免无谓复杂度。
     */
    private val _isUrlVerified = MutableStateFlow(false)
    val isUrlVerified: StateFlow<Boolean> = _isUrlVerified.asStateFlow()

    /**
     * Persist the new URL and broadcast. Both sides MUST be touched:
     * - prefs first (so a kill between this and the broadcast doesn't lose config)
     * - then the flow (so subscribers refresh immediately)
     *
     * [UX-Gap#2] URL 一变 → 验证自动失效, banner 重新常驻;
     * 即便用户"故意回填"同样 URL 也按"重新配"处理,避免 stale-true 的隐藏把用户困死。
     */
    fun set(newUrl: String) {
        AuthPrefs.writeServerUrl(context, newUrl)
        _url.value = newUrl
        if (_isUrlVerified.value) {
            Log.d(TAG, "set: URL changed → isUrlVerified reset false")
            _isUrlVerified.value = false
        }
    }

    /**
     * [UX-Gap#2] 由登录成功路径调用 ([AuthViewModel.signIn/register] onSuccess)。
     * 幂等: 已 verified 时 no-op。
     */
    fun markUrlVerified() {
        if (!_isUrlVerified.value) {
            _isUrlVerified.value = true
            Log.d(TAG, "markUrlVerified: banner-hide gate cleared")
        }
    }
}
