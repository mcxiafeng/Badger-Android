package top.mcxiafeng.badger.data.queue

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OutboxDao {

    
    @Query(
        "SELECT * FROM outbox WHERE nextAttemptAt <= :now " +
            "ORDER BY createdAt ASC, id ASC LIMIT :limit"
    )
    suspend fun getReady(now: Long, limit: Int): List<OutboxEntity>

    
    @Query(
        "SELECT * FROM outbox " +
            "ORDER BY createdAt ASC, id ASC LIMIT :limit"
    )
    suspend fun getReadyIncludingBackoff(limit: Int): List<OutboxEntity>

    
    @Query("SELECT * FROM outbox WHERE mergeKey = :mergeKey LIMIT 1")
    suspend fun getByMergeKey(mergeKey: String): OutboxEntity?

    

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOrAbort(entity: OutboxEntity): Long

    
    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    
    @Query(
        "DELETE FROM outbox WHERE entityKind = :entityKind AND localId = :localId " +
            "AND op IN ('CREATE', 'PATCH')"
    )
    suspend fun deleteUnsentCreateAndPatch(entityKind: String, localId: Long): Int

    

    @Query(
        "UPDATE outbox SET remoteId = :newRemoteId, updatedAt = :now " +
            "WHERE entityKind = :entityKind AND localId = :localId AND remoteId = :oldRemoteId"
    )
    suspend fun backfillRemoteId(
        entityKind: String,
        localId: Long,
        oldRemoteId: String,
        newRemoteId: String,
        now: Long,
    ): Int

    

    @Query(
        "SELECT * FROM outbox WHERE op IN ('MEMBER_ADD', 'MEMBER_REMOVE') " +
            "AND payloadJson LIKE :quotedUuidNeedle"
    )
    suspend fun getMemberRowsReferencing(quotedUuidNeedle: String): List<OutboxEntity>

    
    @Query("UPDATE outbox SET payloadJson = :payloadJson, updatedAt = :now WHERE id = :id")
    suspend fun updatePayloadJson(id: Long, payloadJson: String, now: Long): Int

    

    @Query(
        "UPDATE outbox SET " +
            "attempts = attempts + 1, " +
            "nextAttemptAt = :now + :baseBackoffMillis * (1 << min(attempts, :maxBackoffExponent)), " +
            "updatedAt = :now, " +
            "lastError = :lastError " +
            "WHERE id = :id"
    )
    suspend fun recordFailure(
        id: Long,
        now: Long,
        baseBackoffMillis: Long,
        maxBackoffExponent: Int,
        lastError: String?,
    ): Int

    
    @Query("SELECT COUNT(*) FROM outbox")
    suspend fun count(): Int

    
    @Query("DELETE FROM outbox")
    suspend fun clearAll()
}
