package top.mcxiafeng.badger.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import top.mcxiafeng.badger.data.AppDatabase

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DatabaseEntryPoint {
    fun database(): AppDatabase
    fun okHttpClient(): OkHttpClient
}
