package top.mcxiafeng.badger.di

import android.content.Context
import android.util.Log
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.CardCollectionDao
import top.mcxiafeng.badger.data.ContactDao
import top.mcxiafeng.badger.data.ContactFieldDao
import top.mcxiafeng.badger.data.ContactFieldValueDao
import top.mcxiafeng.badger.data.ContactFtsDao
import top.mcxiafeng.badger.data.ContactPlatformDao
import top.mcxiafeng.badger.data.ContactTagDao
import top.mcxiafeng.badger.data.CustomFieldDao
import top.mcxiafeng.badger.data.ScanResultDao
import top.mcxiafeng.badger.data.SyncStateDao
import top.mcxiafeng.badger.data.TagDao
import top.mcxiafeng.badger.data.TagFtsDao
import top.mcxiafeng.badger.data.UserProfileDao
import top.mcxiafeng.badger.data.account.AccountContext
import top.mcxiafeng.badger.data.account.AccountDatabaseProvider
import top.mcxiafeng.badger.data.account.AccountScope
import top.mcxiafeng.badger.data.account.AccountScopedContext
import top.mcxiafeng.badger.data.account.AccountStore
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.data.repository.TagRepositoryImpl

@Module
@InstallIn(ViewModelComponent::class)
object DatabaseModule {
    @Provides
    @ViewModelScoped
    fun provideAccountScope(store: AccountStore): AccountScope {
        Log.d("DatabaseModuleTester", "Capturing fixed ViewModel account space")
        return store.current()
    }

    @Provides
    @ViewModelScoped
    @AccountScopedContext
    fun provideAccountContext(@ApplicationContext context: Context, scope: AccountScope): Context =
        AccountContext(context, scope)

    @Provides
    @ViewModelScoped
    fun provideDatabase(provider: AccountDatabaseProvider, scope: AccountScope): AppDatabase = provider.open(scope)

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
    @Provides fun provideSyncStateDao(db: AppDatabase): SyncStateDao = db.syncStateDao()
}

@Module
@InstallIn(ViewModelComponent::class)
abstract class RepositoryModule {
    @Binds
    @ViewModelScoped
    abstract fun bindTagRepository(impl: TagRepositoryImpl): TagRepository
}
