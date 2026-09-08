package top.mcxiafeng.badger.pages.settings.history

import top.mcxiafeng.badger.data.repository.HistoryFilter

/**
 * OperationHistoryPage 事件流。
 *
 * 只读日志视图：仅保留 filter 切换。原 Refresh 事件是 no-op
 *（本地订阅驱动，filter 切走再切回即等价 refresh）已删。
 */
sealed interface OperationHistoryEvent {
    /** 切换顶部 filter tab。 */
    data class ChangeFilter(val filter: HistoryFilter) : OperationHistoryEvent
}
