package top.mcxiafeng.badger.pages.auth

import top.mcxiafeng.badger.network.RegisterPolicy

internal object AuthValidator {

    const val USERNAME_MIN = 3
    const val USERNAME_MAX = 32
    const val PASSWORD_MIN = 8

    
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun isValidEmail(value: String): Boolean = EMAIL_REGEX.matches(value)

    

    fun sanitizeIdentity(raw: String): String =
        raw.filterNot { it.isISOControl() || it.isWhitespace() || it.code !in 0x21..0x7E }.trim()

    

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
        captchaInput: String,
        emailCodeInput: String,
    ): String? = when {
        username.length < USERNAME_MIN || username.length > USERNAME_MAX ->
            "用户名长度需 $USERNAME_MIN-$USERNAME_MAX 字符"
        password.length < PASSWORD_MIN -> "密码至少 $PASSWORD_MIN 位"
        !isValidEmail(email) -> "请填写有效邮箱"
        passwordAgain != password -> "两次密码不一致"
        policy == null -> "注册策略加载中，请稍候"
        !policy.allowRegister -> "注册功能已关闭"
        policy.requireCaptcha && captchaInput.isBlank() -> "请输入图形验证码"
        policy.requireEmailCode && emailCodeInput.isBlank() -> "请输入邮箱验证码"
        else -> null
    }

    

    fun registerHint(
        username: String,
        email: String,
        password: String,
        passwordAgain: String,
    ): String? = when {
        username.isEmpty() -> null
        username.length < USERNAME_MIN || username.length > USERNAME_MAX ->
            "用户名长度需 $USERNAME_MIN-$USERNAME_MAX 字符"
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
        code.isBlank() -> "请输入验证码"
        newPassword.length < PASSWORD_MIN -> "新密码至少 $PASSWORD_MIN 位"
        newPasswordAgain != newPassword -> "两次密码不一致"
        else -> null
    }
}
