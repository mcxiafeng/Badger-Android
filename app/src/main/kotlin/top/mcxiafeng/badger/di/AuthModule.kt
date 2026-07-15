package top.mcxiafeng.badger.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import top.mcxiafeng.badger.data.repository.ServerApiFactory
import javax.inject.Singleton

/**
 * Auth-related singletons. The factory reference is filled in at app-init
 * time (BadgerApplication) so [UserAuthRepository] and the OkHttp
 * interceptor can both pull from the same source of truth.
 */
@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    @Provides
    @Singleton
    fun provideServerApiFactory(): ServerApiFactory = ServerApiFactory()
}
