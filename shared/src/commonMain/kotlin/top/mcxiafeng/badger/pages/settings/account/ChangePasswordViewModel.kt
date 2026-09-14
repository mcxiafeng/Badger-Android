package top.mcxiafeng.badger.pages.settings.account

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.repository.ServerApiFactory
import top.mcxiafeng.badger.network.ApiException
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.shared.util.BadgerDispatchers

class ChangePasswordViewModel(
    private val dispatcher: CoroutineDispatcher = BadgerDispatchers.io,
) : ViewModel() {

    private val serverApiFactory: ServerApiFactory = top.mcxiafeng.badger.di.KoinComponentBy.get()

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    fun changePassword(oldPassword: String, newPassword: String, newPasswordAgain: String) {
        
        if (_uiState.value.loading) {
            BadgerLog.d(TAG, "changePassword: blocked by loading gate")
            return
        }
        if (oldPassword.isBlank() || newPassword.isBlank() || newPasswordAgain.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "请填写所有字段")
            return
        }
        if (newPassword != newPasswordAgain) {
            _uiState.value = _uiState.value.copy(error = "两次输入的新密码不一致")
            return
        }
        if (newPassword.length < 8) {
            _uiState.value = _uiState.value.copy(error = "新密码至少 8 位")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null, success = false)
            val result = runCatching {
                withContext(dispatcher) {
                    serverApiFactory.get().changePassword(oldPassword, newPassword, newPasswordAgain)
                }
            }
            result.onSuccess {
                BadgerLog.d(TAG, "changePassword OK")
                _uiState.value = _uiState.value.copy(loading = false, success = true)
            }.onFailure { e ->
                
                if (e is CancellationException) throw e
                BadgerLog.w(TAG, "changePassword failed: ${e::class.simpleName}: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = when {
                        e is ApiException && e.status == 400 -> e.bodyText ?: "旧密码错误或新密码不合法"
                        e is ApiException -> e.bodyText ?: "修改失败 (${e.status})"
                        else -> e.message ?: "修改失败"
                    },
                )
            }
        }
    }

    fun clearError() {
        if (_uiState.value.error != null) {
            _uiState.value = _uiState.value.copy(error = null)
        }
    }

    
    fun consumeSuccess() {
        if (_uiState.value.success) {
            _uiState.value = _uiState.value.copy(success = false)
        }
    }

    companion object {
        private const val TAG = "ChangePasswordVM"
    }
}

@Immutable
data class ChangePasswordUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
)