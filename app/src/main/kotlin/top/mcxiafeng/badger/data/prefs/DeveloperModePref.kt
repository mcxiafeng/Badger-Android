package top.mcxiafeng.badger.data.prefs

import android.content.Context
import androidx.core.content.edit

private const val HINT_PREFS = "badger_hints"
private const val KEY_DEVELOPER_MODE = "developer_mode_enabled"

fun isDeveloperMode(context: Context): Boolean {
    return context.getSharedPreferences(HINT_PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_DEVELOPER_MODE, false)
}

fun setDeveloperMode(context: Context, enabled: Boolean) {
    context.getSharedPreferences(HINT_PREFS, Context.MODE_PRIVATE)
        .edit { putBoolean(KEY_DEVELOPER_MODE, enabled) }
}
