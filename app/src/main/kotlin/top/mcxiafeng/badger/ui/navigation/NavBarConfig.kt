package top.mcxiafeng.badger.ui.navigation

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "NavBarConfigTester"

object NavBarConfig {
    private const val PREFS_NAME = "badger_settings"
    private const val KEY_FLOATING_ENABLED = "nav_bar_floating"
    private const val KEY_LIQUID_ENABLED = "nav_liquid_enabled"

    private val _floatingFlow = MutableStateFlow(true)
    private val _liquidFlow = MutableStateFlow(true)

    val floatingFlow: StateFlow<Boolean> = _floatingFlow.asStateFlow()
    val liquidFlow: StateFlow<Boolean> = _liquidFlow.asStateFlow()

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _floatingFlow.value = prefs.getBoolean(KEY_FLOATING_ENABLED, true)
        _liquidFlow.value = prefs.getBoolean(KEY_LIQUID_ENABLED, true)
        Log.d(TAG, "Initialized: floating=${_floatingFlow.value}, liquid=${_liquidFlow.value}")
    }

    fun isFloatingEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_FLOATING_ENABLED, true)
    }

    fun saveFloatingEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_FLOATING_ENABLED, enabled).apply()
        _floatingFlow.value = enabled
        Log.d(TAG, "Saved: floating=$enabled")
    }

    /** 液态导航栏是悬浮底栏的材质开关：只有悬浮开启时才生效，独立于悬浮形态持久化。 */
    fun isLiquidEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_LIQUID_ENABLED, true)
    }

    fun saveLiquidEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_LIQUID_ENABLED, enabled).apply()
        _liquidFlow.value = enabled
        Log.d(TAG, "Saved: liquid=$enabled")
    }
}
