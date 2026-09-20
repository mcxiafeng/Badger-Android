package top.mcxiafeng.badger.data.user.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlin.uuid.Uuid
import top.mcxiafeng.badger.data.user.entity.Profile

@Dao
interface ProfileDao {

    @Upsert
    suspend fun upsertProfile(profile: Profile)

    @Delete
    suspend fun deleteProfile(profile: Profile)

    @Query("SELECT * FROM Profile WHERE uuid = :uuid")
    suspend fun getProfile(uuid: Uuid): Profile?

}
