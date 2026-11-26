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
    private const val KEY_GLASS_MATERIAL_ENABLED = "glass_material_enabled"

    private val _floatingFlow = MutableStateFlow(true)
    private val _glassMaterialFlow = MutableStateFlow(true)

    val floatingFlow: StateFlow<Boolean> = _floatingFlow.asStateFlow()
    val glassMaterialFlow: StateFlow<Boolean> = _glassMaterialFlow.asStateFlow()

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _floatingFlow.value = prefs.getBoolean(KEY_FLOATING_ENABLED, true)
        _glassMaterialFlow.value = prefs.getBoolean(KEY_GLASS_MATERIAL_ENABLED, true)
        Log.d(TAG, "Initialized: floating=${_floatingFlow.value}, glassMaterial=${_glassMaterialFlow.value}")
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

    fun isGlassMaterialEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_GLASS_MATERIAL_ENABLED, true)
    }

    fun saveGlassMaterialEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_GLASS_MATERIAL_ENABLED, enabled).apply()
        _glassMaterialFlow.value = enabled
        Log.d(TAG, "Saved: glassMaterial=$enabled")
    }
}
