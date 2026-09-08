package top.mcxiafeng.badger.pages.settings.sync

/**
 * SyncStatusPage 用户意图（扁平化为数据类）。
 *
 * 退役队列语义后仅保留 Refresh 与 RetryAll。
 */
sealed interface SyncStatusEvent {
    /** 主动刷新（回到本页 / 用户下拉 / 电池设置返回）。 */
    data object Refresh : SyncStatusEvent

    /** 触发一次服务端增量同步（对应 SyncStatusRepository.retryAll）。 */
    data object RetryAll : SyncStatusEvent
}
