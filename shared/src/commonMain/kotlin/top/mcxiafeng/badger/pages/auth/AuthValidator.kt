package top.mcxiafeng.badger.pages.auth

import top.mcxiafeng.badger.network.RegisterPolicy

/**
 * Auth 域验证单一来源。
 *
 * - `sanitize*` 供 VM 的 on* 输入处理器使用（UI 不自行过滤）；
 * - `blockReason*` / `registerHint` 是 canSubmit*、UI 实时 hint、提交兜底三处的唯一实现，
 *   规则变更只改这里，禁止在 Composable 或提交分支里再抄一份。
 */
internal object AuthValidator {

    const val USERNAME_MIN = 3
    const val USERNAME_MAX = 32
    const val PASSWORD_MIN = 8

    /** 简化 RFC 5322：local@domain.tld，tld 至少 2 个字母。 */
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun isValidEmail(value: String): Boolean = EMAIL_REGEX.matches(value)

    /**
     * 用户名 / 邮箱清洗：仅 ASCII 可见字符（0x21..0x7E），禁空白与控制字符，trim 收尾。
     * 与服务端 `[a-zA-Z0-9_-]{3,32}` 契约对齐，避免「输入了中文但按钮一直 disabled」的迷惑态。
     */
    fun sanitizeIdentity(raw: String): String =
        raw.filterNot { it.isISOControl() || it.isWhitespace() || it.code !in 0x21..0x7E }.trim()

    /**
     * 密码清洗：ASCII 可见字符（0x21..0x7E），空格一并剔除 —— 移动端 IME 下首尾空格
     * 几乎不会被主动使用，却会跟复制粘贴、跨设备同步产生歧义（对齐旧实现语义）。
     */
    fun sanitizePassword(raw: String): String =
        raw.filterNot { it.isISOControl() || it.code !in 0x21..0x7E }

    /** 验证码清洗：滤空白与控制字符。 */
    fun sanitizeCode(raw: String): String =
        raw.filterNot { it.isISOControl() || it.isWhitespace() }.trim()

    /** 登录阻断原因；null = 可提交。 */
    fun loginBlockReason(username: String, password: String): String? = when {
        username.isBlank() -> "请输入用户名"
        password.isBlank() -> "请输入密码"
        else -> null
    }

    /** 注册阻断原因（不含 busy 态，busy 由 VM 层拦截）；null = 可提交。 */
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

    /**
     * 注册表单实时 hint：与 [registerBlockReason] 同规则，但空字段不打扰
     * （用户还没输到的字段不提前报错，只提示当前正在输入的阻断项）。
     */
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

    /** 忘记密码阻断原因（不含 busy 态）；null = 可提交。 */
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
