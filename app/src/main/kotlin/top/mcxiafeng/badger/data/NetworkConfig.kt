package top.mcxiafeng.badger.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Network-level toggles that aren't tied to either the auth flow or the
 * cloud-sync endpoint. Currently only "allow insecure HTTP" — when the
 * device is on a captive portal / self-signed NAS the user has to opt in
 * explicitly. The OkHttp client reads this on construction (see
 * [top.mcxiafeng.badger.di.NetworkModule]); the change only takes effect
 * after a restart, which the Settings page reminds the user about.
 */
object NetworkConfig {
    private const val PREFS = "badger_network"
    private const val KEY_ALLOW_INSECURE_HTTP = "allow_insecure_http"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isAllowInsecureHttp(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_ALLOW_INSECURE_HTTP, false)

    fun saveAllowInsecureHttp(ctx: Context, enabled: Boolean) {
        sp(ctx).edit().putBoolean(KEY_ALLOW_INSECURE_HTTP, enabled).apply()
    }
}