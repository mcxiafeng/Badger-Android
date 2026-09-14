package top.mcxiafeng.badger.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.mcxiafeng.badger.data.prefs.PrefsStore
import top.mcxiafeng.badger.utils.BadgerLog

enum class EffectMode { NONE, LIQUID_GLASS, BG_BLUR }

private const val TAG = "NavBarConfig"

object NavBarConfig {
    private const val KEY_FLOATING_ENABLED = "nav_bar_floating"
    private const val KEY_ADVANCED_BLUR_ENABLED = "nav_bar_advanced_blur"
    private const val KEY_EFFECT_MODE = "nav_bar_effect_mode"
    private const val KEY_HIDE_LABELS = "nav_bar_hide_labels"

    private val _floatingFlow = MutableStateFlow(true)
    private val _advancedBlurFlow = MutableStateFlow(false)
    private val _effectModeFlow = MutableStateFlow(EffectMode.BG_BLUR)
    private val _hideLabelsFlow = MutableStateFlow(false)

    val floatingFlow: StateFlow<Boolean> = _floatingFlow.asStateFlow()
    val advancedBlurFlow: StateFlow<Boolean> = _advancedBlurFlow.asStateFlow()
    val effectModeFlow: StateFlow<EffectMode> = _effectModeFlow.asStateFlow()
    val hideLabelsFlow: StateFlow<Boolean> = _hideLabelsFlow.asStateFlow()

    fun initialize() {
        _floatingFlow.value = PrefsStore.readBoolean(KEY_FLOATING_ENABLED, true)
        _advancedBlurFlow.value = PrefsStore.readBoolean(KEY_ADVANCED_BLUR_ENABLED, false)
        _effectModeFlow.value = EffectMode.entries.getOrElse(PrefsStore.readInt(KEY_EFFECT_MODE, EffectMode.BG_BLUR.ordinal)) { EffectMode.BG_BLUR }
        _hideLabelsFlow.value = PrefsStore.readBoolean(KEY_HIDE_LABELS, false)
        BadgerLog.d(TAG, "Initialized: floating=${_floatingFlow.value}, advancedBlur=${_advancedBlurFlow.value}, effectMode=${_effectModeFlow.value}, hideLabels=${_hideLabelsFlow.value}")
    }

    fun isFloatingEnabled(): Boolean =
        PrefsStore.readBoolean(KEY_FLOATING_ENABLED, true)

    fun saveFloatingEnabled(enabled: Boolean) {
        PrefsStore.writeBoolean(KEY_FLOATING_ENABLED, enabled)
        _floatingFlow.value = enabled
        BadgerLog.d(TAG, "Saved: floating=$enabled")
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

    
    fun saveHideLabels(enabled: Boolean) {
        PrefsStore.writeBoolean(KEY_HIDE_LABELS, enabled)
        _hideLabelsFlow.value = enabled
        BadgerLog.d(TAG, "Saved: hideLabels=$enabled")
    }
}
