package top.mcxiafeng.badger.pages.settings

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.AppearanceAccent
import top.mcxiafeng.badger.data.AppearanceMode
import top.mcxiafeng.badger.data.AppearancePreferences
import top.mcxiafeng.badger.data.AppearanceState
import javax.inject.Inject

@Immutable
data class AppearanceUiState(
    val appearance: AppearanceState = AppearanceState(),
    val errorMessage: String? = null,
)

/** 主题与设置各自观察同一份设备偏好，UI 不直接读写存储。 */
@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val preferences: AppearancePreferences,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(AppearanceUiState(preferences.read()))
    val uiState = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.changes.collect { appearance ->
                Log.d(TAG, "应用外观状态：${appearance.mode}/${appearance.accent}")
                mutableUiState.update { it.copy(appearance = appearance) }
            }
        }
    }

    fun setMode(mode: AppearanceMode) = save("深浅模式") { preferences.setMode(mode) }

    fun setAccent(accent: AppearanceAccent) = save("强调色") { preferences.setAccent(accent) }

    private fun save(label: String, action: suspend () -> Unit) {
        Log.d(TAG, "请求保存$label")
        viewModelScope.launch {
            try {
                action()
                mutableUiState.update { it.copy(errorMessage = null) }
                Log.d(TAG, "保存${label}完成")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "保存${label}失败", e)
                // SharedPreferences 在磁盘写入失败时也可能已更新内存，重新读取以保持界面一致。
                mutableUiState.update {
                    it.copy(appearance = preferences.read(), errorMessage = "保存${label}失败，请重试")
                }
            }
        }
    }

    private companion object {
        const val TAG = "AppearanceViewModelTester"
    }
}
