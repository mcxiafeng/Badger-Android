package top.mcxiafeng.badger.di

import coil3.ImageLoader
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DatabaseEntryPoint {
    fun okHttpClient(): OkHttpClient
    fun imageLoader(): ImageLoader
}
