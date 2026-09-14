package top.mcxiafeng.badger.data.queue

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OperationHistoryDao {

    

    
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(op: OperationHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(ops: List<OperationHistoryEntity>)

    
    @Query("SELECT * FROM operation_history WHERE opId = :opId LIMIT 1")
    suspend fun getById(opId: String): OperationHistoryEntity?

    @Query("SELECT * FROM operation_history WHERE opId = :opId LIMIT 1")
    fun observeById(opId: String): Flow<OperationHistoryEntity?>

    

    @Query("""
        SELECT * FROM operation_history
        ORDER BY createdAt DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getPage(limit: Int, offset: Int): List<OperationHistoryEntity>

    
    @Query("SELECT * FROM operation_history ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<OperationHistoryEntity>>

    
    @Query("""
        SELECT * FROM operation_history
        WHERE contactId = :contactId
        ORDER BY createdAt DESC
        LIMIT :limit
    """)
    suspend fun getByContact(contactId: Long, limit: Int = 50): List<OperationHistoryEntity>

    @Query("""
        SELECT * FROM operation_history
        WHERE contactId = :contactId
        ORDER BY createdAt DESC
        LIMIT :limit
    """)
    fun observeByContact(contactId: Long, limit: Int = 50): Flow<List<OperationHistoryEntity>>

    
    @Query("""
        SELECT * FROM operation_history
        WHERE opStatus IN ('CONFLICT', 'FAILED_PERMANENT')
        ORDER BY createdAt DESC
        LIMIT :limit
    """)
    fun observePending(limit: Int = 50): Flow<List<OperationHistoryEntity>>

    
    @Query("SELECT COUNT(*) FROM operation_history")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM operation_history WHERE opStatus = :status")
    suspend fun countByStatus(status: String): Int

    

    

    @Query("""
        UPDATE operation_history
        SET opStatus = 'DONE',
            serverVersion = :serverVersion,
            snapshotAfterJson = :snapshotAfterJson,
            lastError = NULL
        WHERE opId = :opId
    """)
    suspend fun markDone(opId: String, serverVersion: Long?, snapshotAfterJson: String?)

    

    @Query("""
        UPDATE operation_history
        SET opStatus = 'CONFLICT',
            serverVersion = :serverVersion,
            lastError = :lastError
        WHERE opId = :opId
    """)
    suspend fun markConflict(opId: String, serverVersion: Long?, lastError: String)

    
    @Query("""
        UPDATE operation_history
        SET opStatus = 'FAILED',
            attempts = :attempts,
            lastError = :lastError
        WHERE opId = :opId
    """)
    suspend fun markFailed(opId: String, attempts: Int, lastError: String)

    
    @Query("UPDATE operation_history SET opStatus = 'WITHDRAWN', lastError = NULL WHERE opId = :opId")
    suspend fun markWithdrawn(opId: String)

    
    @Query("UPDATE operation_history SET lastError = NULL, attempts = attempts + 1 WHERE opId = :opId")
    suspend fun touchRetry(opId: String)

    

    

    @Query("""
        DELETE FROM operation_history
        WHERE createdAt < :before
          AND opStatus IN ('DONE', 'FAILED_PERMANENT')
    """)
    suspend fun purgeOld(before: Long): Int

    
    @Query("DELETE FROM operation_history")
    suspend fun clearAll()
}