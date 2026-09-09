package top.mcxiafeng.badger.pages.auth

import androidx.compose.runtime.Immutable
import top.mcxiafeng.badger.network.RegisterPolicy

/**
 * 认证主页模式（登录 / 注册）。
 *
 * 忘记密码不是模式 —— 它是从登录派生的恢复流程，独立为二级页
 * （`Route.ForgotPassword` → [ForgotPasswordScreen]）。
 */
sealed interface AuthMode {
    data object Login : AuthMode
    data object Register : AuthMode
}

@Immutable
sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState

    /** 登录 / 注册成功。 */
    data object SignedIn : AuthUiState

    /** 密码重置成功，忘记密码二级页据此返回认证主页。 */
    data object ResetDone : AuthUiState
    data class Error(val message: String) : AuthUiState
}

/** 登录/注册共用的账号凭据 —— 模式切换不丢已输入内容。 */
@Immutable
data class AuthCredentials(
    val username: String = "",
    val password: String = "",
)

/**
 * 注册表单完整状态：用户输入 + 策略 / 图形验证码 / 邮箱验证码的异步状态。
 * 整体替换（copy）而非散字段 —— reset 与模式归位不再手工罗列二十余个字段。
 */
@Immutable
data class RegisterUiState(
    val email: String = "",
    val passwordAgain: String = "",
    val captchaInput: String = "",
    val emailCodeInput: String = "",
    /** null = 策略未加载；加载中见 [policyLoading]。 */
    val policy: RegisterPolicy? = null,
    val policyLoading: Boolean = false,
    val policyError: String? = null,
    val captchaId: String? = null,
    /** dev 环境明文回显的图形验证码（生产为 null，走图片）。 */
    val captchaCode: String? = null,
    /** [C3 fix] 生产环境验证码 PNG base64，CaptchaCard 渲染为图片。 */
    val captchaImageBase64: String? = null,
    val captchaLoading: Boolean = false,
    val emailCodeCaptchaId: String? = null,
    val sendingEmailCode: Boolean = false,
    val emailCodeSent: Boolean = false,
    val emailCodeHint: String? = null,
)

/** 忘记密码表单完整状态。 */
@Immutable
data class ForgotUiState(
    val email: String = "",
    val code: String = "",
    val newPassword: String = "",
    val newPasswordAgain: String = "",
    /** 发码接口返回的 captchaId，提交时随验证码回传。 */
    val codeCaptchaId: String? = null,
    val sendingCode: Boolean = false,
    val codeSent: Boolean = false,
    val codeHint: String? = null,
)
