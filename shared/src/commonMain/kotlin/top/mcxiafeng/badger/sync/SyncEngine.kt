package top.mcxiafeng.badger.sync

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.mcxiafeng.badger.data.repository.SystemRepository
import top.mcxiafeng.badger.data.repository.UserRepository
import top.mcxiafeng.badger.data.system.entity.SyncAtom
import top.mcxiafeng.badger.data.user.entity.Collection
import top.mcxiafeng.badger.data.user.entity.Person
import top.mcxiafeng.badger.data.user.entity.Profile
import top.mcxiafeng.badger.data.user.entity.Tags
import top.mcxiafeng.badger.utils.BadgerLog
import kotlin.uuid.Uuid

private const val TAG = "SyncEngineTester"

/**
 * 双向同步引擎（以服务端为主）——纯编排层，判定表与实体原语在 [Syncable]。
 * 职责按数据方向切分：**拉取只由页面打开触发**（[syncNow]，页面只拉自己展示的实体）；
 * **推送队列是全局的**（[pushQueue]，启动/回前台/网络恢复时发送 SyncAtom，
 * 队列空则零网络）。冲突弹窗二选一，点外部不裁决。
 * 队列按 atom.userUuid 门控，换账号不掉队列；缓存随页面打开的拉取收敛。
 * 失败语义：拉取失败折叠 [SyncOutcome.pullFailed]、推送失败记账留队，其余异常即 bug 直接崩。
 */
class SyncEngine(
    private val systemRepository: SystemRepository,
    private val userRepository: UserRepository,
    private val personSyncer: Syncable<Person>,
    private val profileSyncer: Syncable<Profile>,
    private val collectionSyncer: Syncable<Collection>,
    private val tagSyncer: Syncable<Tags>,
) {

    private val syncMutex = Mutex()
    private val inFlight = atomic(false)

    private val _conflicts = MutableStateFlow<List<SyncConflict>>(emptyList())
    val conflicts: StateFlow<List<SyncConflict>> = _conflicts.asStateFlow()

    private val _rounds = MutableStateFlow(0L)

    /** 同步轮次计数：冲突弹窗据此实现"点外部后，下一轮同步重新弹"（用户裁决的生命周期语义）。 */
    val rounds: StateFlow<Long> = _rounds.asStateFlow()

    /**
     * 页面打开的一轮：拉取 [kinds] 实体落库展示 + 和解该类队列 atom。
     * 拉取变更只属于打开页面——每个页面只传自己展示的实体。
     */
    suspend fun syncNow(vararg kinds: EntityKind): SyncOutcome {
        if (kinds.isEmpty()) {
            throw IllegalArgumentException("syncNow 需要显式指定实体：拉取只由页面打开触发，全局时刻请用 pushQueue()")
        }
        if (!inFlight.compareAndSet(false, true)) {
            BadgerLog.d(TAG, "syncNow: 已有同步在跑，跳过本轮")
            return SyncOutcome.SKIPPED
        }
        _rounds.value += 1
        try {
            val session = systemRepository.getActiveUserInfo() ?: run {
                BadgerLog.d(TAG, "syncNow: 无活跃载体（未登录），跳过")
                return SyncOutcome.SKIPPED
            }
            return syncMutex.withLock { runSync(session.userUuid, kinds.toSet()) }
        } finally {
            inFlight.value = false
        }
    }

    /**
     * 推送离线队列（启动/回前台/网络恢复等全局时刻用）：只发送 SyncAtom，不为展示拉数据。
     * 队列空 = 零网络；每条 atom 各自经单查接口/清单过滤核对服务端现状（判定表内部），
     * 不做任何全量快照拉取——缓存刷新是页面打开的事。
     */
    suspend fun pushQueue(): SyncOutcome {
        if (!inFlight.compareAndSet(false, true)) {
            BadgerLog.d(TAG, "pushQueue: 已有同步在跑，跳过本轮")
            return SyncOutcome.SKIPPED
        }
        _rounds.value += 1
        try {
            val session = systemRepository.getActiveUserInfo() ?: run {
                BadgerLog.d(TAG, "pushQueue: 无活跃载体（未登录），跳过")
                return SyncOutcome.SKIPPED
            }
            return syncMutex.withLock {
                val atoms = pendingAtomsFor(session.userUuid)
                if (atoms.isEmpty()) {
                    BadgerLog.d(TAG, "pushQueue: 队列空，零网络")
                    return@withLock SyncOutcome()
                }
                reconcileAtoms(atoms, atoms.map { it.entityKind }.toSet())
                SyncOutcome()
            }
        } finally {
            inFlight.value = false
        }
    }

    /**
     * 冲突裁决收口。
     * [keepLocal]=true：重推本地修改（失败则留队，冲突保持）；false：服务端值覆盖本地并销账。
     */
    suspend fun resolveConflict(atomId: Long, keepLocal: Boolean) {
        syncMutex.withLock {
            val atom = systemRepository.getAllAtoms().firstOrNull { it.id == atomId }
            if (atom != null && atom.userUuid != systemRepository.getActiveUserInfo()?.userUuid) {
                // 保险丝：冲突态未按账号清理（logout 尚未实现）期间换账号，拒绝裁决他账号的 atom，
                // 防止拿新会话的 token 把旧用户的数据推上服务端
                BadgerLog.w(TAG, "resolveConflict: atom#$atomId 属于其他用户，拒绝裁决")
                return
            }
            when {
                atom == null ->
                    BadgerLog.w(TAG, "resolveConflict: atom#$atomId 不存在（可能已销账），仅清理冲突态")

                keepLocal -> {
                    BadgerLog.d(TAG, "冲突裁决 atom#$atomId：保留我的，重推本地值")
                    syncableFor(atom.entityKind).push(atom)
                    systemRepository.deleteAtomById(atom.id)
                }

                else -> {
                    BadgerLog.d(TAG, "冲突裁决 atom#$atomId：用服务端的，覆盖本地并销账")
                    syncableFor(atom.entityKind).applyServer(atom)
                    systemRepository.deleteAtomById(atom.id)
                }
            }
            _conflicts.update { list -> list.filterNot { it.atomId == atomId } }
        }
    }

    private suspend fun runSync(userUuid: Uuid, kinds: Set<EntityKind>): SyncOutcome {
        BadgerLog.d(TAG, "页面同步：先拉取服务端快照（scope=$kinds）")
        val snapshot = try {
            pullSnapshot(userUuid, kinds)
        } catch (e: SyncGatewayException) {
            // 离线/服务端不可达是预期失败：折叠进返回值，不抛给调用方
            BadgerLog.w(TAG, "拉取失败，本轮不落库不和解，等下次 kick：${e.message}")
            return SyncOutcome(pullFailed = true)
        }
        // 只和解本轮拉取过的实体——其他实体的 atom 原样留队，等它们自己的同步轮
        val atoms = pendingAtomsFor(userUuid).filter { it.entityKind in kinds }
        val pending = PendingIndex(atoms)

        if (EntityKind.PERSON in kinds) personSyncer.persist(snapshot.persons, pending)
        if (EntityKind.COLLECTION in kinds) collectionSyncer.persist(snapshot.collections, pending)
        if (EntityKind.TAG in kinds) tagSyncer.persist(snapshot.tags, pending)
        if (EntityKind.PROFILE in kinds) profileSyncer.persist(snapshot.ownProfile, pending)

        reconcileAtoms(atoms, kinds)
        return SyncOutcome()
    }

    /** 逐条和解 + 销账；冲突按范围合并进弹窗流。每条 atom 各自查服务端现状（判定表内部）。 */
    private suspend fun reconcileAtoms(atoms: List<SyncAtom>, kinds: Set<EntityKind>) {
        val conflictEntries = mutableListOf<SyncConflict>()
        for (atom in atoms) {
            val result = reconcileAtom(atom)
            if (result is ReconcileResult.Conflicted) conflictEntries += result.entry
            // Settled 即销账；Failed 已在 reconcileAtom 记账留队
            if (result is ReconcileResult.Settled) {
                systemRepository.deleteAtomById(atom.id)
            }
        }
        // 范围外的冲突保留在弹窗流里（它们的 atom 还没被本轮触碰）
        _conflicts.update { current ->
            current.filter { it.entityKind !in kinds } + conflictEntries
        }
    }

    /** 队列按会话 userUuid 门控：其他用户的 atom 原样保留（该用户登录回来时继续推）。 */
    private suspend fun pendingAtomsFor(userUuid: Uuid): List<SyncAtom> {
        val allAtoms = systemRepository.getAllAtoms()
        val foreign = allAtoms.count { it.userUuid != userUuid }
        if (foreign > 0) {
            BadgerLog.d(TAG, "跳过 $foreign 条其他用户的 atom（按 userUuid 门控，该用户登录回来时继续推）")
        }
        return allAtoms.filter { it.userUuid == userUuid }
    }

    private suspend fun pullSnapshot(userUuid: Uuid, kinds: Set<EntityKind>): Snapshot {
        val user = userRepository.get(userUuid)
            ?: throw IllegalStateException("载体指向的用户缺失：$userUuid，请重新登录")
        val persons = if (EntityKind.PERSON in kinds) personSyncer.fetchAll(user.profileUuid) else emptyList()
        val ownProfile = if (EntityKind.PROFILE in kinds) profileSyncer.fetchAll(user.profileUuid) else emptyList()
        val collections = if (EntityKind.COLLECTION in kinds) collectionSyncer.fetchAll(user.profileUuid) else emptyList()
        val tags = if (EntityKind.TAG in kinds) tagSyncer.fetchAll(user.profileUuid) else emptyList()
        BadgerLog.d(
            TAG,
            "拉取完成：persons=${persons.size} collections=${collections.size} tags=${tags.size} " +
                "profile=${ownProfile.firstOrNull()?.uuid}",
        )
        return Snapshot(persons, ownProfile, collections, tags)
    }

    private suspend fun reconcileAtom(atom: SyncAtom): ReconcileResult =
        try {
            when (atom.entityKind) {
                EntityKind.PERSON -> personSyncer.reconcile(atom)
                EntityKind.PROFILE -> profileSyncer.reconcile(atom)
                EntityKind.COLLECTION -> collectionSyncer.reconcile(atom)
                EntityKind.TAG -> tagSyncer.reconcile(atom)
            }
        } catch (e: SyncGatewayException) {
            // 只捕推送的预期失败（网络、服务端非 2xx）并记账重试；其余异常是 bug，直接崩
            BadgerLog.w(TAG, "atom#${atom.id}(${atom.entityKind}/${atom.syncType}) 推送失败，留队下轮重试", e)
            systemRepository.updateReplayFailure(atom.id, e.message)
            ReconcileResult.Failed
        }

    private fun syncableFor(kind: EntityKind): Syncable<*> = when (kind) {
        EntityKind.PERSON -> personSyncer
        EntityKind.PROFILE -> profileSyncer
        EntityKind.COLLECTION -> collectionSyncer
        EntityKind.TAG -> tagSyncer
    }

    /** 一轮同步内复用的服务端快照（ownProfile 统一为单元素表）。 */
    private class Snapshot(
        val persons: List<Person>,
        val ownProfile: List<Profile>,
        val collections: List<Collection>,
        val tags: List<Tags>,
    )
}

/**
 * [SyncEngine] 全局单例持有者，模式同 CacheDbHolder / SystemDbHolder。
 * 组合根在应用入口 init 一次，VM / UI 经 [get] 取用。
 */
object SyncEngineHolder {

    private var instance: SyncEngine? = null

    fun init(engine: SyncEngine) {
        check(instance == null) { "SyncEngine 已初始化，禁止重复 init" }
        instance = engine
        BadgerLog.d("SyncEngineHolderTester", "SyncEngine 初始化完成")
    }

    fun get(): SyncEngine =
        instance ?: error("SyncEngine 未初始化：请先在应用入口调用 SyncEngineHolder.init()")
}
