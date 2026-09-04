package top.mcxiafeng.badger.data.prefs

/**
 * Lightweight prefs for the on-server auth system. Kept separate from
 * OnboardingPrefs so an OAuth refresh-token revocation doesn't wipe the
 * onboarding flag (and vice versa).
 *
 * [KMP K05] Storage: DataStore Preferences（经 PrefsStore 内存缓存，读同步语义不变）。
 * The refresh token is short-lived (≤ 7d by server default) and the access
 * token never touches disk — it lives in memory inside the ServerApi's
 * `TokenHolder` and is rewritten on every refresh.
 */
object AuthPrefs {
    // [Phase 2] 旧契约只有 access token（存 refresh_token 键）+ role 字符串；
    // 新 Java /api 契约登录返回 user{uuid,name,displayName,email,isAdmin}，这里补本地缓存。
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_ROLE = "role"
    private const val KEY_DISPLAY_NAME = "display_name"
    private const val KEY_EMAIL = "email"
    private const val KEY_IS_ADMIN = "is_admin"
    private const val KEY_SERVER_URL = "server_url"

    fun readRefreshToken(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context): String? =
        PrefsStore.readString(KEY_REFRESH)

    fun writeRefreshToken(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context, token: String) {
        PrefsStore.writeString(KEY_REFRESH, token)
    }

    fun readUserId(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context): String? =
        PrefsStore.readString(KEY_USER_ID)

    fun writeUserId(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context, id: String) {
        PrefsStore.writeString(KEY_USER_ID, id)
    }

    fun readUsername(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context): String? =
        PrefsStore.readString(KEY_USERNAME)

    fun writeUsername(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context, name: String) {
        PrefsStore.writeString(KEY_USERNAME, name)
    }

    fun readRole(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context): String? =
        PrefsStore.readString(KEY_ROLE)

    fun writeRole(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context, role: String) {
        PrefsStore.writeString(KEY_ROLE, role)
    }

    // ---- [Phase 2] 新契约 user 字段缓存 ----

    fun readDisplayName(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context): String? =
        PrefsStore.readString(KEY_DISPLAY_NAME)

    fun writeDisplayName(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context, name: String) {
        PrefsStore.writeString(KEY_DISPLAY_NAME, name)
    }

    fun readEmail(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context): String? =
        PrefsStore.readString(KEY_EMAIL)

    fun writeEmail(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context, email: String) {
        PrefsStore.writeString(KEY_EMAIL, email)
    }

    fun readIsAdmin(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context): Boolean =
        PrefsStore.readBoolean(KEY_IS_ADMIN, false)

    fun writeIsAdmin(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context, isAdmin: Boolean) {
        PrefsStore.writeBoolean(KEY_IS_ADMIN, isAdmin)
    }

    /**
     * Returns the base URL the client should talk to. Defaults to a dev
     * emulator-style address (`http://10.0.2.2:8080` reaches the host machine
     * from an Android emulator).
     */
    fun readServerUrl(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context): String =
        PrefsStore.readString(KEY_SERVER_URL) ?: "http://10.0.2.2:8080"

    fun writeServerUrl(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context, url: String) {
        PrefsStore.writeString(KEY_SERVER_URL, url)
    }

    fun clearAuth(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context) {
        listOf(
            KEY_REFRESH, KEY_USER_ID, KEY_USERNAME, KEY_ROLE,
            KEY_DISPLAY_NAME, KEY_EMAIL, KEY_IS_ADMIN,
        ).forEach { PrefsStore.remove(it) }
    }
}
