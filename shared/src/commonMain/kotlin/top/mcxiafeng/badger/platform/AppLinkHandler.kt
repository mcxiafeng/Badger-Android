package top.mcxiafeng.badger.platform

import kotlinx.coroutines.flow.SharedFlow

/**
 * [KMP K13c] Deep Link 消费边界（App.kt 去平台化的解耦点）。
 *
 * 原 App.kt 直接 `LocalContext as? MainActivity` 消费 pendingDeepLinkServerId /
 * deepLinkEvents——commonMain 不可引用 Activity。Android actual 由 app 侧
 * `DeepLinkBus` 提供（MainActivity 喂入冷启动 pending 与 onNewIntent 事件流）；
 * iOS actual 为空实现（universal link 接线 K16）。
 */
interface AppLinkHandler {
    /** 冷启动 pending deep link（消费一次即清空）；无则返回 null。 */
    suspend fun consumePendingDeepLink(): String?

    /** 热启动 deep link 事件流（onNewIntent / open url）。 */
    val deepLinkEvents: SharedFlow<String>
}
