package top.mcxiafeng.badger.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistent prefs for the short-link (short.io / custom) feature.
 *
 * Lives independently of [CloudSyncConfig] and [AuthPrefs] so a settings
 * reset only nukes one concern at a time.
 */
object ShortLinkPrefs {
    private const val PREFS = "badger_short_link"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_DOMAIN = "domain"
    private const val KEY_DOMAIN_ID = "domain_id"
    private const val KEY_LINK_ID = "link_id"
    private const val KEY_CUSTOM_ENABLED = "custom_enabled"
    private const val KEY_API_URL = "api_url"
    private const val KEY_UPDATE_PATH = "update_path"
    private const val KEY_API_METHOD = "api_method"
    private const val KEY_AUTH_HEADER = "auth_header"
    private const val KEY_AUTH_PREFIX = "auth_prefix"
    private const val KEY_UPDATE_BODY = "update_body"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getApiKey(ctx: Context): String = sp(ctx).getString(KEY_API_KEY, "") ?: ""
    fun saveApiKey(ctx: Context, k: String) { sp(ctx).edit().putString(KEY_API_KEY, k).apply() }

    fun isEnabled(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_ENABLED, false)
    fun setEnabled(ctx: Context, b: Boolean) { sp(ctx).edit().putBoolean(KEY_ENABLED, b).apply() }

    fun getDomain(ctx: Context): String = sp(ctx).getString(KEY_DOMAIN, "") ?: ""
    fun saveDomain(ctx: Context, d: String) { sp(ctx).edit().putString(KEY_DOMAIN, d).apply() }

    /**
     * The numeric short.io domain id, persisted as Long so it survives the
     * process boundary. 0L means "not yet picked".
     */
    fun getDomainId(ctx: Context): Long = sp(ctx).getLong(KEY_DOMAIN_ID, 0L)
    fun saveDomainId(ctx: Context, id: Long) { sp(ctx).edit().putLong(KEY_DOMAIN_ID, id).apply() }

    fun getLinkId(ctx: Context): String = sp(ctx).getString(KEY_LINK_ID, "") ?: ""
    fun saveLinkId(ctx: Context, id: String) { sp(ctx).edit().putString(KEY_LINK_ID, id).apply() }

    fun isCustomEnabled(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_CUSTOM_ENABLED, false)
    fun setCustomEnabled(ctx: Context, b: Boolean) { sp(ctx).edit().putBoolean(KEY_CUSTOM_ENABLED, b).apply() }

    fun getApiUrl(ctx: Context): String = sp(ctx).getString(KEY_API_URL, "") ?: ""
    fun getUpdatePath(ctx: Context): String = sp(ctx).getString(KEY_UPDATE_PATH, "/links/{linkId}") ?: "/links/{linkId}"
    fun getApiMethod(ctx: Context): String = sp(ctx).getString(KEY_API_METHOD, "POST") ?: "POST"
    fun getAuthHeader(ctx: Context): String = sp(ctx).getString(KEY_AUTH_HEADER, "Authorization") ?: "Authorization"
    fun getAuthPrefix(ctx: Context): String = sp(ctx).getString(KEY_AUTH_PREFIX, "Bearer ") ?: "Bearer "
    fun getUpdateBody(ctx: Context): String = sp(ctx).getString(KEY_UPDATE_BODY, """{"originalURL":"{url}"}""") ?: """{"originalURL":"{url}"}"""

    fun saveAdvanced(
        ctx: Context,
        enabled: Boolean,
        apiUrl: String,
        updatePath: String,
        method: String,
        authHeader: String,
        authPrefix: String,
        updateBody: String,
    ) {
        sp(ctx).edit()
            .putBoolean(KEY_CUSTOM_ENABLED, enabled)
            .putString(KEY_API_URL, apiUrl)
            .putString(KEY_UPDATE_PATH, updatePath)
            .putString(KEY_API_METHOD, method)
            .putString(KEY_AUTH_HEADER, authHeader)
            .putString(KEY_AUTH_PREFIX, authPrefix)
            .putString(KEY_UPDATE_BODY, updateBody)
            .apply()
    }
}