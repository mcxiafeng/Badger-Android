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
import top.mcxiafeng.badger.data.CardCollectionDao
import top.mcxiafeng.badger.data.ContactDao
import top.mcxiafeng.badger.data.ContactFieldDao
import top.mcxiafeng.badger.data.ContactFieldValueDao
import top.mcxiafeng.badger.data.ContactFtsDao
import top.mcxiafeng.badger.data.ContactPlatformDao
import top.mcxiafeng.badger.data.CustomFieldDao
import top.mcxiafeng.badger.data.MIGRATION_1_2
import top.mcxiafeng.badger.data.MIGRATION_2_3
import top.mcxiafeng.badger.data.ScanResultDao
import top.mcxiafeng.badger.data.UserProfileDao
import top.mcxiafeng.badger.ocr.ALL_FIELDS
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
            })
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
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
            "INSERT OR REPLACE INTO user_profile (id, name, bio, updateTime) VALUES (1, '用户', NULL, ?)",
            arrayOf<Any>(now)
        )
        db.execSQL(
            "INSERT OR REPLACE INTO card_collections (id, name, description, createTime) VALUES (1, '默认名片夹', '所有新扫描的联系人将添加到此处', ?)",
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
        val profileCursor = db.query("SELECT id FROM user_profile WHERE id = 1")
        val profileExists = profileCursor.moveToFirst()
        profileCursor.close()
        if (!profileExists) {
            db.execSQL(
                "INSERT INTO user_profile (id, name, bio, updateTime) VALUES (1, '用户', NULL, ?)",
                arrayOf<Any>(now)
            )
            Log.d("Tester", "ensureDefaults: inserted default profile")
        }
    }

    /**
     * 清理老版本 MIGRATION_2_3 创建的 contacts_ai/ad/au FTS 同步触发器。
     *
     * 旧迁移使用 FTS4 `'delete'` 控制命令手写了 3 个同步触发器，
     * 但 Room 通过 @Fts4(contentEntity = Contact::class) 已经自动生成了
     * 4 个 room_fts_content_sync_* 触发器。两套触发器在 DELETE 行时并发执行，
     * 迁移版的 `contacts_ad` 触发 FTS4 `'delete'` 命令在内容表行已删除的情况下
     * 报 SQLITE_ERROR，导致 deleteContact 崩溃。
     *
     * 保留 Room 自动生成的触发器即可维护 FTS 索引，所以这里直接 DROP 掉手写的。
     */
    private fun dropLegacyFtsTriggers(db: SupportSQLiteDatabase) {
        val legacyTriggers = listOf("contacts_ai", "contacts_ad", "contacts_au")
        legacyTriggers.forEach { trigger ->
            db.execSQL("DROP TRIGGER IF EXISTS `$trigger`")
        }
        Log.d("Tester", "dropLegacyFtsTriggers: dropped legacy FTS sync triggers (conflicted with Room's auto-generated triggers)")
    }

    @Provides fun provideContactDao(db: AppDatabase): ContactDao = db.contactDao()
    @Provides fun provideContactFieldDao(db: AppDatabase): ContactFieldDao = db.contactFieldDao()
    @Provides fun provideCustomFieldDao(db: AppDatabase): CustomFieldDao = db.customFieldDao()
    @Provides fun provideContactFieldValueDao(db: AppDatabase): ContactFieldValueDao = db.contactFieldValueDao()
    @Provides fun provideScanResultDao(db: AppDatabase): ScanResultDao = db.scanResultDao()
    @Provides fun provideCardCollectionDao(db: AppDatabase): CardCollectionDao = db.cardCollectionDao()
    @Provides fun provideUserProfileDao(db: AppDatabase): UserProfileDao = db.userProfileDao()
    @Provides fun provideContactPlatformDao(db: AppDatabase): ContactPlatformDao = db.contactPlatformDao()
    @Provides fun provideContactFtsDao(db: AppDatabase): ContactFtsDao = db.contactFtsDao()
}
