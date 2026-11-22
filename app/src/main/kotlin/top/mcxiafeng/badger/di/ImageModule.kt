package top.mcxiafeng.badger.di

import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import top.mcxiafeng.badger.data.account.AccountImageCacheInterceptor
import top.mcxiafeng.badger.data.sync.PrivateAssetFetcherFactory
import top.mcxiafeng.badger.data.sync.PrivateAssetImageInterceptor
import top.mcxiafeng.badger.data.sync.PrivateAssetKeyer
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ImageModule {
    private const val MEMORY_CACHE_BYTES = 32L * 1024 * 1024
    private const val DISK_CACHE_BYTES = 100L * 1024 * 1024

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        privateAssetInterceptor: PrivateAssetImageInterceptor,
        privateAssetFetcher: PrivateAssetFetcherFactory,
    ): ImageLoader {
        Log.d("ImageModuleTester", "创建图片加载器 memoryBytes=$MEMORY_CACHE_BYTES diskBytes=$DISK_CACHE_BYTES")
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(MEMORY_CACHE_BYTES)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(DISK_CACHE_BYTES)
                    .build()
            }
            .components {
                add(AccountImageCacheInterceptor())
                add(privateAssetInterceptor)
                add(PrivateAssetKeyer())
                add(privateAssetFetcher)
                add(OkHttpNetworkFetcherFactory(okHttpClient))
            }
            .crossfade(true)
            .build()
    }
}
