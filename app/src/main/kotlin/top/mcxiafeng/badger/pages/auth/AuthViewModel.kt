package top.mcxiafeng.badger.pages.auth

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import javax.inject.Inject

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data object SignedIn : AuthUiState
    data class Error(val message: String) : AuthUiState
}

/**
 * Shared VM for the LoginScreen / RegisterScreen. The two screens use
 * distinct hiltViewModel keys so their username/password/email inputs do
 * not bleed across navigation.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userAuthRepository: UserAuthRepository,
) : ViewModel() {

    val username: MutableState<String> = mutableStateOf("")
    val email: MutableState<String> = mutableStateOf("")
    val password: MutableState<String> = mutableStateOf("")

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    val onUsername: (String) -> Unit = { username.value = it.trim() }
    val onEmail: (String) -> Unit = { email.value = it.trim() }
    val onPassword: (String) -> Unit = { password.value = it }

    fun reset() {
        username.value = ""
        email.value = ""
        password.value = ""
        _state.value = AuthUiState.Idle
    }

    fun signIn() {
        if (username.value.isBlank() || password.value.isBlank()) {
            _state.value = AuthUiState.Error("用户名和密码不能为空")
            return
        }
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            val r = userAuthRepository.login(username.value, password.value)
            _state.value = r.fold(
                onSuccess = { AuthUiState.SignedIn },
                onFailure = { AuthUiState.Error(it.message ?: "登录失败") },
            )
        }
    }

    fun register() {
        val u = username.value
        if (u.length < 3 || u.length > 32) {
            _state.value = AuthUiState.Error("用户名长度需 3-32 字符")
            return
        }
        if (password.value.length < 8) {
            _state.value = AuthUiState.Error("密码至少 8 位")
            return
        }
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            val r = userAuthRepository.register(
                username = u,
                password = password.value,
                email = email.value.takeIf { it.isNotBlank() },
                displayName = null,
            )
            _state.value = r.fold(
                onSuccess = { AuthUiState.SignedIn },
                onFailure = { AuthUiState.Error(it.message ?: "注册失败") },
            )
        }
    }
}
