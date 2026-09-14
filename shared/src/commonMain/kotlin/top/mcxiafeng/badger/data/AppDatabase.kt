package top.mcxiafeng.badger.data

import androidx.room.Database
import androidx.room.ConstructedBy
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity
import top.mcxiafeng.badger.data.cache.entity.CollectionMemberCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactFieldCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactFieldValueCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactTagCacheEntity
import top.mcxiafeng.badger.data.cache.entity.CustomFieldCacheEntity
import top.mcxiafeng.badger.data.cache.entity.PersonProfileCacheEntity
import top.mcxiafeng.badger.data.cache.entity.SyncCursorEntity
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity
import top.mcxiafeng.badger.data.migrations.MIGRATION_1_2
import top.mcxiafeng.badger.data.migrations.MIGRATION_10_11
import top.mcxiafeng.badger.data.migrations.MIGRATION_11_12
import top.mcxiafeng.badger.data.migrations.MIGRATION_12_13
import top.mcxiafeng.badger.data.migrations.MIGRATION_13_14
import top.mcxiafeng.badger.data.migrations.MIGRATION_14_15
import top.mcxiafeng.badger.data.migrations.MIGRATION_15_16
import top.mcxiafeng.badger.data.migrations.MIGRATION_16_17
import top.mcxiafeng.badger.data.migrations.MIGRATION_2_3
import top.mcxiafeng.badger.data.migrations.MIGRATION_3_4
import top.mcxiafeng.badger.data.migrations.MIGRATION_4_5
import top.mcxiafeng.badger.data.migrations.MIGRATION_5_6
import top.mcxiafeng.badger.data.migrations.MIGRATION_6_7
import top.mcxiafeng.badger.data.migrations.MIGRATION_7_8
import top.mcxiafeng.badger.data.migrations.MIGRATION_8_9
import top.mcxiafeng.badger.data.migrations.MIGRATION_9_10
import top.mcxiafeng.badger.data.queue.OperationHistoryDao
import top.mcxiafeng.badger.data.queue.OperationHistoryEntity
import top.mcxiafeng.badger.data.queue.OutboxDao
import top.mcxiafeng.badger.data.queue.OutboxEntity

@Database(
    entities = [
        
        ContactCacheEntity::class,
        ContactFieldCacheEntity::class,
        ContactFieldValueCacheEntity::class,
        ContactPlatformCacheEntity::class,
        TagCacheEntity::class,
        CardCollectionCacheEntity::class,
        UserProfileCacheEntity::class,
        ContactTagCacheEntity::class,
        
        SyncCursorEntity::class,
        
        PersonProfileCacheEntity::class,
        
        CustomFieldCacheEntity::class,
        
        CollectionMemberCacheEntity::class,
        
        OperationHistoryEntity::class,
        
        OutboxEntity::class,
    ],
    version = 17,
    exportSchema = true
)

@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {

    
    abstract fun contactCacheDao(): top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
    abstract fun contactFieldCacheDao(): top.mcxiafeng.badger.data.cache.dao.ContactFieldCacheDao
    abstract fun contactFieldValueCacheDao(): top.mcxiafeng.badger.data.cache.dao.ContactFieldValueCacheDao
    abstract fun contactPlatformCacheDao(): top.mcxiafeng.badger.data.cache.dao.ContactPlatformCacheDao
    abstract fun tagCacheDao(): top.mcxiafeng.badger.data.cache.dao.TagCacheDao
    abstract fun cardCollectionCacheDao(): top.mcxiafeng.badger.data.cache.dao.CardCollectionCacheDao
    abstract fun userProfileCacheDao(): top.mcxiafeng.badger.data.cache.dao.UserProfileCacheDao
    abstract fun contactTagCacheDao(): top.mcxiafeng.badger.data.cache.dao.ContactTagCacheDao
    
    abstract fun customFieldCacheDao(): top.mcxiafeng.badger.data.cache.dao.CustomFieldCacheDao

    
    abstract fun collectionMemberCacheDao(): top.mcxiafeng.badger.data.cache.dao.CollectionMemberCacheDao

    
    abstract fun syncCursorDao(): top.mcxiafeng.badger.data.cache.dao.SyncCursorDao

    
    abstract fun personProfileCacheDao(): top.mcxiafeng.badger.data.cache.dao.PersonProfileCacheDao

    
    abstract fun operationHistoryDao(): OperationHistoryDao

    
    abstract fun outboxDao(): OutboxDao

    companion object {
        const val DB_NAME = "badger_database"

        

        const val DB_VERSION = 17

        
        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
            MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
            MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
            MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
        )
    }
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>
