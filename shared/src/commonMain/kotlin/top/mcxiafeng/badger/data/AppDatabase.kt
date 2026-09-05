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

/**
 * [KMP K08-B] AppDatabase 本体迁 commonMain（@Database + DAO 抽象 + 迁移链清单）。
 * 平台构造（Android filesDir builder + seed/ensureDefaults callback + 破坏迁移备份）
 * 在 androidMain `AppDatabaseHost.build(context)`；iOS 侧 K16 随 iosApp 接
 * RoomDatabaseConstructor 模式（bundled driver + NSDocumentDirectory）。
 */
@Database(
    entities = [
        // V2 cache 表(主路径)
        ContactCacheEntity::class,
        ContactFieldCacheEntity::class,
        ContactFieldValueCacheEntity::class,
        ContactPlatformCacheEntity::class,
        TagCacheEntity::class,
        CardCollectionCacheEntity::class,
        UserProfileCacheEntity::class,
        ContactTagCacheEntity::class,
        // [Phase 3] sync 游标
        SyncCursorEntity::class,
        // [Phase 2] Person Profile 子表
        PersonProfileCacheEntity::class,
        // [Phase 3 Task #30] custom_fields V2 cache 表
        CustomFieldCacheEntity::class,
        // [Phase 4 Task #20] 名片夹成员关联 V2 cache 表
        CollectionMemberCacheEntity::class,
        // V2 queue 表（退役为本地只读日志）
        OperationHistoryEntity::class,
        // [Phase 2] 通用 Outbox（规格 §3.1，替代 pending_person_updates 旁路表）
        OutboxEntity::class,
    ],
    version = 17,
    exportSchema = true
)
// [KMP K13b] iOS KSP 校验要求：非 Android target 的 @Database 必须显式 ConstructedBy
// （K07 备注④的 expect object : RoomDatabaseConstructor<T> 模式；此前 iOS KSP 被
// 跳过掩盖了该校验，Kotlin 2.4 升级后按 target 跑 KSP 才暴露）
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {


    // [A3] V2 cache DAO(主路径)
    abstract fun contactCacheDao(): top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
    abstract fun contactFieldCacheDao(): top.mcxiafeng.badger.data.cache.dao.ContactFieldCacheDao
    abstract fun contactFieldValueCacheDao(): top.mcxiafeng.badger.data.cache.dao.ContactFieldValueCacheDao
    abstract fun contactPlatformCacheDao(): top.mcxiafeng.badger.data.cache.dao.ContactPlatformCacheDao
    abstract fun tagCacheDao(): top.mcxiafeng.badger.data.cache.dao.TagCacheDao
    abstract fun cardCollectionCacheDao(): top.mcxiafeng.badger.data.cache.dao.CardCollectionCacheDao
    abstract fun userProfileCacheDao(): top.mcxiafeng.badger.data.cache.dao.UserProfileCacheDao
    abstract fun contactTagCacheDao(): top.mcxiafeng.badger.data.cache.dao.ContactTagCacheDao
    // [Phase 3 Task #30] custom_fields V2 cache DAO
    abstract fun customFieldCacheDao(): top.mcxiafeng.badger.data.cache.dao.CustomFieldCacheDao

    // [Phase 4 Task #20] 名片夹成员关联 V2 cache DAO
    abstract fun collectionMemberCacheDao(): top.mcxiafeng.badger.data.cache.dao.CollectionMemberCacheDao

    // [Phase 3] sync 游标 DAO
    abstract fun syncCursorDao(): top.mcxiafeng.badger.data.cache.dao.SyncCursorDao

    // [Phase 2] Person Profile 子表 DAO
    abstract fun personProfileCacheDao(): top.mcxiafeng.badger.data.cache.dao.PersonProfileCacheDao

    // [V2-P2] queue DAO(历史只读日志)
    abstract fun operationHistoryDao(): OperationHistoryDao

    // [Phase 2] 通用 Outbox DAO
    abstract fun outboxDao(): OutboxDao

    companion object {
        const val DB_NAME = "badger_database"

        /** [KMP K07] 全量迁移链（build 与 MigrationChainTest 共用，防测试漏链）。 */
        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
            MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
            MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
            MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
        )
    }
}

/** KMP 实例工厂：iOS 侧由 Room K/N 合成 actual，无需手写。 */
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>
