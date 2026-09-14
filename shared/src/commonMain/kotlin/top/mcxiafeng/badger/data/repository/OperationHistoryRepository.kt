package top.mcxiafeng.badger.data.repository

import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.queue.OperationHistoryEntity

interface OperationHistoryRepository {

    

    fun observeHistory(
        filter: HistoryFilter = HistoryFilter.All,
        limit: Int = 100,
    ): Flow<List<OperationHistoryWithContact>>
}

data class OperationHistoryWithContact(
    val history: OperationHistoryEntity,
    val contactName: String?,
)

enum class HistoryFilter {
    
    All,

    
    Pending,
}
