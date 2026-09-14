package top.mcxiafeng.badger.pages.settings.sync

sealed interface SyncStatusEvent {
    
    data object Refresh : SyncStatusEvent

    
    data object RetryAll : SyncStatusEvent
}
