package top.mcxiafeng.badger.pages.settings

/**
 * [Phase 4 Task #21] SyncStatusPage 事件流(用户意图扁平化为数据类)。
 *
 * 退役队列语义后，仅保留 Refresh 和 RetryAll（触发增量同步）。
 */
sealed interface SyncStatusEvent {
    /** 主动刷新(回到本页 / 用户下拉)。 */
    data object Refresh : SyncStatusEvent

    /** 触发一次服务端增量同步(对应 SyncStatusRepository.retryAll)。 */
    data object RetryAll : SyncStatusEvent

    /** 关闭 Snackbar 反馈消息(暂未使用,留给未来扩展)。 */
    data object DismissMessage : SyncStatusEvent
}
