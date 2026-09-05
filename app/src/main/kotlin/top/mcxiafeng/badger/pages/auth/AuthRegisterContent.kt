package top.mcxiafeng.badger.pages.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
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
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff

/**
 * 注册模式表单 —— 用户名 / 邮箱 / 密码 / 二次密码 + 验证码（按 registerPolicy）。
 */
@Composable
internal fun RegisterContent(
    viewModel: AuthViewModel,
    enabled: Boolean,
    state: AuthUiState,
    passwordVisible: Boolean,
    onTogglePasswordVisible: () -> Unit,
    onBackToLogin: () -> Unit,
) {
    val isLoading = state is AuthUiState.Loading
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(BadgerSpacing.lg),
        cornerRadius = BadgerRadius.xl,
    ) {
        Column {
            // ---- 账号区 ----
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
            FieldLabel("邮箱")
            Spacer(modifier = Modifier.height(BadgerSpacing.xs))
            TextField(
                value = viewModel.email.value,
                onValueChange = viewModel.onEmail,
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

            // ---- 凭据区 ----
            Spacer(modifier = Modifier.height(BadgerSpacing.md))
            FieldLabel("密码")
            Spacer(modifier = Modifier.height(BadgerSpacing.xs))
            TextField(
                value = viewModel.password.value,
                onValueChange = viewModel.onPassword,
                label = "至少 8 位",
                useLabelAsPlaceholder = true,
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next,
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
                                Lucide.EyeOff
                            } else {
                                Lucide.Eye
                            },
                            contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // ---- 注册扩展字段 ----
            Spacer(modifier = Modifier.height(BadgerSpacing.sm))
            RegisterExtraFields(viewModel = viewModel, enabled = enabled)

            // ---- 字段级 hint(实时校验反馈):卡在字段下方,与字段共享基线 ----
            // [修复防御]: 注册按钮被 canSubmitRegister 禁用时,用户根本不知道为什么不能点。
            // 这里把阻断条件翻译成可见文本,让"按钮是禁用状态"变成有引导的可操作状态。
            val u = viewModel.username.value
            val pw = viewModel.password.value
            val em = viewModel.email.value
            val hint = when {
                u.isEmpty() -> null
                u.length < 3 || u.length > 32 -> "用户名长度需 3-32 字符"
                pw.isEmpty() -> null
                pw.length < 8 -> "密码至少 8 位"
                !viewModel.isValidEmailForHint(em) -> "请填写有效邮箱"
                viewModel.passwordAgain.value != pw -> "两次密码不一致"
                else -> null
            }
            if (hint != null) {
                Spacer(modifier = Modifier.height(BadgerSpacing.xs))
                FieldError(hint)
            }

            // ---- 提交级错误(网络 / 服务端) ----
            (state as? AuthUiState.Error)?.let { err ->
                Spacer(modifier = Modifier.height(BadgerSpacing.sm))
                FieldError(err.message)
            }

            Spacer(modifier = Modifier.height(BadgerSpacing.lg))

            Button(
                onClick = {
                    if (!isLoading && viewModel.canSubmitRegister()) viewModel.register()
                },
                enabled = !isLoading && viewModel.canSubmitRegister(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                PrimaryButtonContent(isLoading = isLoading, label = "注册")
            }

            // 返回登录 —— 注册模式专属
            Spacer(modifier = Modifier.height(BadgerSpacing.sm))
            TextButton(
                text = "已有账号?返回登录",
                enabled = enabled,
                onClick = onBackToLogin,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
