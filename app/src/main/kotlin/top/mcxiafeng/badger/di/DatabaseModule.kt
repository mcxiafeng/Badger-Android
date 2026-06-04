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
import top.mcxiafeng.badger.data.CustomFieldDao
import top.mcxiafeng.badger.data.MIGRATION_1_2
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
                }
            })
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigrationOnDowngrade(true)
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
            "INSERT OR REPLACE INTO user_profile (id, name, bio) VALUES (1, '用户', NULL)"
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
            db.execSQL("INSERT INTO user_profile (id, name, bio) VALUES (1, '用户', NULL)")
            Log.d("Tester", "ensureDefaults: inserted default profile")
        }
    }

    @Provides fun provideContactDao(db: AppDatabase): ContactDao = db.contactDao()
    @Provides fun provideContactFieldDao(db: AppDatabase): ContactFieldDao = db.contactFieldDao()
    @Provides fun provideCustomFieldDao(db: AppDatabase): CustomFieldDao = db.customFieldDao()
    @Provides fun provideContactFieldValueDao(db: AppDatabase): ContactFieldValueDao = db.contactFieldValueDao()
    @Provides fun provideScanResultDao(db: AppDatabase): ScanResultDao = db.scanResultDao()
    @Provides fun provideCardCollectionDao(db: AppDatabase): CardCollectionDao = db.cardCollectionDao()
    @Provides fun provideUserProfileDao(db: AppDatabase): UserProfileDao = db.userProfileDao()
}
