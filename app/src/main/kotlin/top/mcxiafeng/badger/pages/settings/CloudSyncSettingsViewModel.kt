package top.mcxiafeng.badger.pages.settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import top.mcxiafeng.badger.data.ImportResult
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.network.CloudSyncManager
import javax.inject.Inject

@HiltViewModel
class CloudSyncSettingsViewModel @Inject constructor(
    val contactRepository: ContactRepository,
    val fieldRepository: FieldRepository,
    val collectionRepository: CollectionRepository,
    val tagRepository: TagRepository,
    private val cloudSyncManager: CloudSyncManager
) : ViewModel() {
    init {
        Log.d("Tester", "CloudSyncSettingsViewModel initialized")
    }

    suspend fun testConnection(context: Context): Result<Unit> =
        cloudSyncManager.testConnection(context)

    suspend fun backup(context: Context): Result<Unit> =
        cloudSyncManager.backup(context, contactRepository, fieldRepository, collectionRepository, tagRepository)

    suspend fun restore(context: Context): Result<ImportResult> =
        cloudSyncManager.restore(context, contactRepository, fieldRepository, collectionRepository, tagRepository)
}
