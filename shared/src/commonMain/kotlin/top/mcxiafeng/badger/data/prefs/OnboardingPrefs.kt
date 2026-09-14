package top.mcxiafeng.badger.data.prefs

private const val KEY_COMPLETED = "onboarding_completed"
private const val KEY_SERVER_URL_CONFIGURED = "server_url_configured"

fun isOnboardingCompleted(): Boolean =
    PrefsStore.readBoolean(KEY_COMPLETED, false)

fun setOnboardingCompleted() {
    PrefsStore.writeBoolean(KEY_COMPLETED, true)
}

fun isServerUrlConfigured(): Boolean =
    PrefsStore.readBoolean(KEY_SERVER_URL_CONFIGURED, false)

fun setServerUrlConfigured(configured: Boolean) {
    PrefsStore.writeBoolean(KEY_SERVER_URL_CONFIGURED, configured)
}
