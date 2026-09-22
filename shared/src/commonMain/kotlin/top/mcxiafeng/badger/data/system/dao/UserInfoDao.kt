package top.mcxiafeng.badger.data.system.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.system.entity.UserInfo
import kotlin.uuid.Uuid

@Dao
interface UserInfoDao {

    @Query("SELECT * FROM UserInfo WHERE userUuid = :userUuid")
    suspend fun getUserInfo(userUuid: Uuid): UserInfo?

    /** 当前登录会话（单会话模型：登录时 clearAllStates 后只保留一行）。 */
    @Query("SELECT * FROM UserInfo LIMIT 1")
    suspend fun getActiveUserInfo(): UserInfo?

    @Query("SELECT * FROM UserInfo WHERE userUuid = :userUuid")
    fun observeUserInfo(userUuid: Uuid): Flow<UserInfo?>

    @Upsert
    suspend fun upsertUserInfo(info: UserInfo)

    @Query("DELETE FROM UserInfo WHERE userUuid = :userUuid")
    suspend fun deleteUserInfo(userUuid: Uuid)

    /**
     * 推进同步游标：是否前进由 [syncVersion] 决定（权威），
     * [serverTime] 只随行记录服务端下发时间（禁止传客户端本地时钟值）。
     */
    @Query(
        "UPDATE UserInfo SET syncVersion = :syncVersion, lastSyncTime = :serverTime " +
            "WHERE userUuid = :userUuid"
    )
    suspend fun advanceSyncCursor(userUuid: Uuid, syncVersion: Long, serverTime: Long)

    @Query("UPDATE UserInfo SET token = :token WHERE userUuid = :userUuid")
    suspend fun updateToken(userUuid: Uuid, token: String)


    @Query("DELETE FROM UserInfo")
    suspend fun clearAllUserInfos()
}
