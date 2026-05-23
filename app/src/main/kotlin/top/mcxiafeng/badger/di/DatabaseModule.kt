package top.mcxiafeng.badger.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Cache
import okhttp3.OkHttpClient
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.CardCollection
import top.mcxiafeng.badger.data.CardCollectionDao
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactDao
import top.mcxiafeng.badger.data.ContactField
import top.mcxiafeng.badger.data.ContactFieldDao
import top.mcxiafeng.badger.data.ContactFieldValueDao
import top.mcxiafeng.badger.data.CustomFieldDao
import top.mcxiafeng.badger.data.ScanResultDao
import top.mcxiafeng.badger.data.UserProfile
import top.mcxiafeng.badger.data.UserProfileDao
import top.mcxiafeng.badger.ocr.ALL_FIELDS
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
                    applicationScope.launch {
                        seedDefaults(context)
                    }
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    applicationScope.launch {
                        ensureDefaults(context)
                    }
                }
            })
            .fallbackToDestructiveMigration(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val cacheDir = File(context.cacheDir, "http_cache")
        return OkHttpClient.Builder()
            .cache(Cache(cacheDir, 10L * 1024 * 1024))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    @Provides fun provideContactDao(db: AppDatabase): ContactDao = db.contactDao()
    @Provides fun provideContactFieldDao(db: AppDatabase): ContactFieldDao = db.contactFieldDao()
    @Provides fun provideCustomFieldDao(db: AppDatabase): CustomFieldDao = db.customFieldDao()
    @Provides fun provideContactFieldValueDao(db: AppDatabase): ContactFieldValueDao = db.contactFieldValueDao()
    @Provides fun provideScanResultDao(db: AppDatabase): ScanResultDao = db.scanResultDao()
    @Provides fun provideCardCollectionDao(db: AppDatabase): CardCollectionDao = db.cardCollectionDao()
    @Provides fun provideUserProfileDao(db: AppDatabase): UserProfileDao = db.userProfileDao()

    private suspend fun seedDefaults(context: Context) {
        val db = Room.databaseBuilder(
                context, AppDatabase::class.java, "badger_database"
            ).fallbackToDestructiveMigration(false).build()
        try {
            val fieldDao = db.contactFieldDao()
            ALL_FIELDS.forEachIndexed { index, def ->
                fieldDao.insertField(ContactField(
                    fieldName = def.displayName, fieldKey = def.fieldKey,
                    icon = def.fieldKey, sortOrder = index + 1, isSystem = true
                ))
            }
            db.userProfileDao().saveProfile(UserProfile(id = 1L, name = "用户", bio = null))
            val collectionDao = db.cardCollectionDao()
            if (collectionDao.getCollectionById(1L) == null) {
                collectionDao.insertCollection(CardCollection(id = 1L, name = "默认名片夹", description = "所有新扫描的联系人将添加到此处"))
            }
        } finally {
            db.close()
        }
    }

    private suspend fun ensureDefaults(context: Context) {
        val db = Room.databaseBuilder(
                context, AppDatabase::class.java, "badger_database"
            ).fallbackToDestructiveMigration(false).build()
        try {
            val fieldDao = db.contactFieldDao()
            ALL_FIELDS.forEachIndexed { index, def ->
                if (fieldDao.getFieldByKey(def.fieldKey) == null) {
                    fieldDao.insertField(ContactField(
                        fieldName = def.displayName, fieldKey = def.fieldKey,
                        icon = def.fieldKey, sortOrder = index + 1, isSystem = true
                    ))
                }
            }
            val profileDao = db.userProfileDao()
            if (profileDao.getProfileOnce() == null) {
                profileDao.saveProfile(UserProfile(id = 1L, name = "用户", bio = null))
            }
        } finally {
            db.close()
        }
    }

    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
}
