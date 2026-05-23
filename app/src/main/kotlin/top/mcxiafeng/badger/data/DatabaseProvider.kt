package top.mcxiafeng.badger.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dagger.hilt.android.EntryPointAccessors
import top.mcxiafeng.badger.BadgerApplication
import top.mcxiafeng.badger.di.DatabaseEntryPoint

@Composable
fun rememberContactRepository(): ContactRepository {
    val entryPoint = EntryPointAccessors.fromApplication(
        BadgerApplication.getInstance(),
        DatabaseEntryPoint::class.java
    )
    val database = remember { entryPoint.database() }
    return remember {
        ContactRepositoryImpl(
            contactDao = database.contactDao(),
            contactFieldDao = database.contactFieldDao(),
            customFieldDao = database.customFieldDao(),
            contactFieldValueDao = database.contactFieldValueDao(),
            scanResultDao = database.scanResultDao(),
            collectionDao = database.cardCollectionDao(),
            userProfileDao = database.userProfileDao()
        )
    }
}
