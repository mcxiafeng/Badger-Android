package top.mcxiafeng.badger.data.prefs

private const val KEY_COMPLETED = "onboarding_completed"
private const val KEY_SERVER_URL_CONFIGURED = "server_url_configured"

/**
 * [KMP K05/K08-B] DataStore Preferences（经 PrefsStore 内存缓存），原 badger_onboarding 文件。
 */
fun isOnboardingCompleted(): Boolean =
    PrefsStore.readBoolean(KEY_COMPLETED, false)

fun setOnboardingCompleted() {
    PrefsStore.writeBoolean(KEY_COMPLETED, true)
}

/**
 * 检测当前服务器 URL 是否被用户主动配置过。
 *
 * false：首次启动，默认 URL 是 emulator 专用的 10.0.2.2:8080，需要引导配置。
 * true：用户曾成功保存过服务器地址。
 */
fun isServerUrlConfigured(): Boolean =
    PrefsStore.readBoolean(KEY_SERVER_URL_CONFIGURED, false)

fun setServerUrlConfigured(configured: Boolean) {
    PrefsStore.writeBoolean(KEY_SERVER_URL_CONFIGURED, configured)
}
