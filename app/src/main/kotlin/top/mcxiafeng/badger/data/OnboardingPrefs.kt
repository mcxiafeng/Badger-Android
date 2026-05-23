package top.mcxiafeng.badger.data

import android.content.Context

private const val PREFS_NAME = "badger_onboarding"
private const val KEY_COMPLETED = "onboarding_completed"

fun isOnboardingCompleted(context: Context): Boolean {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_COMPLETED, false)
}

fun setOnboardingCompleted(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_COMPLETED, true).apply()
}
