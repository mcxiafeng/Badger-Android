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
 * 忘记密码表单卡 —— 邮箱 + 验证码 + 新密码两次。
 * 忘记密码二级页与 SetupStepAccount 共用。
 */
@Composable
internal fun AuthForgotCard(
    viewModel: AuthViewModel,
    enabled: Boolean,
    onSubmit: () -> Unit,
) {
    val forgot by viewModel.forgotForm.collectAsState()
    val state by viewModel.state.collectAsState()
    val isLoading = state is AuthUiState.Loading
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(BadgerSpacing.lg),
        cornerRadius = BadgerRadius.card,
    ) {
        Column {
            FieldLabel("邮箱")
            Spacer(modifier = Modifier.height(BadgerSpacing.xs))
            TextField(
                value = forgot.email,
                onValueChange = viewModel::onForgotEmail,
                label = "注册时使用的邮箱",
                useLabelAsPlaceholder = true,
                enabled = enabled && !forgot.sendingCode,
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
            FieldLabel("邮箱验证码")
            Spacer(modifier = Modifier.height(BadgerSpacing.xs))
            CodeSendRow(
                code = forgot.code,
                onCodeChange = viewModel::onForgotCode,
                codeHint = "6 位数字验证码",
                enabled = enabled && !isLoading,
                sending = forgot.sendingCode,
                onSend = viewModel::sendForgotCode,
            )
            CodeHintText(forgot.codeHint)

            Spacer(modifier = Modifier.height(BadgerSpacing.md))
            FieldLabel("新密码")
            Spacer(modifier = Modifier.height(BadgerSpacing.xs))
            AuthPasswordField(
                value = forgot.newPassword,
                onValueChange = viewModel::onForgotNewPassword,
                hint = "至少 ${AuthValidator.PASSWORD_MIN} 位",
                enabled = enabled && !forgot.sendingCode,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(BadgerSpacing.md))
            FieldLabel("确认新密码")
            Spacer(modifier = Modifier.height(BadgerSpacing.xs))
            AuthPasswordField(
                value = forgot.newPasswordAgain,
                onValueChange = viewModel::onForgotNewPasswordAgain,
                hint = "再输入一次",
                enabled = enabled,
                imeAction = ImeAction.Done,
                onImeAction = {
                    if (enabled && viewModel.canSubmitForgotPassword()) viewModel.resetPassword()
                },
                modifier = Modifier.fillMaxWidth(),
            )

            (state as? AuthUiState.Error)?.let { err ->
                Spacer(modifier = Modifier.height(BadgerSpacing.sm))
                FieldError(err.message)
            }

            Spacer(modifier = Modifier.height(BadgerSpacing.lg))
            Button(
                onClick = onSubmit,
                enabled = enabled && viewModel.canSubmitForgotPassword(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                PrimaryButtonContent(isLoading = isLoading, label = "重置密码")
            }
        }
    }
}
