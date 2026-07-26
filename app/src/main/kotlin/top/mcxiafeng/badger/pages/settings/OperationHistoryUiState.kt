package top.mcxiafeng.badger.pages.settings

import top.mcxiafeng.badger.data.repository.HistoryFilter
import top.mcxiafeng.badger.data.repository.HistoryOpResult
import top.mcxiafeng.badger.data.repository.OperationHistoryWithContact

/**
 * [V2-P7] OperationHistoryPage UI 状态。
 *
 * 严格按 TagManagerUiState 模式:
 * - `Loading`:首帧 + 强制刷新期间
 * - `Success`:列表可用;`filter` 是当前顶部 tab 选中的 filter;`records` 是 join 后展示项
 * - `Empty`:没有 history 记录(全表 0 条或筛选后 0 条),用于渲染空态
 * - `Error`:致命失败(读取 history 异常)
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

/**
 * OperationHistoryPage 的"瞬时 UI 反馈"消息(走 Channel 上抛给 Composable 转 Snackbar)。
 *
 * 持久状态走 `uiState`,瞬时反馈走 Channel,符合 NowInAndroid 模式。
 */
sealed interface OperationHistoryMessage {
    val text: String

    data class Info(override val text: String) : OperationHistoryMessage
    data class Error(override val text: String) : OperationHistoryMessage
}

/**
 * OperationHistoryViewModel 调用 Repository 的结果 → 转 Snackbar 文本。
 */
internal fun HistoryOpResult.toMessage(): OperationHistoryMessage = when (this) {
    is HistoryOpResult.Success -> OperationHistoryMessage.Info("操作成功")
    is HistoryOpResult.Failure -> OperationHistoryMessage.Error(reason)
}