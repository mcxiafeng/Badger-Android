package top.mcxiafeng.badger.data.repository

import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.queue.OperationHistoryEntity

/**
 * [V2-P7] 操作历史仓库接口。
 *
 * [Phase 3] 降级为**只读本地日志**：队列退役（乐观锁 + PendingUpload 全部移除），
 * 撤销 / 重发 / 采用本地 / 采用服务端 等副作用方法全部删除 —— 新同步模型下
 * 服务端是权威，历史页只展示过去发生的写操作记录，不再提供回滚入口。
 *
 * 只保留 [observeHistory]（已 join 联系人名的只读 Flow）。
 */
interface OperationHistoryRepository {

    /**
     * 订阅"已 join 联系人名"的历史列表（按 createdAt DESC）。
     *
     * @param filter 仅显示特定状态；默认显示全部。
     * @param limit 与 DAO.observeRecent 一致默认 100 条。
     */
    fun observeHistory(
        filter: HistoryFilter = HistoryFilter.All,
        limit: Int = 100,
    ): Flow<List<OperationHistoryWithContact>>
}

/**
 * 一个 history op 与其联系人名 join 后的展示项。
 *
 * 联系人名缺失（已被删除 / 找不到）统一用 `null`，VM/UI 层 fallback 到占位字符串。
 */
data class OperationHistoryWithContact(
    val history: OperationHistoryEntity,
    val contactName: String?,
)

/**
 * UI 列表筛选。
 */
enum class HistoryFilter {
    /** 显示所有状态(opStatus 包括 PENDING / IN_FLIGHT / DONE / CONFLICT / FAILED / FAILED_PERMANENT / WITHDRAWN)。 */
    All,

    /** 仅显示"待处理"：CONFLICT + FAILED_PERMANENT（历史遗留，队列退役后通常为空）。 */
    Pending,
}
