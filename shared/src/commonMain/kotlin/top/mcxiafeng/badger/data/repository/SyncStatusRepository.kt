package top.mcxiafeng.badger.data.repository

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import top.mcxiafeng.badger.data.system.database.SystemDatabase
import top.mcxiafeng.badger.data.system.database.SystemDbHolder
import top.mcxiafeng.badger.data.system.entity.SyncAtom
import top.mcxiafeng.badger.data.user.database.CacheDatabase
import top.mcxiafeng.badger.data.user.database.CacheDbHolder
import top.mcxiafeng.badger.sync.EntityKind
import top.mcxiafeng.badger.sync.SyncAtomReplayer
import top.mcxiafeng.badger.sync.SyncPullEngine
import top.mcxiafeng.badger.sync.SyncType
import top.mcxiafeng.badger.utils.BadgerLog

/** 单条 pending atom 的展示摘要（不含 JSON 载荷）。 */
@Immutable
data class PendingAtomSummary(
    val id: Long,
    val kind: EntityKind,
    val syncType: SyncType,
    val attempts: Int,
    val lastError: String?,
    val createdAt: Long,
)

/**
 * 同步状态快照：同步页一屏所需的全部只读数据。
 *
 * [loggedIn]/[cursor]/[lastSyncTime] 来自 UserSyncState 载体：未登录时 loggedIn=false、
 * 后两者为 0；lastSyncTime 只在登录后由增量拉取以服务端下发时间回写。
 */
@Immutable
data class SyncStatusSnapshot(
    val loggedIn: Boolean,
    val cursor: Long,
    val lastSyncTime: Long,
    val pendingTotal: Int,
    val failedCount: Int,
    val lastError: String?,
    val oldestPendingAt: Long?,
    val localPersons: Int,
    val localCollections: Int,
    val localTags: Int,
    val recentAtoms: List<PendingAtomSummary>,
) {
    val hasAttention: Boolean get() = pendingTotal > 0
}

/** 立即同步的结果：先推（重放 pending）后拉（增量引擎按游标续拉，未登录返回 null）。 */
data class SyncNowReport(
    val push: SyncAtomReplayer.Report,
    val pull: SyncPullEngine.Report?,
) {
    fun summary(): String =
        "推送: ${push.summary()}；拉取: ${pull?.summary() ?: "未登录，跳过"}"
}

/**
 * 同步状态只读仓储：给同步页提供快照查询 + 手动触发"立即同步"（推送 + 增量拉取）。
 * 数据源与 [[SocialRepository]] 相同（两库 + SyncAtom 队列 + 载体），不持有独立状态。
 */
class SyncStatusRepository(
    private val cache: CacheDatabase = CacheDbHolder.get(),
    private val system: SystemDatabase = SystemDbHolder.get(),
    private val social: SocialRepository = SocialRepository(cache, system),
) {

    private val pullEngine = SyncPullEngine(cache, system, social)

    fun observePendingCount(): Flow<Int> = system.syncAtomDao().observePendingCount()

    suspend fun snapshot(): SyncStatusSnapshot {
        val session = system.userSyncStateDao().getActiveState()
        val atoms = system.syncAtomDao().getAllAtoms()
        val failed = atoms.filter { it.attempts > 0 }
        val snapshot = SyncStatusSnapshot(
            loggedIn = session != null,
            cursor = session?.syncVersion ?: 0L,
            lastSyncTime = session?.lastSyncTime ?: 0L,
            pendingTotal = atoms.size,
            failedCount = failed.size,
            lastError = failed.maxByOrNull { it.id }?.lastError,
            oldestPendingAt = atoms.minByOrNull { it.id }?.createdAt,
            localPersons = cache.personDao().observeAllPersons().first().size,
            localCollections = cache.collectionDao().getCollections().size,
            localTags = cache.tagsDao().observeAllTags().first().size,
            recentAtoms = atoms.take(RECENT_ATOM_LIMIT).map { it.toSummary() },
        )
        BadgerLog.d(TAG, "snapshot: 登录=${snapshot.loggedIn} 游标=${snapshot.cursor} pending=${snapshot.pendingTotal}")
        return snapshot
    }

    suspend fun syncNow(): SyncNowReport {
        val push = social.pushPending()
        val pull = pullEngine.pullAll()
        BadgerLog.d(TAG, "syncNow: ${push.summary()} | ${pull?.summary() ?: "未登录跳过"}")
        return SyncNowReport(push, pull)
    }

    private fun SyncAtom.toSummary() = PendingAtomSummary(
        id = id,
        kind = entityKind,
        syncType = syncType,
        attempts = attempts,
        lastError = lastError,
        createdAt = createdAt,
    )

    companion object {
        private const val TAG = "SyncStatusRepositoryTester"

        /** 快照最多携带的 pending 明细条数，队列堆积时不至于塞满整页。 */
        private const val RECENT_ATOM_LIMIT = 5
    }
}
