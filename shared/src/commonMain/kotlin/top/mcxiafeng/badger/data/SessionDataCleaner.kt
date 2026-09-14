package top.mcxiafeng.badger.data

import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity
import top.mcxiafeng.badger.shared.db.dbTransaction
import top.mcxiafeng.badger.shared.util.nowMs
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "SessionDataCleaner"

class SessionDataCleaner(private val database: AppDatabase) {

    suspend fun clearAllUserData() {
        BadgerLog.d(TAG, "clearAllUserData: start")
        database.dbTransaction {
            
            database.contactTagCacheDao().clearAll()
            database.contactPlatformCacheDao().clearAll()
            database.contactFieldValueCacheDao().clearAll()
            database.collectionMemberCacheDao().clearAll()
            database.personProfileCacheDao().clearAll()

            
            database.contactCacheDao().clearAll()
            database.tagCacheDao().clearAll()
            database.cardCollectionCacheDao().clearAll()
            database.userProfileCacheDao().clearAll()
            database.customFieldCacheDao().clearAll()

            
            database.syncCursorDao().clearAll()
            database.outboxDao().clearAll()
            database.operationHistoryDao().clearAll()

            
            val now = nowMs()
            database.userProfileCacheDao().saveProfile(
                UserProfileCacheEntity(name = "用户", updateTime = now)
            )
            database.cardCollectionCacheDao().insertCollection(
                CardCollectionCacheEntity(
                    id = 1,
                    name = "默认名片夹",
                    description = "所有新扫描的联系人将添加到此处",
                    personMembers = "[]",
                    createTime = now,
                    isLocalOnly = true,
                )
            )
        }
        BadgerLog.d(TAG, "clearAllUserData: done (13 tables cleared + 2 defaults re-seeded)")
    }
}
