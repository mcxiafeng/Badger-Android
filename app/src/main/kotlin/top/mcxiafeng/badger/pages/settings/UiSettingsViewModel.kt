package top.mcxiafeng.badger.pages.settings

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.AppearanceMode
import top.mcxiafeng.badger.data.AppearancePreferences
import top.mcxiafeng.badger.ui.navigation.NavBarConfig
import javax.inject.Inject

@HiltViewModel
class UiSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appearancePreferences: AppearancePreferences,
) : ViewModel() {
    init {
        NavBarConfig.initialize(context)
        Log.d(TAG, "UI settings initialized")
    }

    val floatingEnabled: StateFlow<Boolean> = NavBarConfig.floatingFlow
    val liquidEnabled: StateFlow<Boolean> = NavBarConfig.liquidFlow

    /**
     * 深浅模式是否跟随系统（默认开启）。「我的」页顶栏快切到显式深/浅色时自动变回关闭。
     */
    val followSystem: StateFlow<Boolean> = appearancePreferences.changes
        .map { it.mode == AppearanceMode.System }
        .stateIn(viewModelScope, SharingStarted.Eagerly, appearancePreferences.read().mode == AppearanceMode.System)

    fun setFloatingEnabled(enabled: Boolean) {
        Log.d(TAG, "Floating navigation requested: $enabled")
        NavBarConfig.saveFloatingEnabled(context, enabled)
    }

    fun setLiquidEnabled(enabled: Boolean) {
        Log.d(TAG, "Liquid navigation requested: $enabled")
        NavBarConfig.saveLiquidEnabled(context, enabled)
    }

    /** 关闭跟随系统时落到当前生效的深浅色，保证视觉不突变。 */
    fun setFollowSystem(enabled: Boolean) {
        val target = when {
            enabled -> AppearanceMode.System
            else -> currentModeOrEffective()
        }
        Log.d(TAG, "Follow system requested: $enabled; target=$target")
        viewModelScope.launch {
            try {
                appearancePreferences.setMode(target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "保存跟随系统开关失败", e)
            }
        }
    }

    private fun currentModeOrEffective(): AppearanceMode {
        val mode = appearancePreferences.read().mode
        if (mode != AppearanceMode.System) return mode
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        return if (night) AppearanceMode.Dark else AppearanceMode.Light
    }

    private companion object {
        const val TAG = "UiSettingsViewModelTester"
    }
}
