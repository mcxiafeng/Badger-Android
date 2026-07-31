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
 *
 * [§16] envelope size guard:服务端 spec §0.2 限定单次 envelope ≤ 4 MiB,
 * 在 upload 之前做客户端校验,避免被服务端 4xx 拒收后 UI 看到无差别错误。
 */
class CloudSyncSettingsViewModel : ViewModel() {
    val contactRepository: ContactRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    val fieldRepository: FieldRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    val collectionRepository: CollectionRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    val tagRepository: TagRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val serverApiFactory: ServerApiFactory = top.mcxiafeng.badger.di.KoinComponentBy.get()
    init {
            }

    suspend fun testConnection(context: Context): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) { serverApiFactory.get().listBackups() }
    }

    /**
     * Build the envelope from local Room state and upload it. Pre-flight
     * check rejects envelopes exceeding [MAX_BACKUP_BYTES] with a precise
     * message; this turns a server-side 4xx into a useful client error.
     */
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
        val size = envelope.toByteArray(Charsets.UTF_8).size
        if (size > MAX_BACKUP_BYTES) {
            // [修复防御]: 客户端先于服务端 4xx 拒收 envelope,给出实际字节数。
            // 服务端 spec §0.2 / §14:envelope 上限 4 MiB。
            Log.w(TAG, "backup: envelope too large, size=$size > max=$MAX_BACKUP_BYTES")
            throw IllegalStateException("备份包过大: $size 字节 > ${MAX_BACKUP_BYTES} 字节")
        }
        Log.d(TAG, "backup: envelope size=$size bytes, uploading")
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

    /**
     * [§16] 列出服务端所有 backup envelope,供 [CloudBackupViewModel] 使用。
     */
    suspend fun listBackups(): Result<List<ServerApi.BackupSummary>> = runCatching {
        withContext(Dispatchers.IO) { serverApiFactory.get().listBackups() }
    }

    /**
     * [§16] 删除服务端单条 backup envelope。404 视幂等成功,与 [ServerApi.deleteBackup] 行为一致。
     */
    suspend fun deleteBackup(id: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) { serverApiFactory.get().deleteBackup(id) }
        Unit
    }

    companion object {
        private const val TAG = "CloudSync"

        /**
         * 服务端 spec §0.2 / §14:envelope 最大 4 MiB。
         * 客户端必须在此上限内拒绝上传,避免被服务端 4xx 拒收后用户看到无差别错误。
         */
        const val MAX_BACKUP_BYTES: Long = 4L * 1024 * 1024
    }
}
