package top.mcxiafeng.badger.pages.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 忘记密码表单 —— 邮箱、邮箱验证码、新密码两次。
 */
@Composable
internal fun ForgotPasswordContent(
    viewModel: AuthViewModel,
    enabled: Boolean,
    state: AuthUiState,
    onBackToLogin: () -> Unit,
) {
    val isLoading = state is AuthUiState.Loading
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(BadgerSpacing.lg),
        cornerRadius = BadgerRadius.xl,
    ) {
        Column {
            FieldLabel("邮箱")
            Spacer(modifier = Modifier.height(BadgerSpacing.xs))
            TextField(
                value = viewModel.forgotEmail.value,
                onValueChange = viewModel.onForgotEmail,
                label = "注册时使用的邮箱",
                useLabelAsPlaceholder = true,
                enabled = enabled && !viewModel.sendingForgotCode.value,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = viewModel.forgotCode.value,
                    onValueChange = viewModel.onForgotCode,
                    label = "6 位数字验证码",
                    useLabelAsPlaceholder = true,
                    enabled = enabled && !viewModel.sendingForgotCode.value,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(BadgerSpacing.sm))
                Button(
                    onClick = {
                        if (enabled && !viewModel.sendingForgotCode.value) {
                            viewModel.sendForgotCode()
                        }
                    },
                    enabled = enabled && !viewModel.sendingForgotCode.value,
                    minHeight = 48.dp,
                ) {
                    if (viewModel.sendingForgotCode.value) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(size = 14.dp, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(BadgerSpacing.xs))
                            Text(text = "发送中…")
                        }
                    } else {
                        Text(text = "发送验证码")
                    }
                }
            }
            viewModel.forgotCodeHint.value?.let { hintText ->
                Spacer(modifier = Modifier.height(BadgerSpacing.xs))
                Text(
                    text = hintText,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }

            Spacer(modifier = Modifier.height(BadgerSpacing.md))
            FieldLabel("新密码")
            Spacer(modifier = Modifier.height(BadgerSpacing.xs))
            TextField(
                value = viewModel.forgotNewPassword.value,
                onValueChange = viewModel.onForgotNewPassword,
                label = "至少 8 位",
                useLabelAsPlaceholder = true,
                enabled = enabled && !viewModel.sendingForgotCode.value,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next,
                ),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(BadgerSpacing.md))
            FieldLabel("确认新密码")
            Spacer(modifier = Modifier.height(BadgerSpacing.xs))
            TextField(
                value = viewModel.forgotNewPasswordAgain.value,
                onValueChange = viewModel.onForgotNewPasswordAgain,
                label = "再输入一次",
                useLabelAsPlaceholder = true,
                enabled = enabled && !viewModel.sendingForgotCode.value,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (enabled && viewModel.canSubmitForgotPassword()) viewModel.resetPassword()
                    },
                ),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            (state as? AuthUiState.Error)?.let { err ->
                Spacer(modifier = Modifier.height(BadgerSpacing.sm))
                FieldError(err.message)
            }

            Spacer(modifier = Modifier.height(BadgerSpacing.lg))

            Button(
                onClick = {
                    if (enabled && viewModel.canSubmitForgotPassword()) viewModel.resetPassword()
                },
                enabled = !isLoading && viewModel.canSubmitForgotPassword(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                PrimaryButtonContent(isLoading = isLoading, label = "重置密码")
            }

            Spacer(modifier = Modifier.height(BadgerSpacing.sm))
            TextButton(
                text = "返回登录",
                enabled = enabled,
                onClick = onBackToLogin,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
