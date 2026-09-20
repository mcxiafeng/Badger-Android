package top.mcxiafeng.badger.data.system.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import top.mcxiafeng.badger.data.system.entity.SyncAtom
import top.mcxiafeng.badger.sync.EntityKind

@Dao
interface SyncAtomDao {

    @Insert
    suspend fun insertAtom(atom: SyncAtom): Long

    @Query("SELECT * FROM SyncAtom ORDER BY id ASC")
    suspend fun getAllAtoms(): List<SyncAtom>

    @Query("SELECT COUNT(*) FROM SyncAtom WHERE entityKind = :kind")
    suspend fun pendingCount(kind: EntityKind): Int

    @Query("SELECT COUNT(*) FROM SyncAtom")
    fun observePendingCount(): Flow<Int>

    @Query("DELETE FROM SyncAtom WHERE id = :id")
    suspend fun deleteAtomById(id: Long)

    @Query("DELETE FROM SyncAtom WHERE entityKind = :kind")
    suspend fun deleteAtomsByKind(kind: EntityKind)

    @Query("UPDATE SyncAtom SET attempts = :attempts, lastError = :lastError WHERE id = :id")
    suspend fun updateReplayFailure(id: Long, attempts: Int, lastError: String?)

    @Query("UPDATE SyncAtom SET data = :data WHERE id = :id")
    suspend fun updatePayload(id: Long, data: JsonObject)
}
