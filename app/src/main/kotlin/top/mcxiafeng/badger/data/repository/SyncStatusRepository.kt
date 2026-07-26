package top.mcxiafeng.badger.data.repository

/**
 * [V2-P9] 同步状态仓库接口。
 *
 * 单一职责: 暴露 PendingUpload 队列的"宏观状态"(6 种状态各多少条) +
 * 批量重试 + 清理历史。**不**与 [OperationHistoryRepository] 的"单条 op 撤销/解决冲突"
 * 重叠 —— 这里的批量操作只针对 FAILED(卡住)与清理终态(DONE/WITHDRAWN)。
 *
 * 设计要点:
 * - **互不依赖 OperationHistoryRepository**: 二者都依赖 `PendingUploadDao` + `PendingUploadScheduler`,
 *   但 SyncStatus 不需要 contactCacheDao / serverApi。
 * - **批量重试只针对 FAILED**: PENDING 已经在队列里,kick() 即可;CONFLICT / FAILED_PERMANENT
 *   需要用户在 OperationHistoryPage 单条决策。
 * - **清理 = purgeDone(before)**: 只清 DONE 30 天前,保留所有活跃 + 终态 WITHDRAWN 记录
 *   (撤回的历史仍有 UI 价值)。
 */
interface SyncStatusRepository {

    /**
     * 取 PendingUpload 队列当前宏观快照 — 用于 Settings 同步状态页头部卡片展示。
     */
    suspend fun snapshot(): SyncStatusSnapshot

    /**
     * 批量重试所有 FAILED 状态 op。返回成功重新入队的条数。
     *
     * 流程: 遍历 `status='FAILED'` 全部 op → `pendingDao.retryNow(opId, now)` 批写 →
     * `scheduler.kick()` 触发 Worker 立即拉批。
     *
     * [修复防御]: 单条 `retryNow` 抛异常被 catch + warn 继续下一条,不阻断整体。
     */
    suspend fun retryAll(): Int

    /**
     * 重试指定 opId(单条)。仅当 op 存在 + status 是 FAILED 时成功,返 true。
     *
     * 其他状态(已完成 / 已撤销)返 false —— 保留 [OperationHistoryRepository.retry] 语义。
     */
    suspend fun retryOne(opId: String): Boolean

    /**
     * 清理超过 [olderThanDays] 天的 DONE 状态 op,释放表空间。
     *
     * @return 实际删除条数;无历史可清理返 0,不报错。
     */
    suspend fun purgeFinished(olderThanDays: Int = DEFAULT_PURGE_DAYS): Int

    companion object {
        /** 默认清理阈值 30 天 —— [purgeFinished] 不传参数时使用。 */
        const val DEFAULT_PURGE_DAYS = 30
    }
}

/**
 * PendingUpload 队列表的宏观状态快照。
 *
 * 各字段独立计数(总和 = totalCount),用于同步状态页 3×2 网格展示。
 */
data class SyncStatusSnapshot(
    /** 等待 Worker 拉取的 op 数(PENDING 状态,这是常态,不应有视觉报警)。 */
    val pendingCount: Int = 0,
    /** 正在 HTTP 中(IN_FLIGHT 状态,Worker 当前正在调用 ServerApi)。 */
    val inFlightCount: Int = 0,
    /** 临时失败(5xx / 网络 IO),还没达到 maxAttempts,等下次退避或 kick。 */
    val failedCount: Int = 0,
    /** 服务端 409,用户需在 OperationHistoryPage 决策采用本地/服务端。 */
    val conflictCount: Int = 0,
    /** 永久失败(attempts >= max 或 4xx 非 409),用户需主动"重发"。 */
    val failedPermanentCount: Int = 0,
    /** 用户主动撤销的 op 数 —— 信息性字段,UI 用于显示历史回退深度。 */
    val withdrawnCount: Int = 0,
    /** 成功同步的 op 数(用于 purge 决策展示)。 */
    val doneCount: Int = 0,
    /** 总条数。 */
    val totalCount: Int = 0,
) {
    /**
     * 是否"有需要关注的 op"(任何 retryable/decision-pending 状态)。
     *
     * 用于 UI 决定: `failedCount == 0 && conflictCount == 0 && failedPermanentCount == 0`
     * 时不显示红色徽章,而是绿色"同步正常"。
     */
    val hasAttention: Boolean
        get() = failedCount > 0 || conflictCount > 0 || failedPermanentCount > 0
}
