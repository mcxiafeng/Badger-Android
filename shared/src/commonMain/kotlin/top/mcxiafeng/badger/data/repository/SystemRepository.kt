package top.mcxiafeng.badger.data.repository

import kotlinx.serialization.json.JsonObject
import top.mcxiafeng.badger.data.system.dao.SyncAtomDao
import top.mcxiafeng.badger.data.system.dao.UserInfoDao
import top.mcxiafeng.badger.data.system.database.SystemDbHolder
import top.mcxiafeng.badger.data.system.entity.SyncAtom
import top.mcxiafeng.badger.sync.EntityKind
import top.mcxiafeng.badger.sync.SyncType
import top.mcxiafeng.badger.utils.BadgerLog
import kotlin.time.Clock

private const val TAG = "SystemRepositoryTester"

/**
 * 系统库 CRUD 仓储，薄封装 [SyncAtomDao] + [UserInfoDao]，只做增删改查。
 *
 * 不含同步编排逻辑（快照聚合 / 推拉调度）——那些属于上层 SyncEngine / VM。
 */
class SystemRepository(
    private val syncAtomDao: SyncAtomDao = SystemDbHolder.get().syncAtomDao(),
    private val userInfoDao: UserInfoDao = SystemDbHolder.get().userInfoDao(),
) {

    /**
     * 入队一条待同步意图（本地写操作落库后调用）。
     * 要求已登录（活跃载体存在，fail-loud）；createdAt 取本地时钟，当前无读取方（重放排序走自增 id）。
     * atom 携带 userUuid：引擎按会话用户门控和解，掉登录/换账号不影响队列。
     */
    suspend fun enqueueAtom(kind: EntityKind, type: SyncType, data: JsonObject): Long {
        val userUuid = getActiveUserInfo()?.userUuid
            ?: throw IllegalStateException("未登录：无活跃载体，无法入队同步意图 ($kind/$type)")
        val atom = SyncAtom(
            userUuid = userUuid,
            entityKind = kind,
            syncType = type,
            data = data,
            createdAt = Clock.System.now().toEpochMilliseconds(),
        )
        val id = syncAtomDao.insertAtom(atom)
        BadgerLog.d(TAG, "入队 atom#$id: $kind/$type")
        return id
    }

    suspend fun getAllAtoms(): List<SyncAtom> = syncAtomDao.getAllAtoms()

    suspend fun deleteAtomById(id: Long) = syncAtomDao.deleteAtomById(id)

    /** 单条 SQL 自增 attempts 并记录最后一次错误（fail-loud 记账）。 */
    suspend fun updateReplayFailure(id: Long, lastError: String?) = syncAtomDao.incrementReplayFailure(id, lastError)

    /** 当前登录会话（单会话模型：登录时清空后只保留一行）。 */
    suspend fun getActiveUserInfo() = userInfoDao.getActiveUserInfo()
}
