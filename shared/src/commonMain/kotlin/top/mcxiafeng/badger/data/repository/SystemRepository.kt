package top.mcxiafeng.badger.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import top.mcxiafeng.badger.data.system.dao.SyncAtomDao
import top.mcxiafeng.badger.data.system.dao.UserInfoDao
import top.mcxiafeng.badger.data.system.database.SystemDbHolder
import top.mcxiafeng.badger.data.system.entity.SyncAtom
import top.mcxiafeng.badger.data.system.entity.UserInfo
import top.mcxiafeng.badger.sync.EntityKind
import kotlin.uuid.Uuid

/**
 * 系统库 CRUD 仓储，薄封装 [SyncAtomDao] + [UserInfoDao]，只做增删改查。
 *
 * 不含同步编排逻辑（快照聚合 / 推拉调度）——那些属于上层 SyncEngine / VM。
 */
class SystemRepository(
    private val syncAtomDao: SyncAtomDao = SystemDbHolder.get().syncAtomDao(),
    private val userInfoDao: UserInfoDao = SystemDbHolder.get().userInfoDao(),
) {

    // ---------------- SyncAtom ----------------

    suspend fun insertAtom(atom: SyncAtom): Long = syncAtomDao.insertAtom(atom)

    suspend fun getAllAtoms(): List<SyncAtom> = syncAtomDao.getAllAtoms()

    suspend fun pendingCount(kind: EntityKind): Int = syncAtomDao.pendingCount(kind)

    fun observePendingCount(): Flow<Int> = syncAtomDao.observePendingCount()

    suspend fun deleteAtomById(id: Long) = syncAtomDao.deleteAtomById(id)

    suspend fun deleteAtomsByKind(kind: EntityKind) = syncAtomDao.deleteAtomsByKind(kind)

    suspend fun updateReplayFailure(id: Long, attempts: Int, lastError: String?) = syncAtomDao.updateReplayFailure(id, attempts, lastError)

    suspend fun updatePayload(id: Long, data: JsonObject) = syncAtomDao.updatePayload(id, data)

    // ---------------- UserSyncState ----------------

    suspend fun getUserInfo(userUuid: Uuid): UserInfo? = userInfoDao.getUserInfo(userUuid)

    suspend fun getActiveUserInfo(): UserInfo? = userInfoDao.getActiveUserInfo()

    fun observeUserInfo(userUuid: Uuid): Flow<UserInfo?> = userInfoDao.observeUserInfo(userUuid)

    suspend fun upsertUserInfo(state: UserInfo) = userInfoDao.upsertUserInfo(state)

    suspend fun advanceSyncCursor(userUuid: Uuid, syncVersion: Long, serverTime: Long) = userInfoDao.advanceSyncCursor(userUuid, syncVersion, serverTime)

    suspend fun updateToken(userUuid: Uuid, token: String) =  userInfoDao.updateToken(userUuid, token)

    suspend fun deleteUserInfo(userUuid: Uuid) = userInfoDao.deleteUserInfo(userUuid)

    suspend fun clearAllUserInfos() = userInfoDao.clearAllUserInfos()
}
