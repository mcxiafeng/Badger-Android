package top.mcxiafeng.badger.platform

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class IosAppLinkHandler : AppLinkHandler {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val deepLinkEvents: SharedFlow<String> = _events.asSharedFlow()

    override suspend fun consumePendingDeepLink(): String? = null
}
