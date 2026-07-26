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
}