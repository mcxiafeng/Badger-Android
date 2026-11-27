package top.mcxiafeng.badger.pages.settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import top.mcxiafeng.badger.ui.navigation.NavBarConfig
import javax.inject.Inject

@HiltViewModel
class UiSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    init {
        NavBarConfig.initialize(context)
        Log.d(TAG, "UI settings initialized")
    }

    val floatingEnabled: StateFlow<Boolean> = NavBarConfig.floatingFlow

    fun setFloatingEnabled(enabled: Boolean) {
        Log.d(TAG, "Floating navigation requested: $enabled")
        NavBarConfig.saveFloatingEnabled(context, enabled)
    }

    private companion object {
        const val TAG = "UiSettingsViewModelTester"
    }
}
