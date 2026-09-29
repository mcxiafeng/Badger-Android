package top.mcxiafeng.badger.data.user.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.user.entity.Contact
import top.mcxiafeng.badger.data.user.entity.Platform

@Dao
interface PlatformDao{
    @Upsert
    suspend fun upsertPlatform(profile: Platform)

    @Delete
    suspend fun deletePlatform(profile: Platform)

    @Query("SELECT * FROM Platform WHERE name = :name")
    suspend fun getPlatform(name: String?): Platform?
    @Query("SELECT * FROM Platform")
    fun observeAllPlatform(): Flow<List<Platform>>
}
