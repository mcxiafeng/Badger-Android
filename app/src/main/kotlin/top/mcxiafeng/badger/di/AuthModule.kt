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

    /**
     * [V2-P4] 把 [top.mcxiafeng.badger.network.ServerApi] 暴露给 Hilt,供
     * [top.mcxiafeng.badger.sync.SyncWorkerEntryPoint] / [top.mcxiafeng.badger.sync.PendingUploadExecutor] 注入。
     *
     * [修复防御]:ServerApi 由 NetworkModule.provideOkHttpClient 在初始化时构造并通过
     * ServerApiFactory.install(...) 装入,这里不能直接 new 一个 — 否则会出现两套实例
     * (一份走 tokenHolder,一份独立) 导致 401 拦截器拿不到 token。这里二次校验:若
     * factory 还未 install(单元测试场景),抛错让 caller 走 mockk,避免 silent fallback。
     */
    @Provides
    @Singleton
    fun provideServerApiForWorker(factory: ServerApiFactory): top.mcxiafeng.badger.network.ServerApi =
        factory.get()
}
