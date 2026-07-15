package top.mcxiafeng.badger.data

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
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_ROLE = "role"
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
            .apply()
    }
}