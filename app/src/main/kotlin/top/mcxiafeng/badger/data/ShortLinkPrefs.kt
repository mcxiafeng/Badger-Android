package top.mcxiafeng.badger.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistent local preferences for short-link selection and custom provider UI state.
 * The short.io API key is server-owned; this class deliberately does not persist it.
 */
object ShortLinkPrefs {
    private const val PREFS = "badger_short_link"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_DOMAIN = "domain"
    private const val KEY_DOMAIN_ID = "domain_id"
    private const val KEY_LINK_ID = "link_id"
    private const val KEY_SHORT_URL = "short_url"
    private const val KEY_CUSTOM_ENABLED = "custom_enabled"
    private const val KEY_API_URL = "api_url"
    private const val KEY_UPDATE_PATH = "update_path"
    private const val KEY_API_METHOD = "api_method"
    private const val KEY_AUTH_HEADER = "auth_header"
    private const val KEY_AUTH_PREFIX = "auth_prefix"
    private const val KEY_UPDATE_BODY = "update_body"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_ENABLED, false)
    fun setEnabled(ctx: Context, value: Boolean) { sp(ctx).edit().putBoolean(KEY_ENABLED, value).apply() }

    fun getDomain(ctx: Context): String = sp(ctx).getString(KEY_DOMAIN, "") ?: ""
    fun saveDomain(ctx: Context, value: String) { sp(ctx).edit().putString(KEY_DOMAIN, value).apply() }

    fun getDomainId(ctx: Context): Long = sp(ctx).getLong(KEY_DOMAIN_ID, 0L)
    fun saveDomainId(ctx: Context, value: Long) { sp(ctx).edit().putLong(KEY_DOMAIN_ID, value).apply() }

    fun getLinkId(ctx: Context): String = sp(ctx).getString(KEY_LINK_ID, "") ?: ""
    fun saveLinkId(ctx: Context, value: String) { sp(ctx).edit().putString(KEY_LINK_ID, value).apply() }

    fun getShortUrl(ctx: Context): String? =
        sp(ctx).getString(KEY_SHORT_URL, null)?.takeIf { it.isNotBlank() }

    fun saveShortUrl(ctx: Context, value: String?) {
        sp(ctx).edit().apply {
            if (value.isNullOrBlank()) remove(KEY_SHORT_URL) else putString(KEY_SHORT_URL, value)
        }.apply()
    }

    fun isCustomEnabled(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_CUSTOM_ENABLED, false)
    fun setCustomEnabled(ctx: Context, value: Boolean) { sp(ctx).edit().putBoolean(KEY_CUSTOM_ENABLED, value).apply() }

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