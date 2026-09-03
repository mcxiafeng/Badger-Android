package top.mcxiafeng.badger.data.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * Lightweight prefs for the on-server auth system. Kept separate from
 * [OnboardingPrefs] so an OAuth refresh-token revocation doesn't wipe the
 * onboarding flag (and vice versa).
 *
 * Storage: plain [SharedPreferences]. The refresh token is short-lived (≤
 * 7d by server default) and the access token never touches disk — it lives
 * in memory inside the ServerApi's `TokenHolder` and is rewritten on every
 * refresh.
 */
object AuthPrefs {
    private const val PREFS = "badger_auth"
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

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun readRefreshToken(ctx: Context): String? =
        sp(ctx).getString(KEY_REFRESH, null)

    fun writeRefreshToken(ctx: Context, token: String) {
        sp(ctx).edit().putString(KEY_REFRESH, token).apply()
    }

    fun readUserId(ctx: Context): String? =
        sp(ctx).getString(KEY_USER_ID, null)

    fun writeUserId(ctx: Context, id: String) {
        sp(ctx).edit().putString(KEY_USER_ID, id).apply()
    }

    fun readUsername(ctx: Context): String? =
        sp(ctx).getString(KEY_USERNAME, null)

    fun writeUsername(ctx: Context, name: String) {
        sp(ctx).edit().putString(KEY_USERNAME, name).apply()
    }

    fun readRole(ctx: Context): String? =
        sp(ctx).getString(KEY_ROLE, null)

    fun writeRole(ctx: Context, role: String) {
        sp(ctx).edit().putString(KEY_ROLE, role).apply()
    }

    // ---- [Phase 2] 新契约 user 字段缓存 ----

    fun readDisplayName(ctx: Context): String? =
        sp(ctx).getString(KEY_DISPLAY_NAME, null)

    fun writeDisplayName(ctx: Context, name: String) {
        sp(ctx).edit().putString(KEY_DISPLAY_NAME, name).apply()
    }

    fun readEmail(ctx: Context): String? =
        sp(ctx).getString(KEY_EMAIL, null)

    fun writeEmail(ctx: Context, email: String) {
        sp(ctx).edit().putString(KEY_EMAIL, email).apply()
    }

    fun readIsAdmin(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_IS_ADMIN, false)

    fun writeIsAdmin(ctx: Context, isAdmin: Boolean) {
        sp(ctx).edit().putBoolean(KEY_IS_ADMIN, isAdmin).apply()
    }

    /**
     * Returns the base URL the client should talk to. Defaults to a dev
     * emulator-style address (`http://10.0.2.2:8080` reaches the host machine
     * from an Android emulator).
     */
    fun readServerUrl(ctx: Context): String =
        sp(ctx).getString(KEY_SERVER_URL, null) ?: "http://10.0.2.2:8080"

    fun writeServerUrl(ctx: Context, url: String) {
        sp(ctx).edit().putString(KEY_SERVER_URL, url).apply()
    }

    fun clearAuth(ctx: Context) {
        sp(ctx).edit()
            .remove(KEY_REFRESH)
            .remove(KEY_USER_ID)
            .remove(KEY_USERNAME)
            .remove(KEY_ROLE)
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_EMAIL)
            .remove(KEY_IS_ADMIN)
            .apply()
    }
}
