package top.mcxiafeng.badger.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dagger.hilt.android.EntryPointAccessors
import top.mcxiafeng.badger.BadgerApplication
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.CollectionRepositoryImpl
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.ContactRepositoryImpl
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.repository.FieldRepositoryImpl
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepositoryImpl
import top.mcxiafeng.badger.di.DatabaseEntryPoint

@Composable
fun rememberContactRepository(): ContactRepository {
    val entryPoint = EntryPointAccessors.fromApplication(
        BadgerApplication.getInstance(),
        DatabaseEntryPoint::class.java
    )
    return remember { entryPoint.contactRepository() }
}

@Composable
fun rememberFieldRepository(): FieldRepository {
    val entryPoint = EntryPointAccessors.fromApplication(
        BadgerApplication.getInstance(),
        DatabaseEntryPoint::class.java
    )
    return remember { entryPoint.fieldRepository() }
}

@Composable
fun rememberCollectionRepository(): CollectionRepository {
    val entryPoint = EntryPointAccessors.fromApplication(
        BadgerApplication.getInstance(),
        DatabaseEntryPoint::class.java
    )
    return remember { entryPoint.collectionRepository() }
}

@Composable
fun rememberUserProfileRepository(): UserProfileRepository {
    val entryPoint = EntryPointAccessors.fromApplication(
        BadgerApplication.getInstance(),
        DatabaseEntryPoint::class.java
    )
    return remember { entryPoint.userProfileRepository() }
}
