package top.mcxiafeng.badger.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Lightweight prefs for the user-facing cloud-sync configuration. Replaces
 * the legacy `network.WebDavConfig` object that was deleted as part of the
 * migration onto `Badger-Server`'s `/v1/backups` API.
 *
 * Storage: dedicated SharedPreferences file `badger_cloud_sync` so that
 * wiping auth tokens (`badger_auth`) never wipes the user's server address.
 *
 * The "last sync time" is updated by [top.mcxiafeng.badger.pages.settings.CloudSyncSettingsViewModel]
 * after a successful backup/restore; the UI reads it for display.
 */
object CloudSyncConfig {
    private const val PREFS = "badger_cloud_sync"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"
    private const val KEY_REMOTE_PATH = "remote_path"
    private const val KEY_LAST_SYNC = "last_sync_time"
    private const val KEY_SYNC_ENABLED = "sync_enabled"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getServerUrl(ctx: Context): String =
        sp(ctx).getString(KEY_SERVER_URL, "") ?: ""

    fun saveServerUrl(ctx: Context, url: String) {
        sp(ctx).edit().putString(KEY_SERVER_URL, url).apply()
    }

    fun getUsername(ctx: Context): String =
        sp(ctx).getString(KEY_USERNAME, "") ?: ""

    fun saveUsername(ctx: Context, name: String) {
        sp(ctx).edit().putString(KEY_USERNAME, name).apply()
    }

    fun getPassword(ctx: Context): String =
        sp(ctx).getString(KEY_PASSWORD, "") ?: ""

    fun savePassword(ctx: Context, pw: String) {
        sp(ctx).edit().putString(KEY_PASSWORD, pw).apply()
    }

    fun getRemotePath(ctx: Context): String =
        sp(ctx).getString(KEY_REMOTE_PATH, "/badger-backup/") ?: "/badger-backup/"

    fun saveRemotePath(ctx: Context, path: String) {
        sp(ctx).edit().putString(KEY_REMOTE_PATH, path).apply()
    }

    fun getLastSyncTime(ctx: Context): Long =
        sp(ctx).getLong(KEY_LAST_SYNC, 0L)

    fun writeLastSyncTime(ctx: Context, ts: Long) {
        sp(ctx).edit().putLong(KEY_LAST_SYNC, ts).apply()
    }

    fun isSyncEnabled(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_SYNC_ENABLED, false)

    fun saveSyncEnabled(ctx: Context, enabled: Boolean) {
        sp(ctx).edit().putBoolean(KEY_SYNC_ENABLED, enabled).apply()
    }

    /**
     * Returns true when the user has filled in the minimum fields required to
     * hit the server. (Currently: server URL non-blank. The credential fields
     * are kept on the server side as the JWT auth, so we no longer gate on
     * username/password — those used to be WebDAV credentials.)
     */
    fun isConfigured(ctx: Context): Boolean =
        getServerUrl(ctx).isNotBlank()
}