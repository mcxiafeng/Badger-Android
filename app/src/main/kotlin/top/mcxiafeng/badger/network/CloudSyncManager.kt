package top.mcxiafeng.badger.network

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.exportToJson
import top.mcxiafeng.badger.data.importFromJson
import top.mcxiafeng.badger.data.ImportResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.content.edit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudSyncManager @Inject constructor(
    private val webDavClient: WebDavClient
) {
    private val TAG = "Tester"

    companion object {
        private const val BACKUP_PREFIX = "badger_backup_"
        private const val BACKUP_EXT = ".json"
        private const val BACKUP_VERSION = 1
    }

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun testConnection(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = WebDavConfig.getServerUrl(context)
            val username = WebDavConfig.getUsername(context)
            val password = WebDavConfig.getPassword(context)
            if (url.isBlank()) return@withContext Result.failure(Exception("未配置服务器地址"))
            when (val result = webDavClient.testConnection(url, username, password)) {
                is WebDavResult.Success -> Result.success(Unit)
                is WebDavResult.NotFound -> Result.failure(Exception("服务器路径不存在 (404)"))
                is WebDavResult.Timeout -> Result.failure(Exception("连接超时"))
                is WebDavResult.AuthError -> Result.failure(Exception(result.message))
                is WebDavResult.NetworkError -> Result.failure(result.throwable)
            }
        } catch (e: Exception) {
            Log.e(TAG, "testConnection failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun backup(context: Context, contactRepository: ContactRepository, fieldRepository: FieldRepository, collectionRepository: CollectionRepository): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = WebDavConfig.getServerUrl(context)
            val username = WebDavConfig.getUsername(context)
            val password = WebDavConfig.getPassword(context)
            val remotePath = WebDavConfig.getRemotePath(context)

            if (!WebDavConfig.isConfigured(context)) {
                return@withContext Result.failure(Exception("WebDAV 未配置"))
            }

            // 确保远程目录存在
            when (val ensureResult = webDavClient.ensureRemotePath(url, username, password, remotePath)) {
                is WebDavResult.Success -> { /* ok */ }
                is WebDavResult.NotFound -> return@withContext Result.failure(Exception("远程路径不存在 (404)"))
                is WebDavResult.Timeout -> return@withContext Result.failure(Exception("创建远程目录超时"))
                is WebDavResult.AuthError -> return@withContext Result.failure(Exception(ensureResult.message))
                is WebDavResult.NetworkError -> return@withContext Result.failure(ensureResult.throwable)
            }

            // 导出所有名片夹数据
            val collectionIds = mutableListOf<Long>()
            collectionRepository.getAllCollectionsOnce().forEach { collectionIds.add(it.id) }
            val dataJson = if (collectionIds.isNotEmpty()) {
                exportToJson(contactRepository, fieldRepository, collectionRepository, collectionIds)
            } else {
                "{}"
            }

            // 导出 SharedPreferences
            val preferences = JsonObject()
            for (prefsName in listOf("short_link_settings", "ai_ocr_config", "social_prefs")) {
                val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                val prefsObj = JsonObject()
                prefs.all.forEach { (key, value) ->
                    if (key != "api_key") {
                        prefsObj.addProperty(key, value.toString())
                    }
                }
                preferences.add(prefsName, prefsObj)
            }

            // 合并为完整备份
            val backup = JsonObject()
            backup.addProperty("version", BACKUP_VERSION)
            backup.addProperty("app", "badger")
            backup.addProperty("exportTime", System.currentTimeMillis())
            backup.add("data", JsonParser.parseString(dataJson))
            backup.add("preferences", preferences)

            val backupJson = gson.toJson(backup)

            // 上传
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(Date())
            val fileName = "$BACKUP_PREFIX$timestamp$BACKUP_EXT"
            val filePath = remotePath.trimEnd('/') + '/' + fileName

            when (val uploadResult = webDavClient.upload(url, username, password, filePath, backupJson.toByteArray(Charsets.UTF_8))) {
                is WebDavResult.Success -> {
                    WebDavConfig.saveLastSyncTime(context, System.currentTimeMillis())
                    Log.d(TAG, "backup: 上传成功 $fileName")
                    Result.success(Unit)
                }
                is WebDavResult.NotFound -> Result.failure(Exception("上传路径不存在 (404)"))
                is WebDavResult.Timeout -> Result.failure(Exception("上传超时"))
                is WebDavResult.AuthError -> Result.failure(Exception(uploadResult.message))
                is WebDavResult.NetworkError -> Result.failure(uploadResult.throwable)
            }
        } catch (e: Exception) {
            Log.e(TAG, "backup failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun restore(context: Context, contactRepository: ContactRepository, fieldRepository: FieldRepository, collectionRepository: CollectionRepository): Result<ImportResult> = withContext(Dispatchers.IO) {
        try {
            val url = WebDavConfig.getServerUrl(context)
            val username = WebDavConfig.getUsername(context)
            val password = WebDavConfig.getPassword(context)
            val remotePath = WebDavConfig.getRemotePath(context)

            // 列出远程备份文件
            val files = when (val listResult = webDavClient.listFiles(url, username, password, remotePath)) {
                is WebDavResult.Success -> listResult.data
                is WebDavResult.NotFound -> return@withContext Result.failure(Exception("远程路径不存在 (404)"))
                is WebDavResult.Timeout -> return@withContext Result.failure(Exception("列出文件超时"))
                is WebDavResult.AuthError -> return@withContext Result.failure(Exception(listResult.message))
                is WebDavResult.NetworkError -> return@withContext Result.failure(listResult.throwable)
            }

            if (files.isEmpty()) {
                return@withContext Result.failure(Exception("远程无备份文件"))
            }

            // 找到最新的备份文件
            val latestFile = files.firstOrNull { it.name.startsWith(BACKUP_PREFIX) && it.name.endsWith(BACKUP_EXT) }
                ?: return@withContext Result.failure(Exception("未找到有效备份文件"))

            // 下载
            val filePath = remotePath.trimEnd('/') + '/' + latestFile.name
            val data = when (val downloadResult = webDavClient.download(url, username, password, filePath)) {
                is WebDavResult.Success -> downloadResult.data
                is WebDavResult.NotFound -> return@withContext Result.failure(Exception("备份文件不存在 (404)"))
                is WebDavResult.Timeout -> return@withContext Result.failure(Exception("下载超时"))
                is WebDavResult.AuthError -> return@withContext Result.failure(Exception(downloadResult.message))
                is WebDavResult.NetworkError -> return@withContext Result.failure(downloadResult.throwable)
            }

            val backupJson = String(data, Charsets.UTF_8)
            val backup = JsonParser.parseString(backupJson).asJsonObject

            // 验证
            val version = backup.get("version")?.asInt ?: 0
            val app = backup.get("app")?.asString ?: ""
            if (app != "badger" || version < 1) {
                return@withContext Result.failure(Exception("备份文件格式无效"))
            }

            // 恢复联系人数据
            val dataStr = backup.get("data")?.toString() ?: "{}"
            val importResult = if (dataStr != "{}") {
                importFromJson(contactRepository, fieldRepository, collectionRepository, dataStr)
            } else {
                ImportResult(importedCollections = 0, importedContacts = 0, mergedContacts = 0)
            }

            // 恢复 SharedPreferences
            val preferences = backup.getAsJsonObject("preferences")
            if (preferences != null) {
                for (prefsName in listOf("short_link_settings", "ai_ocr_config", "social_prefs")) {
                    val prefsObj = preferences.getAsJsonObject(prefsName) ?: continue
                    val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    prefs.edit {
                        prefsObj.entrySet().forEach { (key, value) ->
                            if (key != "api_key") {
                                putString(key, value.asString)
                            }
                        }
                    }
                }
            }

            Log.d(TAG, "restore: 恢复成功, collections=${importResult.importedCollections}, contacts=${importResult.importedContacts}")
            Result.success(importResult)
        } catch (e: Exception) {
            Log.e(TAG, "restore failed: ${e.message}")
            Result.failure(e)
        }
    }

}
