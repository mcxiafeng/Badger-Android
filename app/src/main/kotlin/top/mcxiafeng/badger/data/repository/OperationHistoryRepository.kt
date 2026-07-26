package top.mcxiafeng.badger.data.repository

import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.queue.OperationHistoryEntity

/**
 * [V2-P7] 操作历史仓库接口。
 *
 * UI 层(OperationHistoryPage)只通过 [OperationHistoryRepository] 拿到"已 join 联系人名"
 * 的 history 列表,所有撤销/重发/解决冲突副作用也走这里集中收敛,避免 ViewModel 持有
 * 多个 DAO 散落。
 *
 * 方法约定:
 * - [observeHistory] 返回 Flow,UI 用 `collectAsState` 订阅;combine 联系人表让被删联系
 *   仍能看到当时的 name(走 [labelOfContactId] 兜底)。
 * - 副作用方法(retry / withdraw / adoptLocal / adoptServer)返回 [HistoryOpResult]:
 *   `Success` / `Failure(reason)`。VM 收到后 produce 一条 Snackbar message。
 * - 真正的服务端反向 PATCH / 冲突解决后的服务端 PATCH 走 P8 阶段——当前 P7 阶段
 *   只动本地 history + PendingUpload DAO(retryNow / markWithdrawn)。
 */
interface OperationHistoryRepository {

    /**
     * 订阅"已 join 联系人名"的历史列表(按 createdAt DESC)。
     *
     * @param filter 仅显示特定状态;为 null 时显示全部。
     * @param limit 与 DAO.observeRecent 一致默认 100 条。
     */
    fun observeHistory(
        filter: HistoryFilter = HistoryFilter.All,
        limit: Int = 100,
    ): Flow<List<OperationHistoryWithContact>>

    /**
     * 立即重试:调 `pendingDao.retryNow(opId, now)` 清错误 + attempts=0 + nextAttemptAt=now,
     * 然后 `scheduler.kick()` 触发 Worker 立即拉批。
     */
    suspend fun retry(opId: String): HistoryOpResult

    /**
     * 撤销:把 op 标 WITHDRAWN(不动服务端,仅本地 history)。
     *
     * P8 阶段会扩展为:用 `op.inversePayloadJson` 入 PendingUpload 队列发服务端反向 PATCH + 回滚本地 cache,
     * 见 `docs/BADGER_V2_CLIENT_PLAN.md` §6.4。当前 P7 阶段仅做 UI 入口,撤回是不可逆的。
     */
    suspend fun withdraw(opId: String): HistoryOpResult

    /**
     * 解决 CONFLICT — 采用本地:把 history 标 DONE + 写入 serverVersion。
     *
     * P8 阶段会扩展:从 `op.snapshotBeforeJson` 解析联系人主体,走 `ServerApi.patchContact`
     * 用 `op.serverVersion` 作 If-Match;当前 P7 阶段只更新 history 状态。
     */
    suspend fun adoptLocal(opId: String): HistoryOpResult

    /**
     * 解决 CONFLICT — 采用服务端:把 history 标 DONE + 写入 snapshotAfterJson。
     *
     * P8 阶段会扩展:走 `ContactSnapshotter.fromJson(serverContactJson)` 反序列化后
     * `contactCacheDao.upsert(...)` 强制用服务端版本覆盖本地 cache;当前 P7 阶段仅留
     * 占位 history 状态。
     */
    suspend fun adoptServer(opId: String, serverContactJson: String): HistoryOpResult

    /**
     * [V2-P10] 批量重试:对 [opIds] 列表中状态为 FAILED 的 op 走 `pendingDao.retryNow`,
     * 全部 retryNow 完才 [PendingUploadScheduler.kick] 一次(避免 high-priority burst)。
     *
     * 已 DONE / WITHDRAWN / PENDING / IN_FLIGHT / CONFLICT / FAILED_PERMANENT 一律跳过
     * (单条 retry 也会失败),不抛异常。
     *
     * @return [BatchHistoryOpResult.Success] 携带成功 / 失败计数;入参空 → Success(0, 0)。
     */
    suspend fun batchRetry(opIds: List<String>): BatchHistoryOpResult

    /**
     * [V2-P10] 批量撤销:对 [opIds] 列表中 `canUndo=true && opStatus != WITHDRAWN` 的 op
     * **逐条复用现有 [withdraw]**(`markWithdrawn` + `rollbackCache` + 入反向 op + 内部 kick
     * 一次)。
     *
     * 单条失败 catch + log 继续,整体不会被一条 op 拖死。CONFLICT 状态必须单条"采用本地/服务端"
     * 解决,不进 batch withdraw。
     *
     * @return [BatchHistoryOpResult.Success] 携带成功 / 失败计数;入参空 → Success(0, 0)。
     */
    suspend fun batchWithdraw(opIds: List<String>): BatchHistoryOpResult
}

/**
 * 批量操作(retry / withdraw)的结果。
 *
 * 与 [HistoryOpResult] 不同:批量场景不需要"逐条 reason",只关心成功/失败总数,
 * UI 端 emit "已重试 X 条,失败 Y 条" 之类的 Snackbar 文案。
 */
sealed interface BatchHistoryOpResult {
    data class Success(val succeeded: Int, val failed: Int) : BatchHistoryOpResult
    data class Failure(val reason: String) : BatchHistoryOpResult
}

/**
 * 一个 history op 与其联系人名 join 后的展示项。
 *
 * 联系人名缺失(已被删除 / 找不到)统一用 `null`,VM/UI 层 fallback 到占位字符串。
 */
data class OperationHistoryWithContact(
    val history: OperationHistoryEntity,
    val contactName: String?,
)

/**
 * 撤销 / 重发 / 解决冲突的结果。
 *
 * 不是 sealed class 是因为现在只有两种结果,P8 阶段可能扩展 `AlreadyResolved` 等分支。
 */
sealed interface HistoryOpResult {
    data object Success : HistoryOpResult
    data class Failure(val reason: String) : HistoryOpResult
}

/**
 * UI 列表筛选。
 */
enum class HistoryFilter {
    /** 显示所有状态(opStatus 包括 PENDING / IN_FLIGHT / DONE / CONFLICT / FAILED / FAILED_PERMANENT / WITHDRAWN)。 */
    All,

    /** 仅显示"待处理":CONFLICT + FAILED_PERMANENT(用户需要决策)。 */
    Pending,
}
