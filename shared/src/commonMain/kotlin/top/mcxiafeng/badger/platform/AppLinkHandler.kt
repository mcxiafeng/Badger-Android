package top.mcxiafeng.badger.platform

import kotlinx.coroutines.flow.SharedFlow

interface AppLinkHandler {
    
    suspend fun consumePendingDeepLink(): String?

    
    val deepLinkEvents: SharedFlow<String>
}
