package top.mcxiafeng.badger.data.prefs

private const val KEY_DEVELOPER_MODE = "developer_mode_enabled"

/**
 * [KMP K05] DataStore Preferences（经 PrefsStore 内存缓存），原 badger_hints 文件。
 */
fun isDeveloperMode(): Boolean {
    return PrefsStore.readBoolean(KEY_DEVELOPER_MODE, false)
}

fun setDeveloperMode(enabled: Boolean) {
    PrefsStore.writeBoolean(KEY_DEVELOPER_MODE, enabled)
}
