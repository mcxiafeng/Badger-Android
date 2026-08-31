package top.mcxiafeng.badger.data

import android.content.Context
import android.content.SharedPreferences

private const val PREFS_NAME = "badger_onboarding"
private const val KEY_COMPLETED = "onboarding_completed"
private const val KEY_SERVER_URL_CONFIGURED = "server_url_configured"

private fun Context.onboardingPrefs(): SharedPreferences =
    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

fun isOnboardingCompleted(context: Context): Boolean =
    context.onboardingPrefs().getBoolean(KEY_COMPLETED, false)

fun setOnboardingCompleted(context: Context) {
    context.onboardingPrefs()
        .edit()
        .putBoolean(KEY_COMPLETED, true)
        .apply()
}

/**
 * 检测当前服务器 URL 是否被用户主动配置过。
 *
 * false：首次启动，默认 URL 是 emulator 专用的 10.0.2.2:8080，需要引导配置。
 * true：用户曾成功保存过服务器地址。
 */
fun isServerUrlConfigured(context: Context): Boolean =
    context.onboardingPrefs().getBoolean(KEY_SERVER_URL_CONFIGURED, false)

fun setServerUrlConfigured(context: Context, configured: Boolean) {
    context.onboardingPrefs()
        .edit()
        .putBoolean(KEY_SERVER_URL_CONFIGURED, configured)
        .apply()
}
