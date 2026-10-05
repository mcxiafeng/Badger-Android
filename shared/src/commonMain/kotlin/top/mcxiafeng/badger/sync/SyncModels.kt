package top.mcxiafeng.badger.sync

import top.mcxiafeng.badger.data.system.entity.SyncAtom
import kotlin.uuid.Uuid

/** 一条待和解修改与服务端现值撞车，等用户裁决（弹窗数据源）。 */
data class SyncConflict(
    val atomId: Long,
    val entityKind: EntityKind,
    val objectId: Uuid,
    val displayName: String,
)

/** 一轮 syncNow 的结果：pullFailed 是唯一的类型化预期失败（离线/服务端不可达）。 */
data class SyncOutcome(
    val pullFailed: Boolean = false,
) {
    companion object {

        /** 未登录 / 已有同步在跑等跳过场景。 */
        val SKIPPED = SyncOutcome()
    }
}

/** 单条 atom 的和解结果（同步模块内部，引擎据此销账/挂冲突）。 */
sealed interface ReconcileResult {

    /** 已和解（推送/采纳/幂等销账三者之一），引擎销账。 */
    data object Settled : ReconcileResult

    /** 双方都改过，挂起等用户裁决。 */
    data class Conflicted(val entry: SyncConflict) : ReconcileResult

    /** 推送失败，已记 attempts/lastError，留队重试。 */
    data object Failed : ReconcileResult
}

/** pending atom 按 kind 归类的 uuid 索引（落库避让的判据）。 */
class PendingIndex(atoms: List<SyncAtom>) {

    private val byKind: Map<EntityKind, List<SyncAtom>> = atoms.groupBy { it.entityKind }

    /** 该 kind 全部 pending uuid（INSERT/UPDATE/DELETE）——落库时服务端行跳过这些。 */
    fun all(kind: EntityKind): Set<Uuid> = byKind[kind].orEmpty().map { AtomPayload.uuidOf(it.syncType, it.data) }.toSet()

    /** 该 kind 受保护的 uuid（INSERT/UPDATE）——deleteNotIn 清理时必须保留本地行。 */
    fun protected(kind: EntityKind): Set<Uuid> = byKind[kind].orEmpty()
        .filter { it.syncType != SyncType.DELETE }
        .map { AtomPayload.uuidOf(it.syncType, it.data) }
        .toSet()

    /** 该 kind 离线删除意图的 uuid——落库时保证本地行不存在。 */
    fun deleteUuids(kind: EntityKind): Set<Uuid> = byKind[kind].orEmpty()
        .filter { it.syncType == SyncType.DELETE }
        .map { AtomPayload.uuidOf(it.syncType, it.data) }
        .toSet()
}
