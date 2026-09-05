package top.mcxiafeng.badger.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.mcxiafeng.badger.data.prefs.PrefsStore
import top.mcxiafeng.badger.ui.blur.BlurIntensity
import top.mcxiafeng.badger.utils.BadgerLog

enum class EffectMode { NONE, LIQUID_GLASS, BG_BLUR }

private const val TAG = "NavBarConfig"

/**
 * [KMP K05] DataStore Preferences（经 PrefsStore 内存缓存），原 badger_settings 文件。
 */
object NavBarConfig {
    private const val KEY_FLOATING_ENABLED = "nav_bar_floating"
    private const val KEY_LIQUID_GLASS_ENABLED = "nav_bar_liquid_glass"
    private const val KEY_BLUR_INTENSITY = "nav_bar_blur_intensity"
    private const val KEY_ADVANCED_BLUR_ENABLED = "nav_bar_advanced_blur"
    private const val KEY_EFFECT_MODE = "nav_bar_effect_mode"
    private const val KEY_BLUR_RADIUS_DP = "nav_bar_blur_radius_dp"

    private val _floatingFlow = MutableStateFlow(true)
    private val _liquidGlassFlow = MutableStateFlow(true)
    private val _blurIntensityFlow = MutableStateFlow(BlurIntensity.THICK)
    private val _advancedBlurFlow = MutableStateFlow(false)
    private val _effectModeFlow = MutableStateFlow(EffectMode.BG_BLUR)
    private val _blurRadiusDpFlow = MutableStateFlow(12f)

    val floatingFlow: StateFlow<Boolean> = _floatingFlow.asStateFlow()
    val liquidGlassFlow: StateFlow<Boolean> = _liquidGlassFlow.asStateFlow()
    val blurIntensityFlow: StateFlow<BlurIntensity> = _blurIntensityFlow.asStateFlow()
    val advancedBlurFlow: StateFlow<Boolean> = _advancedBlurFlow.asStateFlow()
    val effectModeFlow: StateFlow<EffectMode> = _effectModeFlow.asStateFlow()
    val blurRadiusDpFlow: StateFlow<Float> = _blurRadiusDpFlow.asStateFlow()

    fun initialize() {
        _floatingFlow.value = PrefsStore.readBoolean(KEY_FLOATING_ENABLED, true)
        _liquidGlassFlow.value = PrefsStore.readBoolean(KEY_LIQUID_GLASS_ENABLED, true)
        _blurIntensityFlow.value = BlurIntensity.entries.getOrElse(PrefsStore.readInt(KEY_BLUR_INTENSITY, 1)) { BlurIntensity.THICK }
        _advancedBlurFlow.value = PrefsStore.readBoolean(KEY_ADVANCED_BLUR_ENABLED, false)
        _effectModeFlow.value = EffectMode.entries.getOrElse(PrefsStore.readInt(KEY_EFFECT_MODE, EffectMode.BG_BLUR.ordinal)) { EffectMode.BG_BLUR }
        _blurRadiusDpFlow.value = PrefsStore.readFloat(KEY_BLUR_RADIUS_DP, 12f)
        BadgerLog.d(TAG, "Initialized: floating=${_floatingFlow.value}, liquidGlass=${_liquidGlassFlow.value}, blurIntensity=${_blurIntensityFlow.value}, advancedBlur=${_advancedBlurFlow.value}, effectMode=${_effectModeFlow.value}, blurRadiusDp=${_blurRadiusDpFlow.value}")
    }

    fun isFloatingEnabled(): Boolean =
        PrefsStore.readBoolean(KEY_FLOATING_ENABLED, true)

    fun saveFloatingEnabled(enabled: Boolean) {
        PrefsStore.writeBoolean(KEY_FLOATING_ENABLED, enabled)
        _floatingFlow.value = enabled
        BadgerLog.d(TAG, "Saved: floating=$enabled")
    }

    fun isLiquidGlassEnabled(): Boolean =
        PrefsStore.readBoolean(KEY_LIQUID_GLASS_ENABLED, true)

    fun saveLiquidGlassEnabled(enabled: Boolean) {
        PrefsStore.writeBoolean(KEY_LIQUID_GLASS_ENABLED, enabled)
        _liquidGlassFlow.value = enabled
        BadgerLog.d(TAG, "Saved: liquidGlass=$enabled")
    }

    fun saveBlurIntensity(intensity: BlurIntensity) {
        PrefsStore.writeInt(KEY_BLUR_INTENSITY, intensity.ordinal)
        _blurIntensityFlow.value = intensity
        BadgerLog.d(TAG, "Saved: blurIntensity=$intensity")
    }

    fun saveAdvancedBlurEnabled(enabled: Boolean) {
        PrefsStore.writeBoolean(KEY_ADVANCED_BLUR_ENABLED, enabled)
        _advancedBlurFlow.value = enabled
        BadgerLog.d(TAG, "Saved: advancedBlur=$enabled")
    }

    fun saveEffectMode(mode: EffectMode) {
        PrefsStore.writeInt(KEY_EFFECT_MODE, mode.ordinal)
        _effectModeFlow.value = mode
        BadgerLog.d(TAG, "Saved: effectMode=$mode")
    }

    fun saveBlurRadiusDp(dp: Float) {
        PrefsStore.writeFloat(KEY_BLUR_RADIUS_DP, dp)
        _blurRadiusDpFlow.value = dp
    }
}
