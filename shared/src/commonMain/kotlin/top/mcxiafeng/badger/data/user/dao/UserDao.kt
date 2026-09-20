package top.mcxiafeng.badger.data.user.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import top.mcxiafeng.badger.data.user.entity.User
import kotlin.uuid.Uuid

@Dao
interface UserDao {

    @Upsert
    suspend fun upsertUser(user: User)

    @Delete
    suspend fun deleteUser(user: User)

    @Query("SELECT * FROM User WHERE uuid = :uuid")
    suspend fun getUser(uuid: Uuid): User?
}