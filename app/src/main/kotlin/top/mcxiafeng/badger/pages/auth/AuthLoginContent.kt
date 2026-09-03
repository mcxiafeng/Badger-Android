package top.mcxiafeng.badger.pages.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField

/**
 * 登录模式表单 —— 用户名、密码 + 忘记密码入口。
 */
@Composable
internal fun LoginContent(
    viewModel: AuthViewModel,
    enabled: Boolean,
    state: AuthUiState,
    passwordVisible: Boolean,
    onTogglePasswordVisible: () -> Unit,
    onNavigateForgotPassword: () -> Unit,
) {
    val isLoading = state is AuthUiState.Loading
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(BadgerSpacing.lg),
        cornerRadius = BadgerRadius.xl,
    ) {
        Column {
            FieldLabel("用户名")
            Spacer(modifier = Modifier.height(BadgerSpacing.xs))
            TextField(
                value = viewModel.username.value,
                onValueChange = viewModel.onUsername,
                label = "3 - 32 位字母数字下划线",
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
            FieldLabel("密码")
            Spacer(modifier = Modifier.height(BadgerSpacing.xs))
            TextField(
                value = viewModel.password.value,
                onValueChange = viewModel.onPassword,
                label = "密码",
                useLabelAsPlaceholder = true,
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (enabled && viewModel.canSubmitLogin()) viewModel.signIn()
                    },
                ),
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = onTogglePasswordVisible) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // 错误内嵌 —— 仅在出现错误时占空间
            (state as? AuthUiState.Error)?.let { err ->
                Spacer(modifier = Modifier.height(BadgerSpacing.sm))
                FieldError(err.message)
            }

            Spacer(modifier = Modifier.height(BadgerSpacing.xl))

            // 主按钮
            Button(
                onClick = {
                    if (!isLoading && viewModel.canSubmitLogin()) viewModel.signIn()
                },
                enabled = !isLoading && viewModel.canSubmitLogin(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                PrimaryButtonContent(isLoading = isLoading, label = "登录")
            }

            // 忘记密码入口 —— 登录模式专属,注册/忘记密码模式不显示(无业务关联)
            Spacer(modifier = Modifier.height(BadgerSpacing.sm))
            TextButton(
                text = "忘记密码?",
                enabled = enabled,
                onClick = onNavigateForgotPassword,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
