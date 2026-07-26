package top.mcxiafeng.badger.pages.settings

/**
 * [V2-P9] SyncStatusPage 事件流(用户意图扁平化为数据类)。
 *
 * 与 OperationHistoryEvent / TagManagerEvent 同模式: Composable 收集 VM.onEvent
 * 后转发,VM 内部按 event 分发。所有事件都同步处理(viewModelScope.launch),
 * UI 不需要关心协程。
 */
sealed interface SyncStatusEvent {
    /** 主动刷新(回到本页 / 用户下拉)。 */
    data object Refresh : SyncStatusEvent

    /** 批量重试所有 FAILED op(对应 SyncStatusRepository.retryAll)。 */
    data object RetryAll : SyncStatusEvent

    /** 单条重试(预留 API;当前 P9 UI 不暴露,P10 批量撤销场景可能复用)。 */
    data class RetryOne(val opId: String) : SyncStatusEvent

    /** 清理 [SyncStatusRepository.DEFAULT_PURGE_DAYS] 天前的 DONE 记录。 */
    data object PurgeFinished : SyncStatusEvent

    /** 关闭 Snackbar 反馈消息(暂未使用,留给未来扩展)。 */
    data object DismissMessage : SyncStatusEvent
}
