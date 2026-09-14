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
