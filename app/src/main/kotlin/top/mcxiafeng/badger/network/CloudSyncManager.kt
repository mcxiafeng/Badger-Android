package top.mcxiafeng.badger.network

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.ContactRepository
import top.mcxiafeng.badger.utils.exportToJson
import top.mcxiafeng.badger.utils.importFromJson
import top.mcxiafeng.badger.utils.ImportResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.content.edit

object CloudSyncManager {
    private const val TAG = "CloudSyncManager"
    private const val BACKUP_PREFIX = "badger_backup_"
    private const val BACKUP_EXT = ".json"
    private const val BACKUP_VERSION = 1

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun testConnection(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = WebDavConfig.getServerUrl(context)
            val username = WebDavConfig.getUsername(context)
            val password = WebDavConfig.getPassword(context)
            if (url.isBlank()) return@withContext Result.failure(Exception("未配置服务器地址"))
            val success = WebDavClient.testConnection(url, username, password)
            if (success) Result.success(Unit) else Result.failure(Exception("连接失败，请检查配置"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun backup(context: Context, repository: ContactRepository): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = WebDavConfig.getServerUrl(context)
            val username = WebDavConfig.getUsername(context)
            val password = WebDavConfig.getPassword(context)
            val remotePath = WebDavConfig.getRemotePath(context)

            if (!WebDavConfig.isConfigured(context)) {
                return@withContext Result.failure(Exception("WebDAV 未配置"))
            }

            // 确保远程目录存在
            if (!WebDavClient.ensureRemotePath(url, username, password, remotePath)) {
                return@withContext Result.failure(Exception("无法创建远程目录"))
            }

            // 导出所有名片夹数据
            val collectionIds = mutableListOf<Long>()
            repository.getAllCollectionsOnce().forEach { collectionIds.add(it.id) }
            val dataJson = if (collectionIds.isNotEmpty()) {
                exportToJson(repository, collectionIds)
            } else {
                "{}"
            }

            // 导出 SharedPreferences
            val preferences = JsonObject()
            for (prefsName in listOf("short_link_settings", "ai_ocr_config", "social_prefs")) {
                val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                val prefsObj = JsonObject()
                prefs.all.forEach { (key, value) ->
                    prefsObj.addProperty(key, value.toString())
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

            val success = WebDavClient.upload(url, username, password, filePath, backupJson.toByteArray(Charsets.UTF_8))
            if (success) {
                WebDavConfig.saveLastSyncTime(context, System.currentTimeMillis())
                Log.d(TAG, "backup: 上传成功 $fileName")
                Result.success(Unit)
            } else {
                Result.failure(Exception("上传失败"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "backup failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun restore(context: Context, repository: ContactRepository): Result<ImportResult> = withContext(Dispatchers.IO) {
        try {
            val url = WebDavConfig.getServerUrl(context)
            val username = WebDavConfig.getUsername(context)
            val password = WebDavConfig.getPassword(context)
            val remotePath = WebDavConfig.getRemotePath(context)

            // 列出远程备份文件
            val files = WebDavClient.listFiles(url, username, password, remotePath)
            if (files.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("远程无备份文件"))
            }

            // 找到最新的备份文件
            val latestFile = files.firstOrNull { it.name.startsWith(BACKUP_PREFIX) && it.name.endsWith(BACKUP_EXT) }
                ?: return@withContext Result.failure(Exception("未找到有效备份文件"))

            // 下载
            val filePath = remotePath.trimEnd('/') + '/' + latestFile.name
            val data = WebDavClient.download(url, username, password, filePath)
                ?: return@withContext Result.failure(Exception("下载失败"))

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
                importFromJson(repository, dataStr)
            } else {
                ImportResult(importedCollections = 0, importedContacts = 0, skippedCollections = 0)
            }

            // 恢复 SharedPreferences
            val preferences = backup.getAsJsonObject("preferences")
            if (preferences != null) {
                for (prefsName in listOf("short_link_settings", "ai_ocr_config", "social_prefs")) {
                    val prefsObj = preferences.getAsJsonObject(prefsName) ?: continue
                    val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    prefs.edit {
                        prefsObj.entrySet().forEach { (key, value) ->
                            putString(key, value.asString)
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
