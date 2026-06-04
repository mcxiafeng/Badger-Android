package top.mcxiafeng.badger.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "NetworkConfig"

object NetworkConfig {
    private const val PREFS_NAME = "badger_settings"
    private const val KEY_ALLOW_INSECURE_HTTP = "allow_insecure_http"

    private val _allowInsecureHttp = MutableStateFlow(false)
    val allowInsecureHttp: StateFlow<Boolean> = _allowInsecureHttp.asStateFlow()

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _allowInsecureHttp.value = prefs.getBoolean(KEY_ALLOW_INSECURE_HTTP, false)
        Log.d(TAG, "Initialized: allowInsecureHttp=${_allowInsecureHttp.value}")
    }

    fun isAllowInsecureHttp(): Boolean = _allowInsecureHttp.value

    fun saveAllowInsecureHttp(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ALLOW_INSECURE_HTTP, enabled).apply()
        _allowInsecureHttp.value = enabled
        Log.d(TAG, "Saved: allowInsecureHttp=$enabled")
    }
}
