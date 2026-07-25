package top.mcxiafeng.badger.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.ContactFieldDao
import top.mcxiafeng.badger.data.ContactFieldValueDao
import top.mcxiafeng.badger.data.ContactPlatformDao
import top.mcxiafeng.badger.data.CustomFieldDao
import top.mcxiafeng.badger.data.MIGRATION_1_2
import top.mcxiafeng.badger.data.MIGRATION_2_3
import top.mcxiafeng.badger.data.MIGRATION_3_4
import top.mcxiafeng.badger.data.MIGRATION_4_5
import top.mcxiafeng.badger.data.MIGRATION_5_6
import top.mcxiafeng.badger.data.ScanResultDao
import top.mcxiafeng.badger.data.cache.dao.CardCollectionCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactFieldCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactFieldValueCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactPlatformCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactTagCacheDao
import top.mcxiafeng.badger.data.cache.dao.TagCacheDao
import top.mcxiafeng.badger.data.cache.dao.UserProfileCacheDao
import top.mcxiafeng.badger.data.queue.OperationHistoryDao
import top.mcxiafeng.badger.data.queue.PendingUploadDao
import top.mcxiafeng.badger.ocr.ALL_FIELDS
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "badger_database"
        )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    seedDefaults(db)
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    ensureDefaults(db)
                    dropLegacyFtsTriggers(db)
                }

                override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                    super.onDestructiveMigration(db)
                    backupDatabaseBeforeDestructive(context)
                }
            })
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .fallbackToDestructiveMigration()
            .build()
    }

    private fun backupDatabaseBeforeDestructive(context: Context) {
        try {
            val dbDir = context.getDatabasePath("badger_database").parentFile ?: return
            val src = File(dbDir, "badger_database")
            if (!src.exists()) {
                Log.w(TAG, "backupDatabaseBeforeDestructive: source db not found, skip")
                return
            }
            val dumpDir = File(dbDir, "dump").apply { mkdirs() }
            val dst = File(dumpDir, "badger_${System.currentTimeMillis()}.db")
            src.copyTo(dst, overwrite = false)
            Log.e(TAG, "backupDatabaseBeforeDestructive: copied ${src.length()} bytes to ${dst.absolutePath}")
            Log.e(TAG, "  → adb pull ${dst.absolutePath} 把损坏前的 db 拿出来")
        } catch (e: Exception) {
            Log.e(TAG, "backupDatabaseBeforeDestructive failed", e)
        }
    }

    private fun seedDefaults(db: SupportSQLiteDatabase) {
        Log.d("Tester", "seedDefaults: seeding default fields and profile")
        val now = System.currentTimeMillis()
        ALL_FIELDS.forEachIndexed { index, def ->
            db.execSQL(
                "INSERT OR REPLACE INTO contact_fields (fieldName, fieldKey, icon, sortOrder, isSystem, isEnabled, createTime) VALUES (?, ?, ?, ?, 1, 1, ?)",
                arrayOf<Any>(def.displayName, def.fieldKey, def.fieldKey ?: "", index + 1, now)
            )
        }
        db.execSQL(
            "INSERT OR REPLACE INTO user_profile_cache (id, name, bio, platformsJson, updateTime, serverVersion) VALUES (1, '用户', NULL, '{}', ?, 0)",
            arrayOf<Any>(now)
        )
        db.execSQL(
            "INSERT OR REPLACE INTO card_collections_cache (id, name, description, createTime, serverVersion, isLocalOnly) VALUES (1, '默认名片夹', '所有新扫描的联系人将添加到此处', ?, 0, 1)",
            arrayOf<Any>(now)
        )
        Log.d("Tester", "seedDefaults: done")
    }

    private fun ensureDefaults(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        ALL_FIELDS.forEachIndexed { index, def ->
            val cursor = db.query("SELECT id FROM contact_fields WHERE fieldKey = ?", arrayOf(def.fieldKey))
            val exists = cursor.moveToFirst()
            cursor.close()
            if (!exists) {
                db.execSQL(
                    "INSERT INTO contact_fields (fieldName, fieldKey, icon, sortOrder, isSystem, isEnabled, createTime) VALUES (?, ?, ?, ?, 1, 1, ?)",
                    arrayOf<Any>(def.displayName, def.fieldKey, def.fieldKey ?: "", index + 1, now)
                )
                Log.d("Tester", "ensureDefaults: inserted missing field ${def.fieldKey}")
            }
        }
        val profileCursor = db.query("SELECT id FROM user_profile_cache WHERE id = 1")
        val profileExists = profileCursor.moveToFirst()
        profileCursor.close()
        if (!profileExists) {
            db.execSQL(
                "INSERT INTO user_profile_cache (id, name, bio, platformsJson, updateTime, serverVersion) VALUES (1, '用户', NULL, '{}', ?, 0)",
                arrayOf<Any>(now)
            )
            Log.d("Tester", "ensureDefaults: inserted default profile")
        }
    }

    private fun dropLegacyFtsTriggers(db: SupportSQLiteDatabase) {
        val legacyTriggers = listOf("contacts_ai", "contacts_ad", "contacts_au")
        legacyTriggers.forEach { trigger ->
            db.execSQL("DROP TRIGGER IF EXISTS `$trigger`")
        }
        Log.d("Tester", "dropLegacyFtsTriggers: dropped legacy FTS sync triggers (conflicted with Room's auto-generated triggers)")
    }

    @Provides fun provideContactFieldDao(db: AppDatabase): ContactFieldDao = db.contactFieldDao()
    @Provides fun provideCustomFieldDao(db: AppDatabase): CustomFieldDao = db.customFieldDao()
    @Provides fun provideContactFieldValueDao(db: AppDatabase): ContactFieldValueDao = db.contactFieldValueDao()
    @Provides fun provideScanResultDao(db: AppDatabase): ScanResultDao = db.scanResultDao()
    @Provides fun provideContactPlatformDao(db: AppDatabase): ContactPlatformDao = db.contactPlatformDao()

    @Provides fun provideContactCacheDao(db: AppDatabase): ContactCacheDao = db.contactCacheDao()
    @Provides fun provideContactFieldCacheDao(db: AppDatabase): ContactFieldCacheDao = db.contactFieldCacheDao()
    @Provides fun provideContactFieldValueCacheDao(db: AppDatabase): ContactFieldValueCacheDao = db.contactFieldValueCacheDao()
    @Provides fun provideContactPlatformCacheDao(db: AppDatabase): ContactPlatformCacheDao = db.contactPlatformCacheDao()
    @Provides fun provideTagCacheDao(db: AppDatabase): TagCacheDao = db.tagCacheDao()
    @Provides fun provideCardCollectionCacheDao(db: AppDatabase): CardCollectionCacheDao = db.cardCollectionCacheDao()
    @Provides fun provideUserProfileCacheDao(db: AppDatabase): UserProfileCacheDao = db.userProfileCacheDao()
    @Provides fun provideContactTagCacheDao(db: AppDatabase): ContactTagCacheDao = db.contactTagCacheDao()

    // [V2-P2] queue DAO:乐观写 + 历史(Sync Worker 在 P4 阶段消费 PendingUploadDao)
    @Provides fun providePendingUploadDao(db: AppDatabase): PendingUploadDao = db.pendingUploadDao()
    @Provides fun provideOperationHistoryDao(db: AppDatabase): OperationHistoryDao = db.operationHistoryDao()
}

private const val TAG = "DatabaseModule"