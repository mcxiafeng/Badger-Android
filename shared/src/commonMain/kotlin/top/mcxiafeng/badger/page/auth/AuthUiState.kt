package top.mcxiafeng.badger.page.auth

import androidx.compose.runtime.Immutable
import top.mcxiafeng.badger.network.auth.RegisterPolicy

/** 登录/注册共用的账号字段；注册专属字段在 [RegisterFormState]。 */
@Immutable
data class AuthCredentials(
    val username: String = "",
    val password: String = "",
)

/** 邮箱验证码发送链路状态（忘记密码页用）。 */
@Immutable
data class CodeDeliveryState(
    val sending: Boolean = false,
    val sent: Boolean = false,
    val hint: String? = null,
)

@Immutable
data class RegisterFormState(
    val email: String = "",
    val passwordAgain: String = "",
    /** GET /registerPolicy 解码结果；null = 尚未拿到（加载中或已失败）。 */
    val policy: RegisterPolicy? = null,
    val policyLoading: Boolean = false,
    val policyFailed: Boolean = false,
)

@Immutable
data class ForgotFormState(
    val email: String = "",
    val code: String = "",
    val newPassword: String = "",
    val newPasswordAgain: String = "",
    val delivery: CodeDeliveryState = CodeDeliveryState(),
)

/** 一次提交（登录/注册/重置密码）的推进状态。 */
@Immutable
sealed interface AuthSubmitState {
    data object Idle : AuthSubmitState
    data object Loading : AuthSubmitState
    data class Error(val message: String) : AuthSubmitState

    /** 成功终态；[SuccessKind] 决定页面后续走向（登出路由 / 切回登录页）。 */
    data class Success(val kind: SuccessKind, val message: String? = null) : AuthSubmitState
}

enum class SuccessKind { LOGIN, REGISTER, PASSWORD_RESET }

/** 表单校验单一来源：UI 置灰与提交判断都走这里。 */
object AuthValidator {

    const val USERNAME_MIN = 3
    const val USERNAME_MAX = 32
    const val PASSWORD_MIN = 8

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun isValidEmail(value: String): Boolean = EMAIL_REGEX.matches(value)

    /** 用户名/邮箱只保留 ASCII 可见字符。 */
    fun sanitizeIdentity(raw: String): String =
        raw.filterNot { it.isISOControl() || it.isWhitespace() || it.code !in 0x21..0x7E }.trim()

    /** 密码保留可见 ASCII（不含空格）。 */
    fun sanitizePassword(raw: String): String =
        raw.filterNot { it.isISOControl() || it.code !in 0x21..0x7E }

    fun sanitizeCode(raw: String): String =
        raw.filterNot { it.isISOControl() || it.isWhitespace() }.trim()

    fun loginBlockReason(username: String, password: String): String? = when {
        username.isBlank() -> "请输入用户名"
        password.isBlank() -> "请输入密码"
        else -> null
    }

    fun registerBlockReason(
        username: String,
        email: String,
        password: String,
        passwordAgain: String,
        policy: RegisterPolicy?,
    ): String? = when {
        username.length !in USERNAME_MIN..USERNAME_MAX -> "用户名长度需 $USERNAME_MIN-$USERNAME_MAX 位字母数字"
        password.length < PASSWORD_MIN -> "密码至少 $PASSWORD_MIN 位"
        !isValidEmail(email) -> "请填写有效邮箱"
        passwordAgain != password -> "两次密码不一致"
        policy == null -> "注册策略加载中，请稍候"
        !policy.allowRegister -> "注册功能已关闭"
        // AuthApi.register 只有 captchaCode 字段，两种验证码当前都无法随注册提交
        policy.requireCaptcha -> "服务端要求图形验证码，当前版本暂不支持"
        policy.requireEmailCode -> "服务端要求邮箱验证码，当前版本暂不支持"
        else -> null
    }

    /** 注册表单的过程提示（边填边提示，与提交门槛分开）。 */
    fun registerHint(
        username: String,
        email: String,
        password: String,
        passwordAgain: String,
    ): String? = when {
        username.isEmpty() -> null
        username.length !in USERNAME_MIN..USERNAME_MAX -> "用户名长度需 $USERNAME_MIN-$USERNAME_MAX 位字母数字"
        password.isEmpty() -> null
        password.length < PASSWORD_MIN -> "密码至少 $PASSWORD_MIN 位"
        email.isEmpty() -> null
        !isValidEmail(email) -> "请填写有效邮箱"
        passwordAgain != password -> "两次密码不一致"
        else -> null
    }

    fun forgotBlockReason(
        email: String,
        code: String,
        newPassword: String,
        newPasswordAgain: String,
    ): String? = when {
        !isValidEmail(email) -> "请填写有效邮箱"
        code.isBlank() -> "请输入邮箱验证码"
        newPassword.length < PASSWORD_MIN -> "新密码至少 $PASSWORD_MIN 位"
        newPasswordAgain != newPassword -> "两次密码不一致"
        else -> null
    }
}
