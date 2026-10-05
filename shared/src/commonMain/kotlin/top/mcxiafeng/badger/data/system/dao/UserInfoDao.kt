package top.mcxiafeng.badger.data.system.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import top.mcxiafeng.badger.data.system.entity.UserInfo
import kotlin.uuid.Uuid

@Dao
interface UserInfoDao {

    @Query("SELECT * FROM UserInfo WHERE userUuid = :userUuid")
    suspend fun getUserInfo(userUuid: Uuid): UserInfo?

    /** 当前登录会话（单会话模型：登录时 clearAllStates 后只保留一行）。 */
    @Query("SELECT * FROM UserInfo LIMIT 1")
    suspend fun getActiveUserInfo(): UserInfo?

    @Upsert
    suspend fun upsertUserInfo(info: UserInfo)

    @Query("DELETE FROM UserInfo")
    suspend fun clearAllUserInfos()
}
