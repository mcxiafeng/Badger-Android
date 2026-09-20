package top.mcxiafeng.badger.data.system.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.system.entity.UserSyncState
import kotlin.uuid.Uuid

@Dao
interface UserSyncStateDao {

    @Query("SELECT * FROM UserSyncState WHERE userUuid = :userUuid")
    suspend fun getState(userUuid: Uuid): UserSyncState?

    /** 当前登录会话（单会话模型：登录时 clearAllStates 后只保留一行）。 */
    @Query("SELECT * FROM UserSyncState LIMIT 1")
    suspend fun getActiveState(): UserSyncState?

    @Query("SELECT * FROM UserSyncState WHERE userUuid = :userUuid")
    fun observeState(userUuid: Uuid): Flow<UserSyncState?>

    @Upsert
    suspend fun upsertState(state: UserSyncState)

    /**
     * 推进同步游标：是否前进由 [syncVersion] 决定（权威），
     * [serverTime] 只随行记录服务端下发时间（禁止传客户端本地时钟值）。
     */
    @Query(
        "UPDATE UserSyncState SET syncVersion = :syncVersion, lastSyncTime = :serverTime " +
            "WHERE userUuid = :userUuid"
    )
    suspend fun advanceSyncCursor(userUuid: Uuid, syncVersion: Long, serverTime: Long)

    @Query("UPDATE UserSyncState SET token = :token WHERE userUuid = :userUuid")
    suspend fun updateToken(userUuid: Uuid, token: String)

    @Query("DELETE FROM UserSyncState WHERE userUuid = :userUuid")
    suspend fun deleteState(userUuid: Uuid)

    @Query("DELETE FROM UserSyncState")
    suspend fun clearAllStates()
}
