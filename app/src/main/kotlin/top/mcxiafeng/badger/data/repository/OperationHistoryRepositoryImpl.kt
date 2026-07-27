package top.mcxiafeng.badger.data.repository

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactFieldValueCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactPlatformCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactTagCacheDao
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactFieldValueCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactTagCacheEntity
import top.mcxiafeng.badger.data.queue.OperationHistoryDao
import top.mcxiafeng.badger.data.queue.OperationHistoryEntity
import top.mcxiafeng.badger.data.queue.OperationTypes
import top.mcxiafeng.badger.data.queue.PendingUploadDao
import top.mcxiafeng.badger.data.queue.PendingUploadEntity
import top.mcxiafeng.badger.data.snapshot.ContactSnapshotter
import top.mcxiafeng.badger.network.ApiException
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.network.ServerApi.ConflictException
import top.mcxiafeng.badger.sync.DeviceIdProvider
import top.mcxiafeng.badger.sync.PendingUploadScheduler
import java.util.UUID

/**
 * [V2-P7/P8] OperationHistoryRepository impl。
 *
 * 单一职责:把 `OperationHistoryEntity` 与 `ContactCacheDao` 的"联系人名"做 in-memory
 * LEFT JOIN,提供一个可直接被 UI 列表消费的 Flow。副作用方法(retry / withdraw /
 * adoptLocal / adoptServer)收敛在这里,避免 VM 持有 5 个 DAO 散落。
 *
 * 设计要点:
 * 1. **联系人名 LEFT JOIN** — 不引 SQL JOIN,简单 in-memory mapping。
 * 2. **filter 用 Flow.map 而非数据库 WHERE** — 简单可控。
 * 3. **dismissed / 找不到联系人 fallback** — commitDelete hardDelete 后 contact 没了,
 *    history 还在;`mapContactName` 返回 null 由 UI 渲染"(已删除)" / "未知联系人"。
 * 4. **[V2-P8] 撤销双边同步** — withdraw / adoptLocal / adoptServer 真正调 ServerApi
 *    + 回滚 cache 4 表(对应 `docs/BADGER_V2_CLIENT_PLAN.md` §6.4)。
 *
 * [§14.2] Hilt `@Singleton @Inject constructor` → Koin `singleOf(::OperationHistoryRepositoryImpl) { bind<OperationHistoryRepository>() }`。
 */
class OperationHistoryRepositoryImpl(
    private val historyDao: OperationHistoryDao,
    private val contactCacheDao: ContactCacheDao,
    private val pendingDao: PendingUploadDao,
    private val scheduler: PendingUploadScheduler,
    // ========== [V2-P8] 撤销双边同步依赖 ==========
    private val contactSnapshotter: ContactSnapshotter,
    private val contactPlatformCacheDao: ContactPlatformCacheDao,
    private val contactFieldValueCacheDao: ContactFieldValueCacheDao,
    private val contactTagCacheDao: ContactTagCacheDao,
    private val serverApi: ServerApi,
    private val deviceIdProvider: DeviceIdProvider,
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
            Log.w(tag, "retry: opId=${opId.take(8)} status=${op.status} cannot retry")
            return HistoryOpResult.Failure("操作已${if (op.status == "WITHDRAWN") "撤销" else "完成"},无法重发")
        }
        val now = System.currentTimeMillis()
        pendingDao.retryNow(opId, now)
        scheduler.kick()
        Log.d(tag, "retry: opId=${opId.take(8)} kicked scheduler")
        return HistoryOpResult.Success
    }

    /**
     * [V2-P8] 撤销(双边同步版):
     * 1. 标 history / pending 为 WITHDRAWN(原 op 立即"死掉")
     * 2. 用 snapshotBeforeJson 回滚本地 cache 4 表(联系主表 + 平台 + 字段值 + 标签)
     * 3. 入反向 op 到 PendingUpload 队列,Worker 异步消费发服务端反向 PATCH / DELETE
     *
     * [修复防御]:
     * - 步骤 1 必须在步骤 2 之前 — 否则 Worker 见到 PENDING 还可能并发消费原 op。
     * - 步骤 2 用 [ContactSnapshotter.fromJson],失败兜底 notFound(空快照)不抛异常。
     * - 步骤 3 opId 用新 UUID — 复用原 opId 会让 pendingDao.enqueue 主键冲突 ABORT。
     * - 只有 opType in (UPDATE_NAME/BIO/NOTE, CREATE_CONTACT) 才真入反向 op 队列。
     *   其他 opType(ADD/UPDATE/REMOVE_PLATFORM 等)P8 MVP 只本地回滚,服务端反向同步留 P9+。
     */
    override suspend fun withdraw(opId: String): HistoryOpResult = withContext(Dispatchers.IO) {
        Log.d(tag, "withdraw: opId=${opId.take(8)}")
        val op = historyDao.getById(opId)
        if (op == null) {
            Log.w(tag, "withdraw: opId=${opId.take(8)} not in history,F(returned Failure)")
            return@withContext HistoryOpResult.Failure("操作不存在")
        }
        if (op.opStatus == "WITHDRAWN") {
            Log.w(tag, "withdraw: opId=${opId.take(8)} already withdrawn")
            return@withContext HistoryOpResult.Failure("已撤销过")
        }
        if (!op.canUndo) {
            Log.w(tag, "withdraw: opId=${opId.take(8)} canUndo=false 不允许撤销")
            return@withContext HistoryOpResult.Failure("此操作不可撤销")
        }
        val now = System.currentTimeMillis()
        val snapshotBeforeJson = op.snapshotBeforeJson
        if (snapshotBeforeJson.isNullOrBlank() || snapshotBeforeJson == "null") {
            Log.w(tag, "withdraw: opId=${opId.take(8)} snapshotBeforeJson 缺失")
            return@withContext HistoryOpResult.Failure("快照损坏,无法撤销")
        }
        val inversePayloadJson = op.inversePayloadJson
        if (inversePayloadJson.isNullOrBlank()) {
            Log.w(tag, "withdraw: opId=${opId.take(8)} inversePayloadJson 缺失")
            return@withContext HistoryOpResult.Failure("无可执行反向操作")
        }

        // 1. 同时标两个 DAO 为 WITHDRAWN
        historyDao.markWithdrawn(opId)
        pendingDao.markWithdrawn(opId)

        // 2. 用 snapshotBeforeJson 回滚本地 cache(ContactSnapshotter.fromJson 失败兜底 notFound)
        val restored = contactSnapshotter.fromJson(snapshotBeforeJson, op.contactId)
        try {
            rollbackCache(op.contactId, restored.contact, restored.platforms, restored.fieldValues, restored.tags)
        } catch (e: Exception) {
            // [修复防御]:回滚失败不能阻塞"撤销"语义,标记 history WITHDRAWN 已经成功,
            // 让用户至少看到 UI 状态正确。日志留 trace 给开发期排查。
            Log.e(tag, "withdraw: rollbackCache 失败,opId=${opId.take(8)} contactId=${op.contactId}", e)
        }

        // 3. 入反向 op 到 PendingUpload(限定 opType)
        val baseOpType = op.opType
        val undoOpType = "${baseOpType}${OperationTypes.UNDO_SUFFIX}"
        val undoSupports = baseOpType in SUPPORTS_UNDO_OP_TYPES
        if (undoSupports) {
            try {
                val inverseOpId = UUID.randomUUID().toString()
                val currentContact = contactCacheDao.getContactById(op.contactId)
                // 撤销创建 = 删除(没有 resourceVersion 概念,DELETE_CONTACT 不需 If-Match)
                // 其他撤销 PATCH 用 op.resourceVersion 作为 If-Match(服务端当前版本)
                val resourceVersion = if (baseOpType == OperationTypes.CREATE_CONTACT) {
                    0L
                } else {
                    currentContact?.serverVersion ?: op.serverVersion ?: 0L
                }
                pendingDao.enqueue(
                    PendingUploadEntity(
                        opId = inverseOpId,
                        contactId = op.contactId,
                        opType = undoOpType,
                        resourceVersion = resourceVersion,
                        payloadJson = inversePayloadJson,
                        createdAt = now,
                        status = "PENDING",
                        deviceId = deviceIdProvider.deviceId(),
                    )
                )
                historyDao.insert(
                    OperationHistoryEntity(
                        opId = inverseOpId,
                        contactId = op.contactId,
                        opType = undoOpType,
                        opLabel = OperationTypes.labelOf(undoOpType),
                        payloadJson = inversePayloadJson,
                        snapshotBeforeJson = op.snapshotAfterJson ?: "null",
                        snapshotAfterJson = op.snapshotBeforeJson,
                        createdAt = now,
                        opStatus = "PENDING",
                        canUndo = true,
                        canReplay = false,
                    )
                )
                scheduler.kick()
                Log.d(tag, "withdraw: opId=${opId.take(8)} 入反向 op=${inverseOpId.take(8)} type=$undoOpType")
            } catch (e: Exception) {
                Log.e(tag, "withdraw: 入反向 op 失败,opId=${opId.take(8)}", e)
            }
        } else {
            Log.w(tag, "withdraw: opId=${opId.take(8)} baseOpType=$baseOpType 不在 P8 支持列表,只本地回滚")
        }

        HistoryOpResult.Success
    }

    /**
     * [V2-P8] 解决 CONFLICT — 采用本地:
     * 走 `ServerApi.patchContact(serverId, 顶层字段 PATCH, If-Match=serverVersion)`。
     * 服务端接受后,本地 cache 同步更新 serverVersion。
     *
     * [修复防御]:
     * - 只 PATCH 顶层字段(name/bio/note/avatarUrl/pinyinInitial),关联子表不动。
     *   平台 PATCH 等 P9+ 扩展(避免爆炸半径)。
     * - 服务端 409 → 标 CONFLICT + 写新 serverVersion(用户可再次"采用本地/服务端")
     * - 服务端 5xx → markFailed,UI 历史页"立即重试"按钮可触发(P9 阶段)
     */
    override suspend fun adoptLocal(opId: String): HistoryOpResult = withContext(Dispatchers.IO) {
        Log.d(tag, "adoptLocal: opId=${opId.take(8)}")
        val op = historyDao.getById(opId)
        if (op == null) {
            Log.w(tag, "adoptLocal: opId=${opId.take(8)} not in history,F(returned Failure)")
            return@withContext HistoryOpResult.Failure("操作不存在")
        }
        if (op.opStatus != "CONFLICT") {
            Log.w(tag, "adoptLocal: opId=${opId.take(8)} status=${op.opStatus} not CONFLICT")
            return@withContext HistoryOpResult.Failure("仅 CONFLICT 状态可解决")
        }
        val serverVersion = op.serverVersion
        if (serverVersion == null) {
            Log.w(tag, "adoptLocal: opId=${opId.take(8)} no serverVersion in record")
            return@withContext HistoryOpResult.Failure("记录缺服务端版本")
        }
        val contact = contactCacheDao.getContactById(op.contactId)
        if (contact == null) {
            Log.w(tag, "adoptLocal: opId=${opId.take(8)} contactId=${op.contactId} 已不存在")
            return@withContext HistoryOpResult.Failure("联系人已不存在")
        }
        val serverId = contact.serverId
        if (serverId.isNullOrBlank()) {
            Log.w(tag, "adoptLocal: opId=${opId.take(8)} contactId=${op.contactId} 无 serverId(isLocalOnly)")
            return@withContext HistoryOpResult.Failure("本地未同步,无法采用本地版本")
        }

        // 构造 PATCH payload — 只顶层字段,不碰关联子表
        val payload = JsonObject().apply {
            addProperty("name", contact.name)
            contact.bio?.let { addProperty("bio", it) }
            contact.note?.let { addProperty("note", it) }
            contact.avatarUrl?.let { addProperty("avatar_url", it) }
            if (contact.pinyinInitial.isNotBlank()) {
                addProperty("pinyin_initial", contact.pinyinInitial)
            }
        }

        val now = System.currentTimeMillis()
        return@withContext try {
            val resp = serverApi.patchContact(serverId, payload, ifMatch = serverVersion)
            // 200 OK:更新本地 cache serverVersion + 标 DONE
            contactCacheDao.updateContact(contact.copy(serverVersion = resp.version))
            contactCacheDao.bumpContact(op.contactId)
            historyDao.markDone(opId, serverVersion = resp.version, snapshotAfterJson = resp.contact.toString())
            pendingDao.markDone(opId)
            Log.d(tag, "adoptLocal: opId=${opId.take(8)} serverVersion=${resp.version} → DONE")
            HistoryOpResult.Success
        } catch (e: ConflictException) {
            // 服务端又有新版本 → 标 CONFLICT + 提示用户
            val newVersion = e.conflict.serverVersion
            historyDao.markConflict(opId, newVersion, "409 Conflict during adoptLocal: serverVersion=$newVersion")
            pendingDao.markConflict(opId, "409 Conflict during adoptLocal")
            Log.w(tag, "adoptLocal: opId=${opId.take(8)} 服务端又有新版本 serverVersion=$newVersion")
            HistoryOpResult.Failure("服务端又有新版本,请重试")
        } catch (e: ApiException) {
            if (e.status in 500..599) {
                // 5xx → markFailed,等下次重试或用户主动"立即重试"
                historyDao.markFailed(opId, attempts = 1, lastError = "5xx ${e.status}: ${e.message ?: ""}")
                pendingDao.markFailed(
                    opId,
                    attempts = 1,
                    lastError = "5xx ${e.status}: ${e.message ?: ""}",
                    now = now,
                    nextAttemptAt = now + 30_000L,
                )
                Log.w(tag, "adoptLocal: opId=${opId.take(8)} 5xx ${e.status},markFailed")
                HistoryOpResult.Failure("服务端错误,可重试")
            } else {
                // 其他 4xx(非 409)→ 标 FAILED_PERMANENT,用户需手动解决
                historyDao.markFailed(opId, attempts = 1, lastError = "HTTP ${e.status}: ${e.message ?: ""}")
                pendingDao.markFailedPermanent(opId, "HTTP ${e.status}: ${e.message ?: ""}", now)
                Log.w(tag, "adoptLocal: opId=${opId.take(8)} HTTP ${e.status} 永久失败")
                HistoryOpResult.Failure("服务端拒绝:HTTP ${e.status}")
            }
        } catch (e: Exception) {
            Log.e(tag, "adoptLocal: opId=${opId.take(8)} 异常", e)
            HistoryOpResult.Failure("网络异常:${e.javaClass.simpleName}")
        }
    }

    /**
     * [V2-P8] 解决 CONFLICT — 采用服务端:
     * 走 `ContactSnapshotter.fromServerContact(serverContactJson, contactId)` 解析服务端响应,
     * 整体覆盖本地 cache 4 表。
     *
     * 与 [withdraw] 的 rollbackCache 区别:rollbackCache 用 snapshotBeforeJson(Client 内部格式),
     * adoptServer 用 serverContactJson(服务端直接 dump 的 JsonObject,无 envelope)。
     *
     * [修复防御]:
     * - serverContactJson 缺失 / 损坏 → 走 notFound fallback,不抛异常。
     * - fromServerContact 解析失败 → 返 Failure("服务端响应格式错误")。
     */
    override suspend fun adoptServer(opId: String, serverContactJson: String): HistoryOpResult = withContext(Dispatchers.IO) {
        Log.d(tag, "adoptServer: opId=${opId.take(8)} serverContactBytes=${serverContactJson.length}")
        val op = historyDao.getById(opId)
        if (op == null) {
            Log.w(tag, "adoptServer: opId=${opId.take(8)} not in history,F(returned Failure)")
            return@withContext HistoryOpResult.Failure("操作不存在")
        }
        if (op.opStatus != "CONFLICT") {
            Log.w(tag, "adoptServer: opId=${opId.take(8)} status=${op.opStatus} not CONFLICT")
            return@withContext HistoryOpResult.Failure("仅 CONFLICT 状态可解决")
        }
        if (serverContactJson.isBlank() || serverContactJson == "null") {
            Log.w(tag, "adoptServer: opId=${opId.take(8)} serverContactJson 缺失")
            return@withContext HistoryOpResult.Failure("服务端响应缺失")
        }

        // [修复防御]:serverContactJson 在 P7 阶段是"测试 mock 字符串"(如 {"name":"server-side"}),
        // 我们让 fromServerContact 兼容两种格式(JsonObject 直接 dump 或 envelope);
        // 解析失败兜底 notFound。
        val restored = try {
            contactSnapshotter.fromServerContact(serverContactJson, op.contactId)
        } catch (e: Exception) {
            Log.e(tag, "adoptServer: opId=${opId.take(8)} fromServerContact 失败", e)
            return@withContext HistoryOpResult.Failure("服务端响应格式错误")
        }

        // 把服务端权威版本套到 cache(contactId 可能从 serverContactJson 的 id 字段取到)
        val serverContactId = restored.contact.id
        if (serverContactId != op.contactId && serverContactId > 0) {
            Log.w(tag, "adoptServer: opId=${opId.take(8)} serverContactId=$serverContactId != localId=${op.contactId},继续按 localId 覆盖")
        }

        try {
            // contact.copy(serverVersion = op.serverVersion ?: restored.contact.serverVersion) 保证
            // 写回服务端当前 serverVersion,让下次 PATCH 的 If-Match 对得上
            val mergedContact = restored.contact.copy(
                id = op.contactId,
                serverVersion = op.serverVersion ?: restored.contact.serverVersion,
                avatarPath = contactCacheDao.getContactById(op.contactId)?.avatarPath,
                isLocalOnly = false,
            )
            rollbackCache(
                contactId = op.contactId,
                newContact = mergedContact,
                platforms = restored.platforms,
                fieldValues = restored.fieldValues,
                tags = restored.tags,
            )
        } catch (e: Exception) {
            Log.e(tag, "adoptServer: 写 cache 失败,opId=${opId.take(8)}", e)
            return@withContext HistoryOpResult.Failure("写入本地失败")
        }

        historyDao.markDone(opId, serverVersion = op.serverVersion, snapshotAfterJson = serverContactJson)
        pendingDao.markDone(opId)
        Log.d(tag, "adoptServer: opId=${opId.take(8)} → DONE snapshotAfterBytes=${serverContactJson.length}")
        HistoryOpResult.Success
    }

    /**
     * [V2-P10] 批量重试:遍历 [opIds] 列表,逐条 `pendingDao.retryNow`,最后 kick 1 次。
     *
     * 流程:
     * 1. 过滤 opIds:仅保留 status == "FAILED" 的(其他状态的 op retryNow 也是 no-op
     *    + 找不到 opId 时 retryNow 抛异常,被 catch 跳过即可)。提前过滤避免冗余 DB 写。
     * 2. 逐条 `pendingDao.retryNow(opId, now)` + catch + log continue。
     * 3. 全部 retryNow 完才 `scheduler.kick()` 1 次(避免高频 kick 触发 Worker 抢跑)。
     *
     * [修复防御]: 单条 retryNow 抛异常被 catch + warn 继续下一条,不阻断整体。
     * 入参空 → Success(0, 0) + 不 kick。
     */
    override suspend fun batchRetry(opIds: List<String>): BatchHistoryOpResult = withContext(Dispatchers.IO) {
        Log.d(tag, "batchRetry: 入参 opIds=${opIds.size}")
        if (opIds.isEmpty()) {
            return@withContext BatchHistoryOpResult.Success(succeeded = 0, failed = 0)
        }
        val now = System.currentTimeMillis()
        var succeeded = 0
        var failed = 0
        for (opId in opIds) {
            try {
                val op = pendingDao.getById(opId)
                if (op == null) {
                    Log.w(tag, "batchRetry: opId=${opId.take(8)} not in pending_uploads,跳过")
                    failed++
                    continue
                }
                if (op.status != "FAILED") {
                    // [修复防御]: 仅 FAILED 才允许批量重试,其他状态 retryNow 是 no-op
                    // (retryNow SQL 没有 WHERE status 过滤),会改变状态计数,故直接跳过。
                    Log.d(tag, "batchRetry: opId=${opId.take(8)} status=${op.status} 非 FAILED,跳过")
                    failed++
                    continue
                }
                pendingDao.retryNow(opId, now)
                succeeded++
                Log.d(tag, "batchRetry: opId=${opId.take(8)} → PENDING")
            } catch (e: Exception) {
                Log.w(tag, "batchRetry: opId=${opId.take(8)} retryNow 异常,继续", e)
                failed++
            }
        }
        if (succeeded > 0) {
            scheduler.kick()
            Log.d(tag, "batchRetry: 完成 succeeded=$succeeded failed=$failed,kick 1 次")
        } else {
            Log.d(tag, "batchRetry: 完成 succeeded=0 failed=$failed,跳过 kick")
        }
        BatchHistoryOpResult.Success(succeeded = succeeded, failed = failed)
    }

    /**
     * [V2-P10] 批量撤销:遍历 [opIds] 列表,**逐条复用现有 [withdraw]**(完整的 3 步:
     * markWithdrawn + rollbackCache + 入反向 op + 内部 kick)。
     *
     * 为什么不重写:
     * - withdraw 内部已经有 catch + log + kick,复制逻辑容易遗漏 rollbackCache 失败兜底
     *   / inversePayload 写入分支。
     * - 单条失败 catch 继续,整体返回 succeeded / failed 计数。
     *
     * [修复防御]: 入参空 → Success(0, 0) 不调任何单条方法。外层不再额外 kick —
     * 每条 withdraw 内部己 kick 一次,避免 burst。
     */
    override suspend fun batchWithdraw(opIds: List<String>): BatchHistoryOpResult = withContext(Dispatchers.IO) {
        Log.d(tag, "batchWithdraw: 入参 opIds=${opIds.size}")
        if (opIds.isEmpty()) {
            return@withContext BatchHistoryOpResult.Success(succeeded = 0, failed = 0)
        }
        var succeeded = 0
        var failed = 0
        for (opId in opIds) {
            try {
                val op = historyDao.getById(opId)
                if (op == null) {
                    Log.w(tag, "batchWithdraw: opId=${opId.take(8)} not in history,跳过")
                    failed++
                    continue
                }
                if (op.opStatus == "WITHDRAWN") {
                    Log.d(tag, "batchWithdraw: opId=${opId.take(8)} 已 WITHDRAWN,跳过")
                    failed++
                    continue
                }
                if (!op.canUndo) {
                    Log.d(tag, "batchWithdraw: opId=${opId.take(8)} canUndo=false,跳过")
                    failed++
                    continue
                }
                if (op.opStatus == "CONFLICT") {
                    // CONFLICT 必须单条"采用本地/服务端",不参与批量撤销
                    Log.d(tag, "batchWithdraw: opId=${opId.take(8)} CONFLICT,跳过")
                    failed++
                    continue
                }
                val result = withdraw(opId)
                if (result is HistoryOpResult.Success) {
                    succeeded++
                } else {
                    failed++
                }
            } catch (e: Exception) {
                Log.w(tag, "batchWithdraw: opId=${opId.take(8)} 异常,继续", e)
                failed++
            }
        }
        Log.d(tag, "batchWithdraw: 完成 succeeded=$succeeded failed=$failed(每条 withdraw 内部 kick)")
        BatchHistoryOpResult.Success(succeeded = succeeded, failed = failed)
    }

    /**
     * 整体替换 cache 4 表(非事务,与 P6 hardDeleteContact 同模式)。
     *
     * [修复防御]:
     * - 即使过程中崩溃,Worker 下次启动按服务端 Source of Truth 纠正。
     * - platforms 用 `insertPlatforms(List)` + `deleteByContact` 一次性 REPLACE。
     * - fieldValues 同模式(`insertOrUpdateFieldValues` + `deleteByContact`)。
     * - tags 用 `clearByContact` 清空后按 crossRef 重新 insert(insertCrossRefs)。
     * - 最后 `bumpContact` 触发 InvalidationTracker,UI Flow 刷新。
     */
    private suspend fun rollbackCache(
        contactId: Long,
        newContact: ContactCacheEntity,
        platforms: List<ContactPlatformCacheEntity>,
        fieldValues: List<ContactFieldValueCacheEntity>,
        tags: List<top.mcxiafeng.badger.data.snapshot.SnapshotTagRef>,
    ) {
        // 1. 主表
        contactCacheDao.updateContact(newContact)
        // 2. platforms 全替换
        contactPlatformCacheDao.deleteByContact(contactId)
        if (platforms.isNotEmpty()) {
            contactPlatformCacheDao.insertPlatforms(platforms)
        }
        // 3. fieldValues 全替换
        contactFieldValueCacheDao.deleteByContact(contactId)
        if (fieldValues.isNotEmpty()) {
            contactFieldValueCacheDao.insertOrUpdateFieldValues(fieldValues)
        }
        // 4. tags 全替换(按 crossRef 重建,SnapshotTagRef → ContactTagCacheEntity)
        contactTagCacheDao.clearByContact(contactId)
        if (tags.isNotEmpty()) {
            val now = System.currentTimeMillis()
            contactTagCacheDao.insertCrossRefs(
                tags.map { ref ->
                    ContactTagCacheEntity(
                        contactId = contactId,
                        tagId = ref.tagId,
                        source = ref.source,
                        confidence = ref.confidence,
                        createTime = now,
                    )
                }
            )
        }
        // 5. invalidation
        contactCacheDao.bumpContact(contactId)
        Log.d(
            tag,
            "rollbackCache: contactId=$contactId platforms=${platforms.size} " +
                "fieldValues=${fieldValues.size} tags=${tags.size}",
        )
    }

    /**
     * 把 contactId 映射成展示用 name — public 暴露但不在 [OperationHistoryRepository] 接口里。
     * 留作 P8 阶段扩展用(撤销时需要取联系人名作 UI 反馈)。
     */
    suspend fun resolveContactName(contactId: Long): String? {
        return contactCacheDao.getContactById(contactId)?.name
    }

    @Suppress("unused")
    private fun debugJsonParse(json: String): JsonObject? = try {
        JsonParser.parseString(json).asJsonObject
    } catch (e: Exception) {
        Log.w(tag, "debugJsonParse: 解析失败", e)
        null
    }

    companion object {
        /**
         * P8 MVP 支持双边同步撤销的 opType 集合:
         * - UPDATE_NAME / UPDATE_BIO / UPDATE_NOTE:inversePayloadJson 直接当 PATCH payload 发
         * - CREATE_CONTACT:inversePayloadJson = DELETE_CONTACT 指令,Executor 走 handleDelete
         *
         * 其他 opType(ADD/UPDATE/REMOVE_PLATFORM、UPDATE_FIELD_VALUE、ADD/REMOVE_TAG、
         * STAR、UNSTAR、DELETE/MERGE_CONTACT)只本地回滚,服务端反向同步留 P9+ 扩展。
         */
        private val SUPPORTS_UNDO_OP_TYPES = setOf(
            OperationTypes.UPDATE_NAME,
            OperationTypes.UPDATE_BIO,
            OperationTypes.UPDATE_NOTE,
            OperationTypes.CREATE_CONTACT,
        )
    }
}

private const val TAG = "OpHistoryRepo"