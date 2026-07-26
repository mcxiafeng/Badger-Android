package top.mcxiafeng.badger.data.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.queue.OperationHistoryDao
import top.mcxiafeng.badger.data.queue.PendingUploadDao
import top.mcxiafeng.badger.sync.PendingUploadScheduler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [V2-P7] OperationHistoryRepository impl。
 *
 * 单一职责:把 `OperationHistoryEntity` 与 `ContactCacheDao` 的"联系人名"做 in-memory
 * LEFT JOIN,提供一个可直接被 UI 列表消费的 Flow。副作用方法(retry / withdraw /
 * adoptLocal / adoptServer)收敛在这里,避免 VM 持有 5 个 DAO 散落。
 *
 * 设计要点:
 * 1. **联系人名 LEFT JOIN** — 不引 SQL JOIN,简单 in-memory mapping。如果未来 ops > 1000 条
 *    性能受限,再回头改用 @Query JOIN。
 * 2. **filter 用 Flow.map 而非数据库 WHERE** — 简单可控;OperationHistoryDao.observeRecent
 *    已经按 createdAt DESC 排序,前置筛选在 DAO 加 WHERE 会让 limit 含义错位。
 * 3. **dismissed / 找不到联系人 fallback** — commitDelete hardDelete 后 contact 没了,
 *    history 还在;`mapContactName` 返回 null 由 UI 渲染"(已删除)" / "未知联系人"。
 * 4. **P8 阶段预留** — adoptLocal / adoptServer 当前只动 history 状态;P8 阶段会扩展
 *    为调 ServerApi + 回滚 cache,接口签名稳定不变。
 */
@Singleton
class OperationHistoryRepositoryImpl @Inject constructor(
    private val historyDao: OperationHistoryDao,
    private val contactCacheDao: ContactCacheDao,
    private val pendingDao: PendingUploadDao,
    private val scheduler: PendingUploadScheduler,
) : OperationHistoryRepository {

    private val tag = TAG

    /**
     * 订阅 history + 联系人名 join。
     *
     * combine 触发条件:任一上游变化 → 重新 join。`observeRecent(limit=100)` 已经按
     * createdAt DESC 排序,join 后顺序不变;filter 在 map 里裁剪。
     */
    override fun observeHistory(
        filter: HistoryFilter,
        limit: Int,
    ): Flow<List<OperationHistoryWithContact>> {
        return combine(
            historyDao.observeRecent(limit = limit),
            contactCacheDao.getAllContacts(),
        ) { historyList, contacts ->
            // [修复防御]: in-memory mapping — 联系人可能 hundreds,但 history 通常 < 100,
            // 简单 firstOrNull 比维护 join index 更清晰。
            val contactMap: Map<Long, String> = contacts.associate { it.id to it.name }
            historyList
                .map { history ->
                    OperationHistoryWithContact(
                        history = history,
                        contactName = contactMap[history.contactId],
                    )
                }
                .let { joined ->
                    when (filter) {
                        HistoryFilter.All -> joined
                        HistoryFilter.Pending -> joined.filter { item ->
                            val status = item.history.opStatus
                            status == "CONFLICT" || status == "FAILED_PERMANENT"
                        }
                    }
                }
        }
    }

    override suspend fun retry(opId: String): HistoryOpResult {
        Log.d(tag, "retry: opId=${opId.take(8)}")
        val op = pendingDao.getById(opId)
        if (op == null) {
            Log.w(tag, "retry: opId=${opId.take(8)} not in pending_uploads,F(returned Failure)")
            return HistoryOpResult.Failure("操作不存在")
        }
        if (op.status == "WITHDRAWN" || op.status == "DONE") {
            // [修复防御]: 已撤销/已成功的 op 不能重发 — Dao 默认 retryNow WHERE 不会
            // 匹配这些状态,静默吞错反而让 UI 看到"已重发"假象。返回 Failure 走 Snackbar。
            Log.w(tag, "retry: opId=${opId.take(8)} status=${op.status} cannot retry")
            return HistoryOpResult.Failure("操作已${if (op.status == "WITHDRAWN") "撤销" else "完成"},无法重发")
        }
        val now = System.currentTimeMillis()
        pendingDao.retryNow(opId, now)
        scheduler.kick()
        // historyDao.touchRetry 也写,UI 状态徽章的 lastError 会被清
        // (但 dao.touchRetry 不调 — 只要 pendingDao.retryNow 即可,observeRecent Flow
        // 通过 pendingDao 的 invalidate tracker 自动刷新)
        Log.d(tag, "retry: opId=${opId.take(8)} kicked scheduler")
        return HistoryOpResult.Success
    }

    override suspend fun withdraw(opId: String): HistoryOpResult {
        Log.d(tag, "withdraw: opId=${opId.take(8)}")
        val op = historyDao.getById(opId)
        if (op == null) {
            Log.w(tag, "withdraw: opId=${opId.take(8)} not in history,F(returned Failure)")
            return HistoryOpResult.Failure("操作不存在")
        }
        if (op.opStatus == "WITHDRAWN") {
            Log.w(tag, "withdraw: opId=${opId.take(8)} already withdrawn")
            return HistoryOpResult.Failure("已撤销过")
        }
        if (!op.canUndo) {
            Log.w(tag, "withdraw: opId=${opId.take(8)} canUndo=false 不允许撤销")
            return HistoryOpResult.Failure("此操作不可撤销")
        }
        // 同时标两个 DAO:history 是 UI 状态,PendingUpload 是 Worker 见到 WITHDRAWN 跳过
        historyDao.markWithdrawn(opId)
        pendingDao.markWithdrawn(opId)
        Log.d(tag, "withdraw: opId=${opId.take(8)} marked WITHDRAWN(本地,P8 阶段扩展服务端反向)")
        return HistoryOpResult.Success
    }

    override suspend fun adoptLocal(opId: String): HistoryOpResult {
        Log.d(tag, "adoptLocal: opId=${opId.take(8)}")
        val op = historyDao.getById(opId)
        if (op == null) {
            Log.w(tag, "adoptLocal: opId=${opId.take(8)} not in history,F(returned Failure)")
            return HistoryOpResult.Failure("操作不存在")
        }
        if (op.opStatus != "CONFLICT") {
            Log.w(tag, "adoptLocal: opId=${opId.take(8)} status=${op.opStatus} not CONFLICT")
            return HistoryOpResult.Failure("仅 CONFLICT 状态可解决")
        }
        val serverVersion = op.serverVersion
        if (serverVersion == null) {
            Log.w(tag, "adoptLocal: opId=${opId.take(8)} no serverVersion in record")
            return HistoryOpResult.Failure("记录缺服务端版本")
        }
        // P7 阶段:仅更新 history 状态;P8 阶段会扩展:用 snapshotBefore + serverVersion
        // 走 ServerApi.patchContact(If-Match=serverVersion)。
        historyDao.markDone(opId, serverVersion = serverVersion, snapshotAfterJson = null)
        // [修复防御]: pendingDao 也要联动 — 标 DONE,Worker 见到 DONE 跳过
        // (与 commitDelete 走 commitDelete 成功路径同模式)
        pendingDao.markDone(opId)
        Log.d(tag, "adoptLocal: opId=${opId.take(8)} serverVersion=$serverVersion → DONE(本地,P8 阶段扩展服务端 PATCH)")
        return HistoryOpResult.Success
    }

    override suspend fun adoptServer(opId: String, serverContactJson: String): HistoryOpResult {
        Log.d(tag, "adoptServer: opId=${opId.take(8)} serverContactBytes=${serverContactJson.length}")
        val op = historyDao.getById(opId)
        if (op == null) {
            Log.w(tag, "adoptServer: opId=${opId.take(8)} not in history,F(returned Failure)")
            return HistoryOpResult.Failure("操作不存在")
        }
        if (op.opStatus != "CONFLICT") {
            Log.w(tag, "adoptServer: opId=${opId.take(8)} status=${op.opStatus} not CONFLICT")
            return HistoryOpResult.Failure("仅 CONFLICT 状态可解决")
        }
        // P7 阶段:仅更新 history 状态;P8 阶段会扩展:ContactSnapshotter.fromJson(serverContactJson)
        // → contactCacheDao.upsert(...) 强制用服务端版本覆盖本地 cache。
        historyDao.markDone(opId, serverVersion = op.serverVersion, snapshotAfterJson = serverContactJson)
        pendingDao.markDone(opId)
        Log.d(tag, "adoptServer: opId=${opId.take(8)} → DONE snapshotAfterBytes=${serverContactJson.length}")
        return HistoryOpResult.Success
    }

    /**
     * 把 contactId 映射成展示用 name — public 暴露但不在 [OperationHistoryRepository] 接口里。
     * 留作 P8 阶段扩展用(撤销时需要取联系人名作 UI 反馈)。
     */
    suspend fun resolveContactName(contactId: Long): String? {
        return contactCacheDao.getContactById(contactId)?.name
    }

    @Suppress("unused")
    private fun debugCacheShape(contacts: List<ContactCacheEntity>) {
        // 仅供开发期 debug 用。当前代码路径不需要。
    }

    companion object {
        private const val TAG = "OpHistoryRepo"
    }
}
