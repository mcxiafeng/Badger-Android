package top.mcxiafeng.badger.pages.server

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.network.HttpResult
import top.mcxiafeng.badger.network.core.PublicApi
import top.mcxiafeng.badger.network.core.ServerAddress
import top.mcxiafeng.badger.network.core.ServerAddressStore
import top.mcxiafeng.badger.utils.SafeLog
import javax.inject.Inject

private const val TAG = "ChoiceServerViewModelTester"

/**
 * 选择服务器页 VM（移植自 dev 分支 page/server/ChoiceServerViewModel）：
 * 手输地址 → /api/info 解析验证 → 激活并记历史 → 前往登录。
 */
@HiltViewModel
class ChoiceServerViewModel @Inject constructor(
    private val addressStore: ServerAddressStore,
    private val publicApi: PublicApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChoiceServerUiState())
    val uiState: StateFlow<ChoiceServerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            addressStore.history.collect { history ->
                _uiState.update { it.copy(history = history) }
            }
        }
    }

    fun onInput(raw: String) {
        val (text, scheme) = ServerValidator.normalizeInput(raw, _uiState.value.https)
        _uiState.update { it.copy(input = text, https = scheme, urlReason = ServerValidator.blockReason(text)) }
    }

    fun onHttpsChange(enabled: Boolean) {
        _uiState.update { it.copy(https = enabled, urlReason = ServerValidator.blockReason(_uiState.value.input)) }
    }

    fun removeHistory(entry: String) {
        addressStore.removeHistory(entry)
    }

    /** 返回拦截原因；null = 通过（已激活该地址并记入历史）。 */
    suspend fun proceed(): String? {
        val state = _uiState.value
        if (!state.canProceed) return state.urlReason ?: "请输入地址"
        val url = ServerValidator.composeUrl(ServerValidator.withDefaultPort(state.input), state.https)
        Log.d(TAG, "proceed: 验证 ${SafeLog.url(url)}")
        _uiState.update { it.copy(resolving = true) }
        try {
            val result = publicApi.getDirect(url, PublicApi.INFO_PATH)
            val error = when (result) {
                is HttpResult.Success -> null
                is HttpResult.Failure -> when (result.errorType) {
                    HttpResult.ErrorType.TIMEOUT -> "连接超时，请检查地址或网络"
                    HttpResult.ErrorType.NETWORK -> "无法连接到服务器"
                    else -> "服务器异常（HTTP ${result.code}）"
                }
            }
            if (error != null) {
                Log.w(TAG, "proceed 被拦：$error（${SafeLog.url(url)}）")
                return error
            }
            addressStore.setActive(url)
            addressStore.addHistory(url)
            publicApi.recordSuccessBase(url)
            Log.d(TAG, "proceed: 服务器就绪 ${SafeLog.url(url)}")
            return null
        } finally {
            _uiState.update { it.copy(resolving = false) }
        }
    }
}
