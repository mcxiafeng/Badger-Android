package top.mcxiafeng.badger.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.mcxiafeng.badger.data.prefs.PrefsStore
import top.mcxiafeng.badger.utils.BadgerLog

/**
 * [K14 / 特效规格 §6] 三档效果模式（enum 顺序即 DataStore 持久化 ordinal，勿重排）：
 * - [NONE]：无——纯色 surface，无任何采样
 * - [LIQUID_GLASS]：液态玻璃——完整液态（L1–L7，折射部分需 GpuCompat + 用户开启高级）
 * - [BG_BLUR]：标准磨砂——L1–L3 + L5（家族 A 磨砂，无折射无镜面）
 */
enum class EffectMode { NONE, LIQUID_GLASS, BG_BLUR }

private const val TAG = "NavBarConfig"

/**
 * [KMP K05] DataStore Preferences（经 PrefsStore 内存缓存），原 badger_settings 文件。
 */
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

    /** [用户裁决 2026-09-06] 常驻隐藏导航栏文字标签（纯图标形态），与滚动无关，默认关闭 */
    fun saveHideLabels(enabled: Boolean) {
        PrefsStore.writeBoolean(KEY_HIDE_LABELS, enabled)
        _hideLabelsFlow.value = enabled
        BadgerLog.d(TAG, "Saved: hideLabels=$enabled")
    }
}
