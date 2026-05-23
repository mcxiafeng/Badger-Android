package top.mcxiafeng.badger.ui.navigation

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "NavBarConfig"

object NavBarConfig {
    private const val PREFS_NAME = "badger_settings"
    private const val KEY_FLOATING_ENABLED = "nav_bar_floating"
    private const val KEY_BLUR_ENABLED = "nav_bar_blur"
    private const val KEY_LIQUID_GLASS_ENABLED = "nav_bar_liquid_glass"

    private val _floatingFlow = MutableStateFlow(false)
    private val _blurFlow = MutableStateFlow(false)
    private val _liquidGlassFlow = MutableStateFlow(false)

    // 系统是否允许跨窗口模糊（Android 16 "减少模糊效果"设置、省电模式都会使其返回 false）
    private val _systemBlurEnabledFlow = MutableStateFlow(true)

    val floatingFlow: StateFlow<Boolean> = _floatingFlow.asStateFlow()
    val blurFlow: StateFlow<Boolean> = _blurFlow.asStateFlow()
    val liquidGlassFlow: StateFlow<Boolean> = _liquidGlassFlow.asStateFlow()

    // 系统级模糊是否可用（受 Android 16 "减少模糊效果" 和省电模式影响）
    val systemBlurEnabledFlow: StateFlow<Boolean> = _systemBlurEnabledFlow.asStateFlow()

    // 模糊是否真正可用：用户开启 + 系统允许
    val blurAvailableFlow: StateFlow<Boolean> get() = _blurAvailableFlow
    val liquidGlassAvailableFlow: StateFlow<Boolean> get() = _liquidGlassAvailableFlow

    private val _blurAvailableFlow = MutableStateFlow(false)
    private val _liquidGlassAvailableFlow = MutableStateFlow(false)

    fun isBlurSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun isLensSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _floatingFlow.value = prefs.getBoolean(KEY_FLOATING_ENABLED, false)
        val defaultBlur = isBlurSupported()
        _blurFlow.value = prefs.getBoolean(KEY_BLUR_ENABLED, defaultBlur)
        _liquidGlassFlow.value = prefs.getBoolean(KEY_LIQUID_GLASS_ENABLED, false)
        Log.d(TAG, "Initialized: floating=${_floatingFlow.value}, blur=${_blurFlow.value}, liquidGlass=${_liquidGlassFlow.value}")

        // 注册跨窗口模糊状态监听（Android 16 "减少模糊效果" 和省电模式）
        registerCrossWindowBlurListener(context)
    }

    /** 注册系统模糊开关监听，动态响应 Android 16 "减少模糊效果"设置和省电模式 */
    private fun registerCrossWindowBlurListener(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            // 初始值
            _systemBlurEnabledFlow.value = windowManager.isCrossWindowBlurEnabled
            Log.d(TAG, "isCrossWindowBlurEnabled: ${windowManager.isCrossWindowBlurEnabled}")
            updateAvailableFlows()

            windowManager.addCrossWindowBlurEnabledListener { enabled ->
                _systemBlurEnabledFlow.value = enabled
                updateAvailableFlows()
                Log.d(TAG, "CrossWindowBlurEnabled changed: $enabled")
            }
        }
    }

    private fun updateAvailableFlows() {
        val systemEnabled = _systemBlurEnabledFlow.value
        _blurAvailableFlow.value = _blurFlow.value && systemEnabled
        _liquidGlassAvailableFlow.value = _liquidGlassFlow.value && systemEnabled
    }

    fun isFloatingEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_FLOATING_ENABLED, false)
    }

    fun isBlurEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_BLUR_ENABLED, isBlurSupported())
    }

    fun isLiquidGlassEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_LIQUID_GLASS_ENABLED, false)
    }

    fun isSystemBlurEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            return windowManager.isCrossWindowBlurEnabled
        }
        return true
    }

    fun saveFloatingEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_FLOATING_ENABLED, enabled).apply()
        _floatingFlow.value = enabled
        Log.d(TAG, "Saved: floating=$enabled")
    }

    fun saveBlurEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_BLUR_ENABLED, enabled).apply()
        _blurFlow.value = enabled
        updateAvailableFlows()
        Log.d(TAG, "Saved: blur=$enabled")
    }

    fun saveLiquidGlassEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_LIQUID_GLASS_ENABLED, enabled).apply()
        _liquidGlassFlow.value = enabled
        updateAvailableFlows()
        Log.d(TAG, "Saved: liquidGlass=$enabled")
    }
}