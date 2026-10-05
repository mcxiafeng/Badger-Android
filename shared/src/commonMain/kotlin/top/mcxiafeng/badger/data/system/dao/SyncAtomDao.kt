package top.mcxiafeng.badger.data.system.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import top.mcxiafeng.badger.data.system.entity.SyncAtom

@Dao
interface SyncAtomDao {

    @Insert
    suspend fun insertAtom(atom: SyncAtom): Long

    @Query("SELECT * FROM SyncAtom ORDER BY id ASC")
    suspend fun getAllAtoms(): List<SyncAtom>

    @Query("DELETE FROM SyncAtom WHERE id = :id")
    suspend fun deleteAtomById(id: Long)

    /** 单条 SQL 自增 attempts（禁止读-改-写，见 AGENTS.md SyncAtom 队列契约）。 */
    @Query("UPDATE SyncAtom SET attempts = attempts + 1, lastError = :lastError WHERE id = :id")
    suspend fun incrementReplayFailure(id: Long, lastError: String?)
}
