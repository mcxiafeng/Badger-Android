package top.mcxiafeng.badger.data.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.queue.OperationHistoryDao

/**
 * [V2-P7/P8] OperationHistoryRepository impl。
 *
 * [Phase 3] 降级为**只读本地日志**：
 * - 队列退役后不再有 PendingUpload 消费 / 撤销双边同步 / 冲突解决；
 * - 本实现只把 `OperationHistoryEntity` 与联系人名做 in-memory LEFT JOIN，
 *   供 [OperationHistoryPage] 只读展示，副作用方法全部删除。
 *
 * [§14.2] Hilt `@Singleton @Inject constructor` → Koin `singleOf(::OperationHistoryRepositoryImpl) { bind<OperationHistoryRepository>() }`。
 */
class OperationHistoryRepositoryImpl(
    private val historyDao: OperationHistoryDao,
    private val contactCacheDao: ContactCacheDao,
) : OperationHistoryRepository {

    private val tag = TAG

    /**
     * 订阅 history + 联系人名 join。
     *
     * combine 触发条件：任一上游变化 → 重新 join。`observeRecent(limit=100)` 已经按
     * createdAt DESC 排序，join 后顺序不变；filter 在 map 里裁剪。
     */
    override fun observeHistory(
        filter: HistoryFilter,
        limit: Int,
    ): Flow<List<OperationHistoryWithContact>> {
        return combine(
            historyDao.observeRecent(limit = limit),
            contactCacheDao.getAllContacts(),
        ) { historyList, contacts ->
            val contactMap: Map<Long, String> = contacts.associate { it.id to it.name }
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
                            val status = item.history.opStatus
                            status == "CONFLICT" || status == "FAILED_PERMANENT"
                        }
                    }
                }
        }.also { flow ->
            Log.d(tag, "observeHistory: filter=$filter limit=$limit (只读日志)")
        }
    }

    private companion object {
        const val TAG = "OpHistoryRepo"
    }
}
