package top.mcxiafeng.badger

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import top.mcxiafeng.badger.platform.AppLinkHandler

object DeepLinkBus : AppLinkHandler {

    @Volatile
    private var pendingServerId: String? = null

    private val _deepLinkEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val deepLinkEvents: SharedFlow<String> = _deepLinkEvents.asSharedFlow()

    
    fun setPending(serverId: String?) {
        pendingServerId = serverId
    }

    
    fun emit(serverId: String) {
        _deepLinkEvents.tryEmit(serverId)
    }

    override suspend fun consumePendingDeepLink(): String? {
        val value = pendingServerId
        pendingServerId = null
        return value
    }
}
