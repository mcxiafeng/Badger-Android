package top.mcxiafeng.badger.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.core.content.edit

object WebDavConfig {
    private const val PREFS_NAME = "webdav_config"
    private const val ENCRYPTED_PREFS_NAME = "webdav_credentials"

    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_REMOTE_PATH = "remote_path"
    private const val KEY_SYNC_ENABLED = "sync_enabled"
    private const val KEY_LAST_SYNC_TIME = "last_sync_time"

    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"

    private const val DEFAULT_REMOTE_PATH = "/badger-backup/"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun encryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getServerUrl(context: Context): String =
        prefs(context).getString(KEY_SERVER_URL, "") ?: ""

    fun saveServerUrl(context: Context, url: String) =
        prefs(context).edit { putString(KEY_SERVER_URL, url) }

    fun getRemotePath(context: Context): String =
        prefs(context).getString(KEY_REMOTE_PATH, DEFAULT_REMOTE_PATH) ?: DEFAULT_REMOTE_PATH

    fun saveRemotePath(context: Context, path: String) =
        prefs(context).edit { putString(KEY_REMOTE_PATH, path) }

    fun getUsername(context: Context): String =
        encryptedPrefs(context).getString(KEY_USERNAME, "") ?: ""

    fun saveUsername(context: Context, username: String) =
        encryptedPrefs(context).edit { putString(KEY_USERNAME, username) }

    fun getPassword(context: Context): String =
        encryptedPrefs(context).getString(KEY_PASSWORD, "") ?: ""

    fun savePassword(context: Context, password: String) =
        encryptedPrefs(context).edit { putString(KEY_PASSWORD, password) }

    fun isSyncEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SYNC_ENABLED, false)

    fun saveSyncEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit { putBoolean(KEY_SYNC_ENABLED, enabled) }

    fun getLastSyncTime(context: Context): Long =
        prefs(context).getLong(KEY_LAST_SYNC_TIME, 0L)

    fun saveLastSyncTime(context: Context, time: Long) =
        prefs(context).edit { putLong(KEY_LAST_SYNC_TIME, time) }

    fun isConfigured(context: Context): Boolean {
        val url = getServerUrl(context)
        val username = getUsername(context)
        return url.isNotBlank() && username.isNotBlank()
    }
}
