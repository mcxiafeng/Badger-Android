package top.mcxiafeng.badger.pages.setupguide

import android.content.Context
import androidx.core.content.edit

internal const val TAG = "SetupGuide"

private const val HINT_PREFS = "badger_hints"
private const val KEY_SETUP_COMPLETED = "hint_shown_setup_guide_completed"

fun setSetupGuideCompleted(context: Context) {
    context.getSharedPreferences(HINT_PREFS, Context.MODE_PRIVATE)
        .edit { putBoolean(KEY_SETUP_COMPLETED, true) }
}
