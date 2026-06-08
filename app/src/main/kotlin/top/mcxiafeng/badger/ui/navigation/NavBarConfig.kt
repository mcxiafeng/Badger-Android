package top.mcxiafeng.badger.ui.navigation

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "NavBarConfig"

object NavBarConfig {
    private const val PREFS_NAME = "badger_settings"
    private const val KEY_FLOATING_ENABLED = "nav_bar_floating"

    private val _floatingFlow = MutableStateFlow(false)

    val floatingFlow: StateFlow<Boolean> = _floatingFlow.asStateFlow()

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _floatingFlow.value = prefs.getBoolean(KEY_FLOATING_ENABLED, false)
        Log.d(TAG, "Initialized: floating=${_floatingFlow.value}")
    }

    fun isFloatingEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_FLOATING_ENABLED, false)
    }

    fun saveFloatingEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_FLOATING_ENABLED, enabled).apply()
        _floatingFlow.value = enabled
        Log.d(TAG, "Saved: floating=$enabled")
    }
}
