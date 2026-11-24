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
import top.mcxiafeng.badger.data.repository.GaodeConfig
import top.mcxiafeng.badger.data.repository.GaodeException
import top.mcxiafeng.badger.data.repository.GaodeRegionRepository
import javax.inject.Inject

@Immutable
data class GaodeSettingsUiState(
    val apiKey: String = "",
    val initialized: Boolean = false,
    val dirty: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val hasError: Boolean = false,
)

@HiltViewModel
class GaodeSettingsViewModel @Inject constructor(
    private val config: GaodeConfig,
    private val repo: GaodeRegionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(GaodeSettingsUiState())
    val state = _state.asStateFlow()
    private var savedKey = ""

    init {
        loadKey()
    }

    fun loadKey() = runOperation {
        savedKey = config.getApiKey()
        _state.update { it.copy(apiKey = savedKey, initialized = true, dirty = false) }
        Log.d(TAG, "高德配置已加载")
    }

    fun onKeyChanged(key: String) {
        _state.update {
            if (it.busy || !it.initialized) it else it.copy(
                apiKey = key,
                dirty = key.trim() != savedKey,
                message = null,
                hasError = false,
            )
        }
    }

    fun saveKey() {
        val current = state.value
        if (!current.dirty) return
        runOperation {
            val key = current.apiKey.trim()
            config.setApiKey(key)
            savedKey = key
            _state.update { it.copy(apiKey = key, dirty = false, message = "已保存高德配置") }
        }
    }

    fun testConnection() {
        val current = state.value
        if (current.dirty || savedKey.isBlank()) return
        runOperation {
            val result = repo.queryCountry(TEST_COUNTRY)
            _state.update { it.copy(message = "连接成功，查询到 ${result.children.size} 个下级行政区") }
            Log.d(TAG, "高德连接测试通过")
        }
    }

    private fun runOperation(operation: suspend () -> Unit) {
        if (state.value.busy) return
        _state.update { it.copy(busy = true, message = null, hasError = false) }
        viewModelScope.launch {
            try {
                operation()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "高德配置操作失败，类型=${e.javaClass.simpleName}")
                val message = (e as? GaodeException)?.message ?: "高德配置操作失败，请重试"
                _state.update { it.copy(message = message, hasError = true) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private companion object {
        const val TEST_COUNTRY = "中国"
        const val TAG = "GaodeSettingsViewModelTester"
    }
}
