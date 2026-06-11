package top.mcxiafeng.badger.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import coil3.ImageLoader
import okhttp3.OkHttpClient
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepository

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DatabaseEntryPoint {
    fun database(): AppDatabase
    fun okHttpClient(): OkHttpClient
    @WebDav
    fun webDavOkHttpClient(): OkHttpClient
    fun contactRepository(): ContactRepository
    fun fieldRepository(): FieldRepository
    fun collectionRepository(): CollectionRepository
    fun imageLoader(): ImageLoader
    fun userProfileRepository(): UserProfileRepository
}
