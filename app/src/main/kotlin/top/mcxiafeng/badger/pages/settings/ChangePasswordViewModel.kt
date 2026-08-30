package top.mcxiafeng.badger.pages.settings

import android.util.Log
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

/**
 * 修改密码页 VM。
 *
 * 流程：用户填旧密码 + 新密码 + 确认 → 调 `POST /api/auth/changePassword` →
 * 成功置 [ChangePasswordUiState.success]=true，UI 弹 snackbar 后返回；
 * 失败写 [ChangePasswordUiState.error]。
 */
class ChangePasswordViewModel(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val serverApiFactory: ServerApiFactory = top.mcxiafeng.badger.di.KoinComponentBy.get()

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    fun changePassword(oldPassword: String, newPassword: String, newPasswordAgain: String) {
        if (oldPassword.isBlank() || newPassword.isBlank() || newPasswordAgain.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "请填写所有字段")
            return
        }
        if (newPassword != newPasswordAgain) {
            _uiState.value = _uiState.value.copy(error = "两次输入的新密码不一致")
            return
        }
        if (newPassword.length < 6) {
            _uiState.value = _uiState.value.copy(error = "新密码至少 6 位")
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
                Log.d(TAG, "changePassword OK")
                _uiState.value = _uiState.value.copy(loading = false, success = true)
            }.onFailure { e ->
                // [修复防御] Critical #2: 不吞 CancellationException，遵守 structured concurrency
                if (e is CancellationException) throw e
                Log.w(TAG, "changePassword failed: ${e.javaClass.simpleName}: ${e.message}")
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

    /** 消费成功标记，防止配置变更后 LaunchedEffect 重复触发导航。 */
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
