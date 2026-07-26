package top.mcxiafeng.badger.sync

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.queue.OperationHistoryDao
import top.mcxiafeng.badger.data.queue.OperationTypes
import top.mcxiafeng.badger.data.queue.PendingUploadDao
import top.mcxiafeng.badger.data.queue.PendingUploadEntity
import top.mcxiafeng.badger.network.ApiException
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.network.ServerApi.ConflictException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [V2-P4] PendingUpload op 实际执行器。
 *
 * 单一职责:对一条 [PendingUploadEntity] 走"标记 IN_FLIGHT → 调 ServerApi → 落状态" 的全流程,
 * 把网络细节、HTTP code 解释、退避时间计算收敛到一处。Worker 只负责"拉一批 → 循环 next()",
 * 真正与 ServerApi 打交道的工作都在这里。
 *
 * 状态机语义(对齐 [PendingUploadDao] 注释 + `docs/BADGER_V2_CLIENT_PLAN.md` §4):
 * ```
 *  (none) → PENDING ──markInFlight──► IN_FLIGHT ──┬─► DONE              (HTTP 2xx)
 *                                                  ├─► CONFLICT          (HTTP 409)
 *                                                  ├─► FAILED            (HTTP 5xx/IO, attempts<max)
 *                                                  ├─► FAILED_PERMANENT  (attempts>=max)
 *                                                  └─► WITHDRAWN         (用户撤销)
 * ```
 *
 * 设计要点(对应 §4.3 抗丢 + §5.5.2 404 视为成功):
 * 1. **404 视为成功**:DELETE/PATCH 收到 404 走 idempotent 路径,标 DONE。
 * 2. **5xx / IO → FAILED**:attempts++ + 退避由 [nextAttempt] 计算(2s → 4s → 8s … 5min 封顶)。
 * 3. **409 → CONFLICT**:`PendingUploadDao.markConflict` 写 lastError;[ConflictException] 携带服务端权威版本,
 *    历史页会读它做"采用本地 / 采用服务端"决策。
 * 4. **2xx 写入 serverVersion**:更新 [ContactCacheEntity.serverVersion],使后续 PATCH 用正确的 If-Match。
 * 5. **可观测**:每条 op 都打 `Log.d("Tester", ...)`,失败路径打 `Log.w` / `Log.e` 含 reason 链。
 */
@Singleton
class PendingUploadExecutor @Inject constructor(
    private val pendingDao: PendingUploadDao,
    private val historyDao: OperationHistoryDao,
    private val deviceIdProvider: DeviceIdProvider,
    private val serverApi: ServerApi,
    // [V2-P8] 撤销双边同步:_UNDO 后缀的 inverse PATCH 需要从 cache 查 server_id
    // (inversePayloadJson 通常不带 server_id,因为撤销时只关心"改回旧值",不知道目标 serverId)
    private val contactCacheDao: ContactCacheDao,
) {

    private val tag = TAG

    /**
     * 处理一条 op 的全流程。
     *
     * 调用前 [op] 应当已经是 PENDING 状态(由 Worker 在拉取后立即 markInFlight)。
     * 本函数**不会**自己调 markInFlight — 把"抢锁"和"消费"分离让 caller 能在并发场景下精确控制。
     *
     * @return 处理结果(用于测试断言 + 调用方日志)
     */
    suspend fun execute(op: PendingUploadEntity, now: Long = System.currentTimeMillis()): ExecResult {
        Log.d(tag, "execute: opId=${op.opId.take(8)} opType=${op.opType} contactId=${op.contactId} attempts=${op.attempts}/${op.maxAttempts}")

        // [修复防御]: 二次防御 markInFlight 失败(caller 没抢到锁或外部状态变更)。
        // 即使外部已经标过,这里再走一遍无副作用 — 但若 op 已被标 DONE / WITHDRAWN,直接退出。
        if (op.status != "PENDING" && op.status != "IN_FLIGHT") {
            Log.w(tag, "execute: opId=${op.opId.take(8)} status=${op.status} 跳过(非活跃态)")
            return ExecResult.Skipped
        }

        return try {
            doExecute(op, now)
        } catch (e: ConflictException) {
            // [修复防御]: 409 — 不重试,标 CONFLICT,UI 弹"采用本地 / 采用服务端"对话框。
            val currentVersion = e.conflict.serverVersion
            val conflictJson = e.conflict.serverContact?.toString() ?: "null"
            Log.w(tag, "execute: opId=${op.opId.take(8)} 409 CONFLICT serverVersion=$currentVersion")
            pendingDao.markConflict(op.opId, "409 Conflict: serverVersion=$currentVersion, contact=$conflictJson")
            historyDao.markConflict(op.opId, currentVersion, "server returned 409, serverVersion=$currentVersion")
            ExecResult.Conflict(currentVersion)
        } catch (e: ApiException) {
            when {
                e.status in 500..599 -> handleTransientFailure(op, e.message ?: "HTTP ${e.status}", now)
                e.status == 404 -> {
                    Log.w(tag, "execute: opId=${op.opId.take(8)} 404 → 视为幂等成功")
                    handleDone(op, serverVersion = null, now)
                    ExecResult.Done(null)
                }
                e.status == 401 -> {
                    Log.w(tag, "execute: opId=${op.opId.take(8)} 401 → 保留 PENDING 等下次重试")
                    pendingDao.markFailed(op.opId, op.attempts, "401 token expired, will retry after re-login", now, nextAttemptAt = now + 30_000L)
                    ExecResult.RetryScheduled
                }
                else -> {
                    Log.w(tag, "execute: opId=${op.opId.take(8)} HTTP ${e.status} → 永久失败: ${e.message}")
                    pendingDao.markFailedPermanent(op.opId, "HTTP ${e.status}: ${e.message ?: ""}", now)
                    historyDao.markFailed(op.opId, op.attempts + 1, "HTTP ${e.status}: ${e.message ?: ""}")
                    ExecResult.PermanentFailure("HTTP ${e.status}")
                }
            }
        } catch (e: ConnectException) {
            handleTransientFailure(op, "ConnectException: ${e.message ?: ""}", now)
        } catch (e: SocketTimeoutException) {
            handleTransientFailure(op, "SocketTimeoutException: ${e.message ?: ""}", now)
        } catch (e: UnknownHostException) {
            handleTransientFailure(op, "UnknownHostException: ${e.message ?: ""}", now)
        } catch (e: IOException) {
            handleTransientFailure(op, "IOException: ${e.javaClass.simpleName}: ${e.message ?: ""}", now)
        } catch (e: Exception) {
            Log.e(tag, "execute: opId=${op.opId.take(8)} 未预期异常", e)
            handleTransientFailure(op, "Exception: ${e.javaClass.simpleName}: ${e.message ?: ""}", now)
        }
    }

    /**
     * 实际 dispatch 到具体 opType handler 的内部函数 — 拆出来让外层 try/catch 收尾各种异常时,
     * 不会因 `when` 表达式混入 ExecResult 之外的值导致 return type 推断失败。
     *
     * 每个 branch 必然返回 [ExecResult] 的子类型,不再调用 markFailed* 等副作用(那些走外层 catch 兜底)。
     */
    private suspend fun doExecute(op: PendingUploadEntity, now: Long): ExecResult {
        val result: ExecResult = when {
            // [V2-P8] _UNDO 后缀:撤销某 op 时的反向 op(由 OperationHistoryRepositoryImpl.withdraw 入队)
            // 去掉 _UNDO 后缀后,根据基础 opType 走对应 handler
            op.opType.endsWith(OperationTypes.UNDO_SUFFIX) -> handleUndo(op, now)
            op.opType == OpType.CREATE_CONTACT -> handleCreate(op, now)
            op.opType == OpType.PATCH_CONTACT -> handlePatch(op, now)
            op.opType == OpType.DELETE_CONTACT -> handleDelete(op, now)
            op.opType == OpType.MERGE_CONTACT -> handleMerge(op, now)
            else -> {
                Log.e(tag, "execute: 未知 opType=${op.opType}, 永久失败")
                pendingDao.markFailedPermanent(op.opId, "unknown opType: ${op.opType}", now)
                historyDao.markFailed(op.opId, op.attempts + 1, "unknown opType: ${op.opType}")
                ExecResult.PermanentFailure("unknown opType: ${op.opType}")
            }
        }
        return result
    }

    /**
     * [V2-P8] 撤销 op 的反向 op 处理(由 OperationHistoryRepositoryImpl.withdraw 入队)。
     *
     * opType 形如 `${原 opType}_UNDO`,去掉 _UNDO 后缀:
     * - UPDATE_NAME / UPDATE_BIO / UPDATE_NOTE → 走 handlePatch(inversePayloadJson 直接当 PATCH payload)
     * - CREATE_CONTACT → 走 handleDelete(撤销创建 = 删除已建联系人)
     * - 其他(ADD/UPDATE/REMOVE_PLATFORM 等)→ P8 MVP 暂不支持,标 FAILED_PERMANENT 走用户重发路径
     */
    private suspend fun handleUndo(op: PendingUploadEntity, now: Long): ExecResult {
        val baseOpType = op.opType.removeSuffix(OperationTypes.UNDO_SUFFIX)
        Log.d(tag, "handleUndo: opId=${op.opId.take(8)} baseOpType=$baseOpType")
        return when (baseOpType) {
            OperationTypes.UPDATE_NAME,
            OperationTypes.UPDATE_BIO,
            OperationTypes.UPDATE_NOTE -> handlePatch(op, now)
            OperationTypes.CREATE_CONTACT -> handleDelete(op, now)
            else -> {
                // P9+ 扩展:ADD/UPDATE/REMOVE_PLATFORM 反向 PATCH 需要服务端接受 platforms 整体 PATCH
                Log.w(tag, "handleUndo: opId=${op.opId.take(8)} baseOpType=$baseOpType not yet supported")
                pendingDao.markFailedPermanent(op.opId, "UNDO for $baseOpType not yet supported, P9+ 扩展", now)
                historyDao.markFailed(op.opId, op.attempts + 1, "UNDO for $baseOpType not yet supported")
                ExecResult.PermanentFailure("UNDO for $baseOpType not yet supported")
            }
        }
    }

    // ============ opType handlers ============

    private suspend fun handleCreate(op: PendingUploadEntity, now: Long): ExecResult {
        val payload = parsePayload(op.payloadJson)
        val resp = serverApi.createContact(payload, ifMatch = null)
        return finalizeDone(op, resp, now)
    }

    private suspend fun handlePatch(op: PendingUploadEntity, now: Long): ExecResult {
        val payload = parsePayload(op.payloadJson)
        // [V2-P8] server_id 兜底:P5 阶段 inversePayloadJson 可能是 `{"name":"old","pinyinInitial":"old"}`
        // 这种顶层 PATCH 字段(不带 server_id)。撤销时先看 payload,再看本地 cache,再看 op 的 resourceVersion
        // 对应的 serverId(无法直接拿 — 走 contactCacheDao)。
        val serverId = payload.get("server_id")?.asString
            ?: payload.get("id")?.asString
            ?: contactCacheDao.getContactById(op.contactId)?.serverId
            ?: throw IllegalStateException("PATCH_CONTACT payload missing server_id and contact $op.contactId has no serverId: ${op.payloadJson.take(120)}")
        val resp = serverApi.patchContact(serverId, payload, ifMatch = op.resourceVersion)
        return finalizeDone(op, resp, now)
    }

    private suspend fun handleDelete(op: PendingUploadEntity, now: Long): ExecResult {
        val payload = parsePayload(op.payloadJson)
        val serverId = payload.get("server_id")?.asString
            ?: payload.get("id")?.asString
            ?: throw IllegalStateException("DELETE_CONTACT payload missing server_id: ${op.payloadJson.take(120)}")
        val ok = serverApi.deleteContact(serverId, ifMatch = op.resourceVersion)
        if (ok) {
            pendingDao.markDone(op.opId)
            historyDao.markDone(op.opId, serverVersion = null, snapshotAfterJson = null)
            Log.d(tag, "handleDelete: opId=${op.opId.take(8)} DONE")
            return ExecResult.Done(null)
        }
        // 不可达分支(服务端实现若返 false 视作失败)
        handleTransientFailure(op, "deleteContact returned false", now)
        return ExecResult.RetryScheduled
    }

    private suspend fun handleMerge(op: PendingUploadEntity, now: Long): ExecResult {
        val payload = parsePayload(op.payloadJson)
        val targetId = payload.get("target_server_id")?.asString
            ?: payload.get("target_id")?.asString
            ?: throw IllegalStateException("MERGE_CONTACT payload missing target_server_id")
        val mergedArr = payload.getAsJsonArray("merged_server_ids") ?: payload.getAsJsonArray("merged_ids")
        val mergedIds = mergedArr?.mapNotNull { it.takeIfString() } ?: emptyList()
        val resp = serverApi.mergeContact(targetId, mergedIds, ifMatch = op.resourceVersion)
        return finalizeDone(op, resp, now)
    }

    private suspend fun finalizeDone(
        op: PendingUploadEntity,
        resp: ServerApi.ContactResponse,
        now: Long,
    ): ExecResult {
        handleDone(op, serverVersion = resp.version, now)
        return ExecResult.Done(resp.version)
    }

    /**
     * 通用 DONE 收尾:写 pendingDao.DONE + historyDao.DONE(serverVersion + snapshotAfter)。
     *
     * [snapshotAfterJson] 是 contact 的服务端响应 JSON,用于 P8 撤销对账(对应
     * `OperationHistoryEntity.snapshotAfterJson`)。P5/P6 阶段 history 写入时已经填了
     * 客户端侧的 after snapshot,这里**不覆盖**,只写 serverVersion 与 lastError=NULL。
     */
    private suspend fun handleDone(op: PendingUploadEntity, serverVersion: Long?, now: Long) {
        pendingDao.markDone(op.opId)
        historyDao.markDone(op.opId, serverVersion = serverVersion, snapshotAfterJson = null)
        Log.d(tag, "handleDone: opId=${op.opId.take(8)} serverVersion=$serverVersion deviceId=${deviceIdProvider.deviceId().take(8)}")
    }

    private suspend fun handleTransientFailure(op: PendingUploadEntity, reason: String, now: Long): ExecResult {
        val newAttempts = op.attempts + 1
        if (newAttempts >= op.maxAttempts) {
            Log.w(tag, "handleTransientFailure: opId=${op.opId.take(8)} attempts=$newAttempts/${op.maxAttempts} → 永久失败")
            pendingDao.markFailedPermanent(op.opId, reason, now)
            historyDao.markFailed(op.opId, newAttempts, reason)
            return ExecResult.PermanentFailure(reason)
        } else {
            val nextAt = nextAttempt(now, newAttempts)
            Log.w(tag, "handleTransientFailure: opId=${op.opId.take(8)} attempts=$newAttempts/${op.maxAttempts} → next at +${(nextAt - now) / 1000}s reason=$reason")
            pendingDao.markFailed(op.opId, newAttempts, reason, now, nextAt)
            historyDao.markFailed(op.opId, newAttempts, reason)
            return ExecResult.RetryScheduled
        }
    }

    // ============ utils ============

    private fun parsePayload(json: String): JsonObject {
        // [修复防御]: payload 损坏不能让 Executor 整个崩 — 返空 JsonObject 让服务端 409 兜底。
        return try {
            JsonParser.parseString(json).asJsonObject
        } catch (e: Exception) {
            Log.e(tag, "parsePayload: 解析失败,使用空对象 — server will 409", e)
            JsonObject()
        }
    }

    companion object {
        private const val TAG = "PendingUploadExec"

        /**
         * 退避时间:2s, 4s, 8s, 16s, 32s, 64s, 128s, 256s, 300s(5min 封顶)。
         * 与 `docs/BADGER_V2_CLIENT_PLAN.md` §4.4 + WorkManager.setBackoffCriteria 协同。
         */
        fun nextAttempt(now: Long, attempts: Int): Long {
            val base = 2_000L
            val shifted = base shl (attempts - 1).coerceIn(0, 8)
            return now + shifted.coerceAtMost(5 * 60_000L)
        }
    }
}

/**
 * Executor 处理结果(纯数据,便于测试断言)。
 */
sealed class ExecResult {
    data class Done(val serverVersion: Long?) : ExecResult()
    data class Conflict(val serverVersion: Long) : ExecResult()
    data object RetryScheduled : ExecResult()
    data object Skipped : ExecResult()
    data class PermanentFailure(val reason: String) : ExecResult()
}

/**
 * opType 常量。与 [PendingUploadEntity.opType] 对应,
 * 把字符串集中放这里便于 §5.5 P5/P6 阶段批量 grep & 改写。
 */
object OpType {
    const val CREATE_CONTACT = "CREATE_CONTACT"
    const val PATCH_CONTACT = "PATCH_CONTACT"
    const val DELETE_CONTACT = "DELETE_CONTACT"
    const val MERGE_CONTACT = "MERGE_CONTACT"
    const val UNDO = "_UNDO"  // P8 阶段 inverse op 使用
}

private fun com.google.gson.JsonElement?.takeIfString(): String? =
    if (this == null || this.isJsonNull) null else runCatching { this.asString }.getOrNull()