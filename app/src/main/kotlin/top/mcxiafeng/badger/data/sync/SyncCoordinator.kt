package top.mcxiafeng.badger.data.sync

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.room.withTransaction
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.account.AccountContext
import top.mcxiafeng.badger.data.account.AccountDatabaseProvider
import top.mcxiafeng.badger.data.account.AccountScope
import top.mcxiafeng.badger.data.account.AccountStore
import top.mcxiafeng.badger.network.auth.AuthSession
import top.mcxiafeng.badger.network.auth.AuthSessionStore
import top.mcxiafeng.badger.network.sync.SnapshotReader
import top.mcxiafeng.badger.network.sync.SyncHttpException
import top.mcxiafeng.badger.network.sync.SyncJson
import top.mcxiafeng.badger.network.sync.SyncRoot
import top.mcxiafeng.badger.network.sync.SyncTransport
import java.math.BigInteger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
data class SyncProgress(val running: Boolean = false, val message: String = "尚未同步", val conflicts: Int = 0,
    val pending: Boolean = false, val canContinue: Boolean = false, val tagNameConflict: Boolean = false)

/** Foreground/offline synchronization. All retryable intent is in the account database, not this singleton. */
@Singleton
class SyncCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accounts: AccountStore,
    private val databases: AccountDatabaseProvider,
    private val sessions: AuthSessionStore,
    private val transport: SyncTransport,
) {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val _progress = MutableStateFlow<Map<String, SyncProgress>>(emptyMap())
    val progress: StateFlow<Map<String, SyncProgress>> = _progress.asStateFlow()

    suspend fun synchronize(scope: AccountScope = accounts.current()) = withContext(Dispatchers.IO) {
        if (scope.serverId == null) return@withContext
        locked(scope) {
            val previous = _progress.value[scope.key] ?: SyncProgress()
            publish(scope, previous.copy(running = true, message = "正在同步"))
            try {
                val session = session(scope)
                val capabilities = transport.json(session, "/api/v1/sync/capabilities")
                require(SyncJson.long(capabilities, "maxSchemaVersion") >= SyncJson.SCHEMA &&
                    SyncJson.long(capabilities, "minSchemaVersion") <= SyncJson.SCHEMA) { "服务器需升级后才能同步完整资料" }
                require(SyncJson.long(capabilities, "requiredSchemaVersion") <= SyncJson.SCHEMA) { "此账号需要更新客户端后才能同步" }
                val db = databases.open(scope)
                val scoped = AccountContext(context, scope)
                val assets = WorkspaceAssets(scoped)
                assets.upload(db, transport, session)
                recover(db, scoped, session)
                pull(db, scoped, assets, session)
                uploadChanges(db, scoped, assets, session)
                pull(db, scoped, assets, session)
                val status = db.withTransaction {
                    val ledger = SyncLedger.load(db)
                    val count = ledger.entries("conflict/").size
                    val pending = ledger.get(PENDING) != null || different(WorkspaceCodec(ledger, scoped).capture(), ledger).isNotEmpty()
                    SyncProgress(message = when { count > 0 -> "$count 项冲突待处理"; pending -> "部分资料待同步"; else -> "已同步" },
                        conflicts = count, pending = pending, canContinue = pending && count == 0)
                }
                publish(scope, status)
            } catch (e: CancellationException) {
                publish(scope, previous.copy(message = "同步已暂停"))
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "用户空间同步失败，保留待确认状态；type=${e.javaClass.simpleName}")
                publish(scope, SyncProgress(message = failureMessage(e), pending = true,
                    conflicts = conflictCount(scope), tagNameConflict = e is TagNameConflictException ||
                        (e is SyncHttpException && "NAME_UNAVAILABLE" in e.fieldCodes)))
            }
        }
    }

    private suspend fun pull(db: AppDatabase, scoped: Context, assets: WorkspaceAssets, session: AuthSession) {
        val minimum = db.withTransaction { SyncLedger.load(db).get(SyncLedger.HIGH_WATER) ?: "0" }
        val snapshot = SnapshotReader(transport).read(session, minimum)
        workspaceTransaction(db) {
            val ledger = SyncLedger.load(db)
            assets.remember(snapshot.roots, ledger)
            SyncReconciler(ledger, scoped).accept(snapshot.roots, snapshot.revision)
        }
    }

    private suspend fun uploadChanges(db: AppDatabase, scoped: Context, assets: WorkspaceAssets, session: AuthSession) {
        repeat(MAX_COMMANDS_PER_PASS) {
            val staged = db.withTransaction { stage(db, scoped, session) } ?: return
            assets.upload(db, transport, session)
            if (!send(db, staged, session)) {
                pull(db, scoped, assets, session)
                return
            }
        }
        Log.d(TAG, "本轮同步达到命令预算，剩余资料保持待同步")
    }

    private suspend fun stage(db: AppDatabase, scoped: Context, session: AuthSession): String? {
        val ledger = SyncLedger.load(db)
        ledger.get(PENDING)?.let { return it }
        val roots = WorkspaceCodec(ledger, scoped).capture()
        val next = different(roots, ledger).sortedBy { order(it) }.firstOrNull() ?: return null
        val base = ledger.shadow(next.key)
        require(next.kind != "selfCard" || base != null) { "我的名片尚未取得云端基线" }
        require(SyncJson.encode(SyncJson.element(next.value)).toByteArray(Charsets.UTF_8).size <= SyncJson.MAX_VALUE_BYTES) {
            "此项资料超过云端容量限制，请拆分名片夹或缩短超长内容后重试；本机原文已保留"
        }
        val request = SyncJson.obj("mutationId" to UUID.randomUUID().toString(), "dataEpoch" to session.dataEpoch,
            "kind" to next.kind, "id" to next.id, "action" to if (next.deleted) "delete" else if (base == null) "create" else "replace",
            "expectedVersion" to (base?.version ?: "0"), "value" to next.value)
        val encoded = SyncJson.encode(SyncJson.obj("root" to next.json(), "request" to request))
        ledger.put(PENDING, encoded)
        Log.d(TAG, "已持久化待确认同步意图 kind=${next.kind}")
        return encoded
    }

    private fun different(local: Map<String, SyncRoot>, ledger: SyncLedger): List<SyncRoot> {
        val result = mutableListOf<SyncRoot>()
        val held = ledger.entries("held/").keys.map { it.removePrefix("held/").substringAfter('/') }.toSet()
        for (root in local.values) {
            if (ledger.get("conflict/${root.key}") != null || root.key in held) continue
            val base = ledger.shadow(root.key)
            if (base == null || (!base.deleted && root.value != base.value)) result.add(root)
        }
        val hasConflicts = ledger.entries("conflict/").isNotEmpty()
        for ((_, encoded) in ledger.entries("shadow/")) {
            val base = SyncRoot.read(SyncJson.parse(encoded))
            if (base.deleted || local.containsKey(base.key) || ledger.get("conflict/${base.key}") != null) continue
            if (base.kind == "fieldDefinition" && hasConflicts) continue
            result.add(base.copy(deleted = true, value = null))
        }
        return result
    }

    private suspend fun recover(db: AppDatabase, scoped: Context, session: AuthSession) {
        val encoded = db.withTransaction { SyncLedger.load(db).get(PENDING) } ?: return
        val pending = SyncJson.parse(encoded)
        if (pending.flag("rejected")) {
            val root = SyncRoot.read(pending["root"].asJsonObject)
            val changed = db.withTransaction {
                val ledger = SyncLedger.load(db)
                val current = WorkspaceCodec(ledger, scoped).capture()[root.key]
                if (current?.value != root.value) { ledger.remove(PENDING); true } else false
            }
            check(changed) { pending.str("error") ?: "有资料未通过云端校验，请修改后重试" }
            return
        }
        send(db, encoded, session)
    }

    private suspend fun send(db: AppDatabase, encoded: String, session: AuthSession): Boolean {
        val pending = SyncJson.parse(encoded)
        val root = SyncRoot.read(pending["root"].asJsonObject)
        val request = pending["request"].asJsonObject
        val ack = try {
            transport.json(session, "/api/v1/sync/mutations", request)
        } catch (e: SyncHttpException) {
            if (e.status == HTTP_CONFLICT && e.code == "VERSION_CONFLICT") {
                db.withTransaction { SyncLedger.load(db).remove(PENDING) }
                Log.d(TAG, "条件写冲突，保留本地意图并拉取最新基线")
                return false
            }
            if (e.status == HTTP_VALIDATION_FAILED && e.fieldCodes.any { it in REFERENCE_ERROR_CODES }) {
                db.withTransaction {
                    val ledger = SyncLedger.load(db)
                    ledger.put("constraint/${root.key}", SyncJson.encode(root.json()))
                    ledger.remove(PENDING)
                }
                Log.d(TAG, "关联约束已变化，转入可恢复冲突 kind=${root.kind}")
                return false
            }
            if (e.status in VALIDATION_ERROR_STATUSES) db.withTransaction {
                pending.addProperty("rejected", true)
                pending.addProperty("error", e.message)
                SyncLedger.load(db).put(PENDING, SyncJson.encode(pending))
            }
            throw e
        }
        val confirmed = confirmAck(root, request, ack, session.dataEpoch)
        db.withTransaction {
            val ledger = SyncLedger.load(db)
            require(ledger.get(PENDING) == encoded) { "待确认命令已变化" }
            ledger.shadow(confirmed.root, verified = false)
            ledger.put("unverified/${root.key}", confirmed.root.version)
            ledger.highWater(confirmed.revision)
            ledger.remove(PENDING)
        }
        Log.d(TAG, "同步意图已确认 kind=${root.kind}")
        return true
    }

    private data class ConfirmedAck(val root: SyncRoot, val revision: String)

    private fun confirmAck(root: SyncRoot, request: JsonObject, ack: JsonObject, epoch: String): ConfirmedAck {
        require(ack.str("mutationId") == request.str("mutationId") && ack.str("dataEpoch") == epoch)
        val revision = requireNotNull(ack.str("revision")).also { SyncJson.counter(it) }
        val changes = SyncJson.array(ack, "changes").map { it.asJsonObject }
        val seen = mutableSetOf<String>()
        for (change in changes) {
            require(seen.add("${change.str("kind")}/${SyncJson.id(requireNotNull(change.str("id")))}"))
            require(SyncJson.counter(requireNotNull(change.str("version"))).signum() > 0)
            require(change["deleted"]?.isJsonPrimitive == true && change["deleted"].asJsonPrimitive.isBoolean)
        }
        val changed = changes.firstOrNull { it.str("kind") == root.kind && it.str("id") == root.id }
        val version = changed?.str("version") ?: requireNotNull(request.str("expectedVersion"))
        val expected = SyncJson.counter(requireNotNull(request.str("expectedVersion")))
        val confirmed = SyncJson.counter(version)
        val expectedConfirmation = if (changed == null) expected else expected + BigInteger.ONE
        require(confirmed.signum() > 0 && confirmed == expectedConfirmation) { "云端确认版本不符合条件写契约，原请求仍待确认" }
        require(changed == null || changed.flag("deleted") == root.deleted) { "云端确认状态不一致" }
        return ConfirmedAck(root.copy(version = version), revision)
    }

    internal suspend fun <T> locked(scope: AccountScope, block: suspend () -> T): T =
        locks.getOrPut(scope.key) { Mutex() }.withLock {
            check(accounts.current().key == scope.key) { "账号已切换，请返回后重试" }
            block()
        }

    internal suspend fun requireReadyForImport(scope: AccountScope) {
        val db = databases.open(scope)
        db.withTransaction {
            val ledger = SyncLedger.load(db)
            check(ledger.get(SyncReconciler.INITIALIZED) == "true" && ledger.get(PENDING) == null &&
                ledger.entries("conflict/").isEmpty() && different(WorkspaceCodec(ledger, scopedContext(scope)).capture(), ledger).isEmpty()) {
                "请先完成同步并处理现有冲突，再导入本地资料"
            }
        }
    }

    internal fun database(scope: AccountScope): AppDatabase = databases.open(scope)
    internal fun scopedContext(scope: AccountScope): Context = AccountContext(context, scope)

    private fun session(scope: AccountScope): AuthSession {
        val session = requireNotNull(sessions.read()) { "请重新登录后同步，资料仍保存在本机" }
        require(session.serverId == scope.serverId && session.userUuid == scope.userId && session.dataEpoch == scope.dataEpoch &&
            accounts.current().key == scope.key && session.baseUrl == accounts.current().baseUrl) { "当前会话与用户空间不匹配" }
        return session
    }

    private suspend fun conflictCount(scope: AccountScope): Int = databases.open(scope).withTransaction {
        SyncLedger.load(databases.open(scope)).entries("conflict/").size
    }

    private fun publish(scope: AccountScope, progress: SyncProgress) {
        _progress.update { it + (scope.key to progress) }
    }

    private fun failureMessage(error: Exception): String {
        if (error is SyncHttpException || error is TagNameConflictException) return error.message ?: "云端同步失败，请重试"
        if (error is IllegalArgumentException || error is IllegalStateException) {
            val message = error.message
            if (message != null && message.any { it.code in HAN_START..HAN_END }) return message
            return "同步资料校验失败，尚未确认的内容不会被标记为已同步"
        }
        return "同步未完成，请检查连接与本机存储空间后重试"
    }

    private fun order(root: SyncRoot): Int = if (root.deleted) DELETE_ORDER.indexOf(root.kind) + DELETE_OFFSET
        else LIVE_ORDER.indexOf(root.kind)

    companion object {
        private const val TAG = "SyncCoordinatorTester"
        private const val PENDING = "pending/current"
        private const val MAX_COMMANDS_PER_PASS = 128
        private const val DELETE_OFFSET = 10
        private const val HAN_START = 0x4E00
        private const val HAN_END = 0x9FFF
        private const val HTTP_CONFLICT = 409
        private const val HTTP_VALIDATION_FAILED = 422
        private val LIVE_ORDER = listOf("fieldDefinition", "person", "selfCard", "collection", "tag")
        private val DELETE_ORDER = listOf("tag", "collection", "person", "fieldDefinition")
        private val REFERENCE_ERROR_CODES = setOf("REFERENCE_NOT_FOUND", "REFERENCE_IN_USE")
        private val VALIDATION_ERROR_STATUSES = setOf(400, 413, 415, 422)
    }
}
