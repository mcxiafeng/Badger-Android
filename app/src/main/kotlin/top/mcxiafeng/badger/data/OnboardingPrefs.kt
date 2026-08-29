package top.mcxiafeng.badger.data

import android.content.Context

private const val PREFS_NAME = "badger_onboarding"
private const val KEY_COMPLETED = "onboarding_completed"
private const val KEY_SERVER_URL_CONFIGURED = "server_url_configured"

fun isOnboardingCompleted(context: Context): Boolean {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_COMPLETED, false)
}

fun setOnboardingCompleted(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_COMPLETED, true).apply()
}

/**
 * [V2-E2E #1] 检测当前服务器 URL 是否被用户主动配置过。
 *
 * 启动期 / AccountProfilePage 引导逻辑会读这个标志:
 * - false:首次启动,默认 URL 是 10.0.2.2:8080 (emulator 专用),真机/真模拟器连不通,
 *         必须在 LoginPage / AccountProfilePage 引导配置服务器地址。
 * - true:用户已配置过,无脑沿用 AuthPrefs 里的 server_url。
 *
 * 在用户**成功保存**服务器 URL 时调 [setServerUrlConfigured] 置 true;
 * "恢复默认"按钮不算用户主动配置(那是 fallback),保持 false。
 */
fun isServerUrlConfigured(context: Context): Boolean {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_SERVER_URL_CONFIGURED, false)
}

fun setServerUrlConfigured(context: Context, configured: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_SERVER_URL_CONFIGURED, configured).apply()
}
