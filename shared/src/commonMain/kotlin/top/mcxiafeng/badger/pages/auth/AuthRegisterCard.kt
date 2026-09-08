package top.mcxiafeng.badger.pages.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.TextField

/**
 * 注册表单卡 —— 用户名 / 邮箱 / 密码 + 策略驱动的扩展字段（[RegisterExtraFields]）。
 *
 * 实时 hint 与提交兜底共用 [AuthValidator]，卡片内不再手写校验规则。
 */
@Composable
internal fun AuthRegisterCard(
    viewModel: AuthViewModel,
    enabled: Boolean,
    onSubmit: () -> Unit,
) {
    val credentials by viewModel.credentials.collectAsState()
    val register by viewModel.registerState.collectAsState()
    val state by viewModel.state.collectAsState()
    val isLoading = state is AuthUiState.Loading
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(BadgerSpacing.lg),
        cornerRadius = BadgerRadius.card,
    ) {
        Column {
            FieldLabel("用户名")
            Spacer(modifier = Modifier.height(BadgerSpacing.xs))
            TextField(
                value = credentials.username,
                onValueChange = viewModel::onUsername,
                label = "${AuthValidator.USERNAME_MIN} - ${AuthValidator.USERNAME_MAX} 位字母数字下划线",
                useLabelAsPlaceholder = true,
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(BadgerSpacing.md))
            FieldLabel("邮箱")
            Spacer(modifier = Modifier.height(BadgerSpacing.xs))
            TextField(
                value = register.email,
                onValueChange = viewModel::onEmail,
                label = "example@domain.com",
                useLabelAsPlaceholder = true,
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(BadgerSpacing.md))
            FieldLabel("密码")
            Spacer(modifier = Modifier.height(BadgerSpacing.xs))
            AuthPasswordField(
                value = credentials.password,
                onValueChange = viewModel::onPassword,
                hint = "至少 ${AuthValidator.PASSWORD_MIN} 位",
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )

            // ---- 策略驱动扩展区（确认密码 / 图形验证码 / 邮箱验证码） ----
            Spacer(modifier = Modifier.height(BadgerSpacing.sm))
            RegisterExtraFields(
                state = register,
                enabled = enabled,
                onPasswordAgain = viewModel::onPasswordAgain,
                onCaptchaInput = viewModel::onCaptchaInput,
                onEmailCodeInput = viewModel::onEmailCodeInput,
                onRefreshCaptcha = viewModel::refreshCaptcha,
                onSendEmailCode = viewModel::sendEmailCode,
            )

            // ---- 实时校验 hint：空字段不打扰，仅提示当前阻断项 ----
            val hint = AuthValidator.registerHint(
                username = credentials.username,
                email = register.email,
                password = credentials.password,
                passwordAgain = register.passwordAgain,
            )
            if (hint != null) {
                Spacer(modifier = Modifier.height(BadgerSpacing.xs))
                FieldError(hint)
            }

            // ---- 提交级错误（网络 / 服务端） ----
            (state as? AuthUiState.Error)?.let { err ->
                Spacer(modifier = Modifier.height(BadgerSpacing.sm))
                FieldError(err.message)
            }

            Spacer(modifier = Modifier.height(BadgerSpacing.lg))
            Button(
                onClick = onSubmit,
                enabled = enabled && viewModel.canSubmitRegister(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                PrimaryButtonContent(isLoading = isLoading, label = "注册")
            }
        }
    }
}
