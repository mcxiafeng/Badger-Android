package top.mcxiafeng.badger

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import top.mcxiafeng.badger.platform.AppLinkHandler

/**
 * [KMP K13c] DeepLinkHandler 的 Android 实现（app 壳层）。
 *
 * MainActivity 冷启动写入 [setPending]，onNewIntent 经 [emit] 推送；
 * common App composable 经 [AppLinkHandler] 契约消费——壳层与 UI 解耦。
 */
object DeepLinkBus : AppLinkHandler {

    @Volatile
    private var pendingServerId: String? = null

    private val _deepLinkEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val deepLinkEvents: SharedFlow<String> = _deepLinkEvents.asSharedFlow()

    /** MainActivity.onCreate：冷启动解析出的 pending serverId。 */
    fun setPending(serverId: String?) {
        pendingServerId = serverId
    }

    /** MainActivity.onNewIntent：热启动事件。 */
    fun emit(serverId: String) {
        _deepLinkEvents.tryEmit(serverId)
    }

    override suspend fun consumePendingDeepLink(): String? {
        val value = pendingServerId
        pendingServerId = null
        return value
    }
}
