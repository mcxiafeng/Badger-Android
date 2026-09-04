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

    fun getApiKey(): String =
        PrefsStore.readString(KEY_API_KEY) ?: ""

    fun saveApiKey(k: String) {
        PrefsStore.writeString(KEY_API_KEY, k)
    }

    fun isEnabled(): Boolean =
        PrefsStore.readBoolean(KEY_ENABLED, false)

    fun setEnabled(value: Boolean) {
        PrefsStore.writeBoolean(KEY_ENABLED, value)
    }

    fun getDomain(): String =
        PrefsStore.readString(KEY_DOMAIN) ?: ""

    fun saveDomain(value: String) {
        PrefsStore.writeString(KEY_DOMAIN, value)
    }

    fun getDomainId(): Long =
        PrefsStore.readLong(KEY_DOMAIN_ID, 0L)

    fun saveDomainId(value: Long) {
        PrefsStore.writeLong(KEY_DOMAIN_ID, value)
    }

    fun getLinkId(): String =
        PrefsStore.readString(KEY_LINK_ID) ?: ""

    fun saveLinkId(value: String) {
        PrefsStore.writeString(KEY_LINK_ID, value)
    }

    fun getShortUrl(): String? =
        PrefsStore.readString(KEY_SHORT_URL)?.takeIf { it.isNotBlank() }

    fun saveShortUrl(value: String?) {
        PrefsStore.writeString(KEY_SHORT_URL, value)
    }

    fun isCustomEnabled(): Boolean =
        PrefsStore.readBoolean(KEY_CUSTOM_ENABLED, false)

    fun setCustomEnabled(value: Boolean) {
        PrefsStore.writeBoolean(KEY_CUSTOM_ENABLED, value)
    }

    fun getApiUrl(): String =
        PrefsStore.readString(KEY_API_URL) ?: ""

    fun getUpdatePath(): String =
        PrefsStore.readString(KEY_UPDATE_PATH) ?: "/links/{linkId}"

    fun getApiMethod(): String =
        PrefsStore.readString(KEY_API_METHOD) ?: "POST"

    fun getAuthHeader(): String =
        PrefsStore.readString(KEY_AUTH_HEADER) ?: "Authorization"

    fun getAuthPrefix(): String =
        PrefsStore.readString(KEY_AUTH_PREFIX) ?: "Bearer "

    fun getUpdateBody(): String =
        PrefsStore.readString(KEY_UPDATE_BODY) ?: """{"originalURL":"{url}"}"""

    fun saveAdvanced(
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
