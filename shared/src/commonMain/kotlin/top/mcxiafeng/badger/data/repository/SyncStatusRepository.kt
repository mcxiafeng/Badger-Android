package top.mcxiafeng.badger.data.repository

interface SyncStatusRepository {

    

    suspend fun snapshot(): SyncStatusSnapshot

    

    suspend fun retryAll(): Int
}

data class SyncStatusSnapshot(
    
    val lastSyncVersion: Long = 0,
    
    val lastSyncedAt: Long = 0,
    
    val unsyncedCount: Int = 0,
) {
    

    val hasAttention: Boolean
        get() = unsyncedCount > 0
}
