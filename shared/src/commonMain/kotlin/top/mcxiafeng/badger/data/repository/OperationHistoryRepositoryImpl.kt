package top.mcxiafeng.badger.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.queue.OperationHistoryDao

class OperationHistoryRepositoryImpl(
    private val historyDao: OperationHistoryDao,
    private val contactCacheDao: ContactCacheDao,
) : OperationHistoryRepository {

    override fun observeHistory(
        filter: HistoryFilter,
        limit: Int,
    ): Flow<List<OperationHistoryWithContact>> =
        combine(
            historyDao.observeRecent(limit = limit),
            contactCacheDao.getAllContacts(),
        ) { historyList, contacts ->
            val contactMap = contacts.associate { it.id to it.name }
            historyList
                .map { history ->
                    OperationHistoryWithContact(
                        history = history,
                        contactName = contactMap[history.contactId],
                    )
                }
                .let { joined ->
                    when (filter) {
                        HistoryFilter.All -> joined
                        HistoryFilter.Pending -> joined.filter { item ->
                            item.history.opStatus == "CONFLICT" ||
                                item.history.opStatus == "FAILED" ||
                                item.history.opStatus == "FAILED_PERMANENT"
                        }
                    }
                }
        }
}
