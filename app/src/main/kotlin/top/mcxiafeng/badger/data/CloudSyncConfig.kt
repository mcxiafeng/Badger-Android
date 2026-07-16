package top.mcxiafeng.badger.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Lightweight prefs for the user-facing cloud-sync **state** (last sync time +
 * whether cloud sync is enabled). Replaces the legacy `network.WebDavConfig`
 * object that was deleted as part of the migration onto `Badger-Server`'s
 * `/v1/backups` API.
 *
 * Storage: dedicated SharedPreferences file `badger_cloud_sync` so that
 * wiping auth tokens (`badger_auth`) never wipes the user's last-sync time
 * or auto-backup preference.
 *
 * The "last sync time" is updated by [top.mcxiafeng.badger.pages.settings.CloudSyncSettingsViewModel]
 * after a successful backup/restore; the UI reads it for display.
 *
 * WebDAV-era fields (username / password / remote path) were removed because
 * the new [top.mcxiafeng.badger.network.ServerApi] uses JWT auth and the
 * server stores backups at `/v1/backups` (no client-supplied path).
 *
 * **重要**: 之前这里有一个独立的「备份服务器 URL」字段
 * (`KEY_SERVER_URL`/`getServerUrl`/`saveServerUrl`),它和登录服务器 URL
 * (`AuthPrefs.readServerUrl`) 实际上指向同一地址,但实际网络层只读取后者。
 * 历史配置已通过 [top.mcxiafeng.badger.pages.settings.AccountAndBackupPage]
 * 的 `LaunchedEffect` 一次性迁移:检测到旧备份 URL 非空且登录服务器仍是默认
 * (`http://10.0.2.2:8080`) 时,把旧值写到 AuthPrefs。该字段已彻底删除,
 * 读取仅作为一次性的迁移辅助:[readLegacyServerUrl]/[clearLegacyServerUrl]。
 */
object CloudSyncConfig {
    private const val PREFS = "badger_cloud_sync"
    private const val KEY_LAST_SYNC = "last_sync_time"
    private const val KEY_SYNC_ENABLED = "sync_enabled"

    /**
     * 旧版服务器 URL 的 SharedPreferences key。仅供迁移代码读,新逻辑不再写。
     */
    private const val LEGACY_KEY_SERVER_URL = "server_url"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * 仅供 [top.mcxiafeng.badger.pages.settings.AccountAndBackupPage] 的迁移
     * LaunchedEffect 一次性读取旧值,无新调用方。
     */
    fun readLegacyServerUrl(ctx: Context): String =
        sp(ctx).getString(LEGACY_KEY_SERVER_URL, "") ?: ""

    /**
     * 一次性清掉历史遗留的 server_url 字段,迁移完成后调用。
     */
    fun clearLegacyServerUrl(ctx: Context) {
        sp(ctx).edit().remove(LEGACY_KEY_SERVER_URL).apply()
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
     * Returns true when the user is configured to sync. Configuration means
     * the **shared** server URL (in [AuthPrefs]) is non-blank — there is no
     * longer a separate backup-server field. Authentication is handled by
     * the JWT layer in [AuthPrefs].
     */
    fun isConfigured(ctx: Context): Boolean =
        AuthPrefs.readServerUrl(ctx).isNotBlank()
}
