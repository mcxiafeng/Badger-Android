package top.mcxiafeng.badger.di

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object CoroutineDispatchersModule {
    @Provides
    @Singleton
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher {
        Log.d("CoroutineDispatchersModuleTester", "提供 IO 调度器")
        return Dispatchers.IO
    }
}
