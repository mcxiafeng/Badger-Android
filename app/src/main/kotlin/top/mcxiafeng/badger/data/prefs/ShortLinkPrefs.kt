package top.mcxiafeng.badger.data.prefs

/**
 * Persistent local preferences for short-link selection and custom provider UI state.
 * The short.io API key is server-owned; this class deliberately does not persist it.
 *
 * [KMP K05] Storage: DataStore Preferences（经 PrefsStore 内存缓存）。
 *
 * [兼容] KEY_API_KEY / getApiKey / saveApiKey 保留:dev 的 NfcSettingsPage 仍提供
 * 本地 key 输入界面。服务器路径不读取该值,后续 UI 迁移完成后可整体移除。
 */
object ShortLinkPrefs {
    private const val KEY_API_KEY = "api_key"
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

    fun getApiKey(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context): String =
        PrefsStore.readString(KEY_API_KEY) ?: ""

    fun saveApiKey(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context, k: String) {
        PrefsStore.writeString(KEY_API_KEY, k)
    }

    fun isEnabled(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context): Boolean =
        PrefsStore.readBoolean(KEY_ENABLED, false)

    fun setEnabled(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context, value: Boolean) {
        PrefsStore.writeBoolean(KEY_ENABLED, value)
    }

    fun getDomain(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context): String =
        PrefsStore.readString(KEY_DOMAIN) ?: ""

    fun saveDomain(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context, value: String) {
        PrefsStore.writeString(KEY_DOMAIN, value)
    }

    fun getDomainId(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context): Long =
        PrefsStore.readLong(KEY_DOMAIN_ID, 0L)

    fun saveDomainId(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context, value: Long) {
        PrefsStore.writeLong(KEY_DOMAIN_ID, value)
    }

    fun getLinkId(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context): String =
        PrefsStore.readString(KEY_LINK_ID) ?: ""

    fun saveLinkId(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context, value: String) {
        PrefsStore.writeString(KEY_LINK_ID, value)
    }

    fun getShortUrl(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context): String? =
        PrefsStore.readString(KEY_SHORT_URL)?.takeIf { it.isNotBlank() }

    fun saveShortUrl(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context, value: String?) {
        PrefsStore.writeString(KEY_SHORT_URL, value)
    }

    fun isCustomEnabled(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context): Boolean =
        PrefsStore.readBoolean(KEY_CUSTOM_ENABLED, false)

    fun setCustomEnabled(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context, value: Boolean) {
        PrefsStore.writeBoolean(KEY_CUSTOM_ENABLED, value)
    }

    fun getApiUrl(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context): String =
        PrefsStore.readString(KEY_API_URL) ?: ""

    fun getUpdatePath(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context): String =
        PrefsStore.readString(KEY_UPDATE_PATH) ?: "/links/{linkId}"

    fun getApiMethod(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context): String =
        PrefsStore.readString(KEY_API_METHOD) ?: "POST"

    fun getAuthHeader(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context): String =
        PrefsStore.readString(KEY_AUTH_HEADER) ?: "Authorization"

    fun getAuthPrefix(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context): String =
        PrefsStore.readString(KEY_AUTH_PREFIX) ?: "Bearer "

    fun getUpdateBody(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context): String =
        PrefsStore.readString(KEY_UPDATE_BODY) ?: """{"originalURL":"{url}"}"""

    fun saveAdvanced(
        @Suppress("UNUSED_PARAMETER") ctx: android.content.Context,
        enabled: Boolean,
        apiUrl: String,
        updatePath: String,
        method: String,
        authHeader: String,
        authPrefix: String,
        updateBody: String,
    ) {
        PrefsStore.writeBoolean(KEY_CUSTOM_ENABLED, enabled)
        PrefsStore.writeString(KEY_API_URL, apiUrl)
        PrefsStore.writeString(KEY_UPDATE_PATH, updatePath)
        PrefsStore.writeString(KEY_API_METHOD, method)
        PrefsStore.writeString(KEY_AUTH_HEADER, authHeader)
        PrefsStore.writeString(KEY_AUTH_PREFIX, authPrefix)
        PrefsStore.writeString(KEY_UPDATE_BODY, updateBody)
    }
}
