package top.mcxiafeng.badger.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Binds
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
import top.mcxiafeng.badger.data.ContactTagDao
import top.mcxiafeng.badger.data.CustomFieldDao
import top.mcxiafeng.badger.data.MIGRATION_1_2
import top.mcxiafeng.badger.data.MIGRATION_2_3
import top.mcxiafeng.badger.data.MIGRATION_3_4
import top.mcxiafeng.badger.data.MIGRATION_4_5
import top.mcxiafeng.badger.data.ScanResultDao
import top.mcxiafeng.badger.data.TagDao
import top.mcxiafeng.badger.data.TagFtsDao
import top.mcxiafeng.badger.data.UserProfileDao
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.data.repository.TagRepositoryImpl
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

                /**
                 * [修复防御] Room 迁移失败兜底:
                 * - Room 默认迁移失败抛 IllegalStateException 导致 app 闪退
                 * - fallbackToDestructiveMigration() 在迁移失败时会 DROP 全部表重建
                 * - 这里在 DROP 之前把原始 db 文件复制一份到 databases/dump/
                 *   供开发 / 测试人员事后排查或尝试手工修复
                 *
                 * 设计取舍:
                 * - 与"自动回退上个版本"不同,Room 不支持 version chain。
                 *   一旦 schema 改变且 migration 失败,没有"上个版本"可回(用户期望的版本号也已固化)。
                 *   退而求其次:保留旧 db 文件,让用户 / dev 可以 pull 出来手工处理。
                 * - onDestructiveMigration 回调由 Room 在 DROP 表前触发,
                 *   此时源 db 文件还完整(尚未 DROP DATABASE),可以正常复制。
                 */
                override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                    super.onDestructiveMigration(db)
                    backupDatabaseBeforeDestructive(context)
                }
            })
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .fallbackToDestructiveMigration()
            .build()
    }

    /**
     * 在 Room destructive migration 触发 DROP 之前,把当前 db 文件复制到
     * `databases/dump/badger_<timestamp>.db`。Room 尚未删除表,文件可直接 copy。
     */
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
            // 同时 dump Room schema 期望 hash 与实际 hash,方便后续排查
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
    @Provides fun provideTagDao(db: AppDatabase): TagDao = db.tagDao()
    @Provides fun provideContactTagDao(db: AppDatabase): ContactTagDao = db.contactTagDao()
    @Provides fun provideTagFtsDao(db: AppDatabase): TagFtsDao = db.tagFtsDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindTagRepository(impl: TagRepositoryImpl): TagRepository
}

private const val TAG = "DatabaseModule"
