package top.mcxiafeng.badger.pages.settings.history

import top.mcxiafeng.badger.data.repository.HistoryFilter
import top.mcxiafeng.badger.data.repository.OperationHistoryWithContact

/**
 * [V2-P7] OperationHistoryPage UI 状态。
 *
 * [Phase 3] 降级为只读日志：`Success` 移除多选（`multiSelect`/`selectedIds`）——
 * 队列退役后不再有撤销 / 重发 / 冲突解决入口。
 *
 * - `Loading`：首帧
 * - `Success`：列表可用；`filter` 是当前顶部 tab；`records` 是 join 后展示项
 * - `Empty`：没有 history 记录（全表 0 条或筛选后 0 条）
 * - `Error`：致命失败（读取 history 异常）
 */
sealed interface OperationHistoryUiState {
    data object Loading : OperationHistoryUiState

    data class Success(
        val records: List<OperationHistoryWithContact>,
        val filter: HistoryFilter,
    ) : OperationHistoryUiState

    data class Empty(val filter: HistoryFilter) : OperationHistoryUiState

    data class Error(val message: String) : OperationHistoryUiState
}
