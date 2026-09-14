package top.mcxiafeng.badger.pages.auth

import androidx.compose.runtime.Immutable
import top.mcxiafeng.badger.network.RegisterPolicy

sealed interface AuthMode {
    data object Login : AuthMode
    data object Register : AuthMode
}

@Immutable
sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState

    
    data object SignedIn : AuthUiState

    
    data object ResetDone : AuthUiState
    data class Error(val message: String) : AuthUiState
}

@Immutable
data class AuthCredentials(
    val username: String = "",
    val password: String = "",
)

@Immutable
data class RegisterUiState(
    val email: String = "",
    val passwordAgain: String = "",
    val captchaInput: String = "",
    val emailCodeInput: String = "",
    
    val policy: RegisterPolicy? = null,
    val policyLoading: Boolean = false,
    val policyError: String? = null,
    val captchaId: String? = null,
    
    val captchaCode: String? = null,
    
    val captchaImageBase64: String? = null,
    val captchaLoading: Boolean = false,
    val emailCodeCaptchaId: String? = null,
    val sendingEmailCode: Boolean = false,
    val emailCodeSent: Boolean = false,
    val emailCodeHint: String? = null,
)

@Immutable
data class ForgotUiState(
    val email: String = "",
    val code: String = "",
    val newPassword: String = "",
    val newPasswordAgain: String = "",
    
    val codeCaptchaId: String? = null,
    val sendingCode: Boolean = false,
    val codeSent: Boolean = false,
    val codeHint: String? = null,
)
