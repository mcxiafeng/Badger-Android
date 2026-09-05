package top.mcxiafeng.badger.platform

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * [KMP K13c] iOS 实现：universal link（badger://persons/{serverId}）接线 K16。
 * 当前无事件源，consumePendingDeepLink 恒 null。
 */
class IosAppLinkHandler : AppLinkHandler {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val deepLinkEvents: SharedFlow<String> = _events.asSharedFlow()

    override suspend fun consumePendingDeepLink(): String? = null
}
