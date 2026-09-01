package top.mcxiafeng.badger.ui.navigation

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.mcxiafeng.badger.ui.blur.BlurIntensity

enum class EffectMode { NONE, LIQUID_GLASS, BG_BLUR }

private const val TAG = "NavBarConfig"

object NavBarConfig {
    private const val PREFS_NAME = "badger_settings"
    private const val KEY_FLOATING_ENABLED = "nav_bar_floating"
    private const val KEY_LIQUID_GLASS_ENABLED = "nav_bar_liquid_glass"
    private const val KEY_BLUR_INTENSITY = "nav_bar_blur_intensity"
    private const val KEY_ADVANCED_BLUR_ENABLED = "nav_bar_advanced_blur"
    private const val KEY_EFFECT_MODE = "nav_bar_effect_mode"
    private const val KEY_BLUR_RADIUS_DP = "nav_bar_blur_radius_dp"

    private const val DEFAULT_BLUR_RADIUS_DP = 12f
    private const val MIN_BLUR_RADIUS_DP = 0f
    private const val MAX_BLUR_RADIUS_DP = 64f

    private val _floatingFlow = MutableStateFlow(true)
    private val _liquidGlassFlow = MutableStateFlow(true)
    private val _blurIntensityFlow = MutableStateFlow(BlurIntensity.THICK)
    private val _advancedBlurFlow = MutableStateFlow(false)
    private val _effectModeFlow = MutableStateFlow(EffectMode.BG_BLUR)
    private val _blurRadiusDpFlow = MutableStateFlow(DEFAULT_BLUR_RADIUS_DP)

    val floatingFlow: StateFlow<Boolean> = _floatingFlow.asStateFlow()
    val liquidGlassFlow: StateFlow<Boolean> = _liquidGlassFlow.asStateFlow()
    val blurIntensityFlow: StateFlow<BlurIntensity> = _blurIntensityFlow.asStateFlow()
    val advancedBlurFlow: StateFlow<Boolean> = _advancedBlurFlow.asStateFlow()
    val effectModeFlow: StateFlow<EffectMode> = _effectModeFlow.asStateFlow()
    val blurRadiusDpFlow: StateFlow<Float> = _blurRadiusDpFlow.asStateFlow()

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _floatingFlow.value = prefs.getBoolean(KEY_FLOATING_ENABLED, true)
        _liquidGlassFlow.value = prefs.getBoolean(KEY_LIQUID_GLASS_ENABLED, true)
        _blurIntensityFlow.value = readBlurIntensity(prefs)
        _advancedBlurFlow.value = prefs.getBoolean(KEY_ADVANCED_BLUR_ENABLED, false)
        _effectModeFlow.value = readEffectMode(prefs)
        _blurRadiusDpFlow.value = readBlurRadiusDp(prefs)
        Log.d(
            TAG,
            "Initialized: floating=${_floatingFlow.value}, liquidGlass=${_liquidGlassFlow.value}, " +
                "blurIntensity=${_blurIntensityFlow.value}, advancedBlur=${_advancedBlurFlow.value}, " +
                "effectMode=${_effectModeFlow.value}, blurRadiusDp=${_blurRadiusDpFlow.value}",
        )
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

    fun isLiquidGlassEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_LIQUID_GLASS_ENABLED, true)
    }

    fun saveLiquidGlassEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_LIQUID_GLASS_ENABLED, enabled).apply()
        _liquidGlassFlow.value = enabled
        Log.d(TAG, "Saved: liquidGlass=$enabled")
    }

    fun saveBlurIntensity(context: Context, intensity: BlurIntensity) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BLUR_INTENSITY, intensity.name).apply()
        _blurIntensityFlow.value = intensity
        Log.d(TAG, "Saved: blurIntensity=$intensity")
    }

    fun saveAdvancedBlurEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ADVANCED_BLUR_ENABLED, enabled).apply()
        _advancedBlurFlow.value = enabled
        Log.d(TAG, "Saved: advancedBlur=$enabled")
    }

    fun saveEffectMode(context: Context, mode: EffectMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_EFFECT_MODE, mode.name).apply()
        _effectModeFlow.value = mode
        Log.d(TAG, "Saved: effectMode=$mode")
    }

    fun saveBlurRadiusDp(context: Context, dp: Float) {
        val normalized = normalizeBlurRadius(dp)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_BLUR_RADIUS_DP, normalized).apply()
        _blurRadiusDpFlow.value = normalized
        Log.d(TAG, "Saved: blurRadiusDp=$normalized")
    }

    private fun readBlurIntensity(prefs: SharedPreferences): BlurIntensity {
        return when (val rawValue = prefs.all[KEY_BLUR_INTENSITY]) {
            is String -> BlurIntensity.entries.firstOrNull { it.name == rawValue } ?: BlurIntensity.THICK
            is Int -> BlurIntensity.entries.getOrElse(rawValue) { BlurIntensity.THICK }
            else -> BlurIntensity.THICK
        }
    }

    private fun readEffectMode(prefs: SharedPreferences): EffectMode {
        return when (val rawValue = prefs.all[KEY_EFFECT_MODE]) {
            is String -> EffectMode.entries.firstOrNull { it.name == rawValue } ?: EffectMode.BG_BLUR
            is Int -> EffectMode.entries.getOrElse(rawValue) { EffectMode.BG_BLUR }
            else -> EffectMode.BG_BLUR
        }
    }

    private fun readBlurRadiusDp(prefs: SharedPreferences): Float {
        val rawValue = prefs.all[KEY_BLUR_RADIUS_DP]
        val radius = when (rawValue) {
            is Float -> rawValue
            is Int -> rawValue.toFloat()
            is Long -> rawValue.toFloat()
            is String -> rawValue.toFloatOrNull() ?: DEFAULT_BLUR_RADIUS_DP
            else -> DEFAULT_BLUR_RADIUS_DP
        }
        return normalizeBlurRadius(radius)
    }

    private fun normalizeBlurRadius(dp: Float): Float =
        dp.coerceIn(MIN_BLUR_RADIUS_DP, MAX_BLUR_RADIUS_DP)
}