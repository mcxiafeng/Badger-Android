package top.mcxiafeng.badger.pages.settings.history

import top.mcxiafeng.badger.data.repository.HistoryFilter

/**
 * [V2-P7] OperationHistoryPage 事件流。
 *
 * [Phase 3] 降级为只读日志：撤销 / 重发 / 采用本地 / 采用服务端 / 多选等副作用事件
 * 全部删除（队列退役，历史页不再提供回滚入口），只保留 filter 切换。
 */
sealed interface OperationHistoryEvent {
    /** 切换顶部 filter tab。 */
    data class ChangeFilter(val filter: HistoryFilter) : OperationHistoryEvent

    /** 触发刷新（纯本地订阅，no-op，见 VM.Refresh）。 */
    data object Refresh : OperationHistoryEvent
}
