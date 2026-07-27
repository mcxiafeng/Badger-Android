package top.mcxiafeng.badger.pages.settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.ImportResult
import top.mcxiafeng.badger.data.exportToJson
import top.mcxiafeng.badger.data.importFromJson
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.repository.ServerApiFactory
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.network.ServerApi

/**
 * Backs the cloud-sync settings page. All actual I/O goes through
 * [ServerApi] (which uses the server's `/v1/backups`); the in-app
 * [ContactRepository] / [FieldRepository] etc. are only used to
 * convert the local Room graph into the export envelope.
 *
 * [§14.2] 移除 `@HiltViewModel` 与 `@Inject` —— Koin `inject()` 字段注入。
 */
class CloudSyncSettingsViewModel : ViewModel() {
    val contactRepository: ContactRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    val fieldRepository: FieldRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    val collectionRepository: CollectionRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    val tagRepository: TagRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val serverApiFactory: ServerApiFactory = top.mcxiafeng.badger.di.KoinComponentBy.get()
    init {
        Log.d("Tester", "CloudSyncSettingsViewModel initialized")
    }

    suspend fun testConnection(context: Context): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) { serverApiFactory.get().listBackups() }
    }

    suspend fun backup(context: Context): Result<Unit> = runCatching {
        val envelope = withContext(Dispatchers.IO) {
            val collections = collectionRepository.getAllCollectionsOnce()
            exportToJson(
                contactRepository = contactRepository,
                fieldRepository = fieldRepository,
                collectionRepository = collectionRepository,
                tagRepository = tagRepository,
                collectionIds = collections.map { it.id },
            )
        }
        withContext(Dispatchers.IO) { serverApiFactory.get().uploadBackup(envelope) }
    }

    suspend fun restore(context: Context): Result<ImportResult> {
        // The server only stores the most-recent backup envelope. We
        // delegate the actual Room-import step to the local data layer.
        // (downloadAndImport is a thin wrapper that fetches bytes, parses
        //  them via the local import function, and applies them.)
        return runCatching {
            withContext(Dispatchers.IO) {
                val backups = serverApiFactory.get().listBackups()
                if (backups.isEmpty()) error("no backups on server")
                val latest = backups.first()
                val bytes = serverApiFactory.get().downloadBackup(latest.id)
                importFromJson(
                    contactRepository = contactRepository,
                    fieldRepository = fieldRepository,
                    collectionRepository = collectionRepository,
                    tagRepository = tagRepository,
                    json = String(bytes, Charsets.UTF_8),
                )
            }
        }
    }
}
