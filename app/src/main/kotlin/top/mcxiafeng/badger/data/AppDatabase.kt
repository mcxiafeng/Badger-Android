package top.mcxiafeng.badger.data

import androidx.room.Database
import androidx.room.RoomDatabase
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity
import top.mcxiafeng.badger.data.migrations.MIGRATION_1_2
import top.mcxiafeng.badger.data.migrations.MIGRATION_2_3
import top.mcxiafeng.badger.data.migrations.MIGRATION_3_4
import top.mcxiafeng.badger.data.migrations.MIGRATION_4_5
import top.mcxiafeng.badger.data.migrations.MIGRATION_5_6
import top.mcxiafeng.badger.data.migrations.MIGRATION_6_7
import top.mcxiafeng.badger.data.migrations.MIGRATION_7_8
import top.mcxiafeng.badger.data.migrations.MIGRATION_8_9
import top.mcxiafeng.badger.data.migrations.MIGRATION_9_10
import top.mcxiafeng.badger.data.migrations.MIGRATION_10_11
import top.mcxiafeng.badger.data.migrations.MIGRATION_11_12
import top.mcxiafeng.badger.data.migrations.MIGRATION_12_13
import top.mcxiafeng.badger.data.migrations.MIGRATION_13_14
import top.mcxiafeng.badger.data.migrations.MIGRATION_14_15
import top.mcxiafeng.badger.data.migrations.MIGRATION_15_16
import top.mcxiafeng.badger.data.migrations.MIGRATION_16_17
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
import top.mcxiafeng.badger.data.queue.OperationHistoryDao
import top.mcxiafeng.badger.data.queue.OperationHistoryEntity
import top.mcxiafeng.badger.data.queue.OutboxDao
import top.mcxiafeng.badger.data.queue.OutboxEntity

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
        // [§14.2] 提取出 build 工厂,让 Koin module 可以单行构造。对应原 Hilt
        // DatabaseModule.provideDatabase,但把 callback 内的"seed/ensureDefaults
        // / backupDatabaseBeforeDestructive"全部下放到这里,Koin 端只需一行。
        fun build(context: android.content.Context): AppDatabase {
            return androidx.room.Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "badger_database"
            )
                .addCallback(object : androidx.room.RoomDatabase.Callback() {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onCreate(db)
                        seedDefaults(db)
                    }

                    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onOpen(db)
                        ensureDefaults(db)
                    }

                    override fun onDestructiveMigration(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onDestructiveMigration(db)
                        backupDatabaseBeforeDestructive(context)
                    }
                })
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                )
                // [§14.7 / §15.4 #17] 迁移链 MIGRATION_1_2~5_6 已完整覆盖 1→6;
                // 移除 fallbackToDestructiveMigration() 以免版本错位时静默丢数据。
                // 万一未来真的发生迁移缺失,Room 会抛 IllegalStateException,crashlytics
                // 上报后人工补 Migration,而不是悄悄抹掉用户的联系人。
                .build()
        }

        private fun backupDatabaseBeforeDestructive(context: android.content.Context) {
            try {
                val dbDir = context.getDatabasePath("badger_database").parentFile ?: return
                val src = java.io.File(dbDir, "badger_database")
                if (!src.exists()) {
                    android.util.Log.w(TAG, "backupDatabaseBeforeDestructive: source db not found, skip")
                    return
                }
                val dumpDir = java.io.File(dbDir, "dump").apply { mkdirs() }
                val dst = java.io.File(dumpDir, "badger_${System.currentTimeMillis()}.db")
                src.copyTo(dst, overwrite = false)
                android.util.Log.e(TAG, "backupDatabaseBeforeDestructive: copied ${src.length()} bytes to ${dst.absolutePath}")
                android.util.Log.e(TAG, "  → adb pull ${dst.absolutePath} 把损坏前的 db 拿出来")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "backupDatabaseBeforeDestructive failed", e)
            }
        }

        private fun seedDefaults(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            // [Phase 3] contact_fields 已删，改用 contact_fields_cache
            top.mcxiafeng.badger.ocr.ALL_FIELDS.forEachIndexed { index, def ->
                db.execSQL(
                    "INSERT OR REPLACE INTO contact_fields_cache (fieldName, fieldKey, icon, sortOrder, isSystem, isEnabled, createTime) VALUES (?, ?, ?, ?, 1, 1, ?)",
                    arrayOf<Any>(def.displayName, def.fieldKey, def.fieldKey ?: "", index + 1, now)
                )
            }
            db.execSQL(
                "INSERT OR REPLACE INTO user_profile_cache (id, name, bio, platformsJson, updateTime) VALUES (1, '用户', NULL, '{}', ?)",
                arrayOf<Any>(now)
            )
            db.execSQL(
                "INSERT OR REPLACE INTO card_collections_cache (id, name, description, personMembers, createTime, isLocalOnly) VALUES (1, '默认名片夹', '所有新扫描的联系人将添加到此处', '[]', ?, 1)",
                arrayOf<Any>(now)
            )
        }

        private fun ensureDefaults(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            // [Phase 3] contact_fields 已删，改用 contact_fields_cache
            top.mcxiafeng.badger.ocr.ALL_FIELDS.forEachIndexed { index, def ->
                val cursor = db.query("SELECT id FROM contact_fields_cache WHERE fieldKey = ?", arrayOf(def.fieldKey))
                val exists = cursor.moveToFirst()
                cursor.close()
                if (!exists) {
                    db.execSQL(
                        "INSERT INTO contact_fields_cache (fieldName, fieldKey, icon, sortOrder, isSystem, isEnabled, createTime) VALUES (?, ?, ?, ?, 1, 1, ?)",
                        arrayOf<Any>(def.displayName, def.fieldKey, def.fieldKey ?: "", index + 1, now)
                    )
                }
            }
            val profileCursor = db.query("SELECT id FROM user_profile_cache WHERE id = 1")
            val profileExists = profileCursor.moveToFirst()
            profileCursor.close()
            if (!profileExists) {
                db.execSQL(
                    "INSERT INTO user_profile_cache (id, name, bio, platformsJson, updateTime) VALUES (1, '用户', NULL, '{}', ?)",
                    arrayOf<Any>(now)
                )
            }
        }

        private const val TAG = "DatabaseModule"
    }
}
