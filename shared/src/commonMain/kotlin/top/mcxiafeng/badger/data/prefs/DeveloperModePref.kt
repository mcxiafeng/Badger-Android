package top.mcxiafeng.badger.data.prefs

private const val KEY_DEVELOPER_MODE = "developer_mode_enabled"

fun isDeveloperMode(): Boolean {
    return PrefsStore.readBoolean(KEY_DEVELOPER_MODE, false)
}

fun setDeveloperMode(enabled: Boolean) {
    PrefsStore.writeBoolean(KEY_DEVELOPER_MODE, enabled)
}
