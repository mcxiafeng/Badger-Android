package top.mcxiafeng.badger.pages.settings

import top.mcxiafeng.badger.data.repository.HistoryFilter

/**
 * [V2-P7] OperationHistoryPage 事件流(用户意图扁平化为数据类)。
 *
 * 与 TagManagerEvent 同模式:Composable 收集 VM.onEvent 后转发 Event,VM 内部按 event
 * 分发。所有事件都同步处理(VM.viewModelScope.launch),UI 不需要关心协程。
 */
sealed interface OperationHistoryEvent {
    /** 切换顶部 filter tab。 */
    data class ChangeFilter(val filter: HistoryFilter) : OperationHistoryEvent

    /** 触发刷新(暂不实现拉服务端,P7 阶段是纯本地订阅,见 VM.Refresh)。 */
    data object Refresh : OperationHistoryEvent

    /** 重发:清错误 + attempts=0 + kick Worker。 */
    data class Retry(val opId: String) : OperationHistoryEvent

    /** 撤销:标 WITHDRAWN。P7 阶段仅本地,P8 阶段扩展服务端反向 PATCH。 */
    data class Withdraw(val opId: String) : OperationHistoryEvent

    /** 解决 CONFLICT — 采用本地。 */
    data class AdoptLocal(val opId: String) : OperationHistoryEvent

    /** 解决 CONFLICT — 采用服务端(serverContactJson 是服务端 409 响应里的 contact 字段 JSON)。 */
    data class AdoptServer(val opId: String, val serverContactJson: String) : OperationHistoryEvent

    // ============ [V2-P10] 多选模式事件 ============

    /**
     * 进入多选模式:
     * - [initialSelectedId] 非空 → 选中该 opId
     * - null → 仅切到多选态,selectedIds 保持上一次(默认空)
     */
    data class EnterMultiSelect(val initialSelectedId: String? = null) : OperationHistoryEvent
    data object ExitMultiSelect : OperationHistoryEvent

    /** 多选模式下勾选 / 取消单条(支持反复 toggle)。 */
    data class ToggleSelect(val opId: String) : OperationHistoryEvent

    /** 全选当前 records 列表中所有 opId。 */
    data object SelectAll : OperationHistoryEvent

    /** 清空 selectedIds(保留多选态,用户可继续勾)。 */
    data object ClearSelection : OperationHistoryEvent

    /** 批量重试:对 [opIds] 列表中 status=FAILED 的 op 调 [OperationHistoryRepository.batchRetry]。 */
    data class BatchRetry(val opIds: List<String>) : OperationHistoryEvent

    /** 批量撤销:对 [opIds] 列表中可撤销的 op 调 [OperationHistoryRepository.batchWithdraw]。 */
    data class BatchWithdraw(val opIds: List<String>) : OperationHistoryEvent
}