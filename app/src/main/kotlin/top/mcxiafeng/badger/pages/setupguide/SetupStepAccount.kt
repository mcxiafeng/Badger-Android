package top.mcxiafeng.badger.pages.setupguide

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import top.mcxiafeng.badger.pages.auth.AuthUiState
import top.mcxiafeng.badger.pages.auth.AuthViewModel
import top.mcxiafeng.badger.pages.auth.RegisterExtraFields
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val ACCOUNT_TAG = "SetupStepAccount"

/**
 * 引导步骤 2：账号（注册 / 登录）。
 *
 * 这是项目"本地优先 + 引导中创建账号"设计的关键节点：
 *   - 默认展示登录（老用户优先体验登录）
 *   - 模式切换器让用户在不离开引导的前提下切换到注册
 *   - 校验成功后会通过 [AuthUiState.SignedIn] 自动调用 [onNext] 翻到下一步
 *   - 提供"暂不创建"跳过按钮，与 Platforms/Profile 一致
 *   - 校验失败 / 加载中 / 已登录 三种状态都会影响按钮与输入框的 enable 态
 *
 * 设计上不复用 LoginScreen/RegisterScreen —— 引导场景使用独立的 key
 * "setup_auth" 持有 ViewModel，避免与从设置页进入的登录页共享输入。
 */
@Composable
internal fun SetupStepAccount(
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val viewModel: AuthViewModel = koinViewModel(key = "setup_auth")
    var isLoginMode by remember { mutableStateOf(true) }
    var passwordVisible by remember { mutableStateOf(false) }
    val state by viewModel.state.collectAsState()
    val isLoading = state is AuthUiState.Loading
    val isSignedIn = state is AuthUiState.SignedIn

    // 模式切换：保留用户名 + 密码，但清空错误。
    val onSwitchToLogin = {
        Log.d(ACCOUNT_TAG, "Switch to login mode")
        isLoginMode = true
        viewModel.switchToLogin()
    }
    val onSwitchToRegister = {
        Log.d(ACCOUNT_TAG, "Switch to register mode")
        isLoginMode = false
        viewModel.switchToRegister()
    }

    // 校验成功：自动翻到下一步。
    LaunchedEffect(state) {
        if (state is AuthUiState.SignedIn) {
            Log.d(ACCOUNT_TAG, "SetupStepAccount -> authed, advancing")
            onNext()
        }
    }

    // [Phase 2]: 进入注册模式时加载注册策略（决定验证码形态）。
    LaunchedEffect(isLoginMode) {
        if (!isLoginMode) {
            Log.d(ACCOUNT_TAG, "SetupStepAccount register mode, loading register policy")
            viewModel.ensureRegisterPolicy()
        }
    }

    SetupStepScaffold(
        onBack = onBack,
        onSkip = {
            Log.d(ACCOUNT_TAG, "SetupStepAccount skip")
            onSkip()
        },
        onNext = {
            // [修复防御]: 已登录才能继续；未登录时点"继续"等于跳过，避免误触进入云同步相关后续步骤。
            if (isSignedIn) {
                Log.d(ACCOUNT_TAG, "SetupStepAccount next")
                onNext()
            } else {
                Log.d(ACCOUNT_TAG, "SetupStepAccount next blocked: not signed in, treat as skip")
                onSkip()
            }
        },
        nextEnabled = isSignedIn,
        nextText = "继续",
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            // 顶部头像 —— [修复防御]: 按用户反馈,头像下不再写标题 / 副标题,
            // 只保留头像圆圈作为视觉锚点,把更多空间留给模式切换器与表单。
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(modifier = Modifier.height(20.dp))

            // 模式切换器
            Card(modifier = Modifier.fillMaxWidth(), insideMargin = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ModeChip(
                        text = "登录",
                        selected = isLoginMode,
                        onClick = onSwitchToLogin,
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f),
                    )
                    ModeChip(
                        text = "注册",
                        selected = !isLoginMode,
                        onClick = onSwitchToRegister,
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 表单 Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 用户名
                    Text(
                        text = "用户名",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TextField(
                        value = viewModel.username.value,
                        onValueChange = viewModel.onUsername,
                        label = if (isLoginMode) "用户名" else "用户名 (3-32 字符)",
                        useLabelAsPlaceholder = true,
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // 邮箱（仅注册）
                    if (!isLoginMode) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "邮箱",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        TextField(
                            value = viewModel.email.value,
                            onValueChange = viewModel.onEmail,
                            label = "邮箱（必填）",
                            useLabelAsPlaceholder = true,
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "密码",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TextField(
                        value = viewModel.password.value,
                        onValueChange = viewModel.onPassword,
                        label = if (isLoginMode) "密码" else "密码 (>=8 字符)",
                        useLabelAsPlaceholder = true,
                        enabled = !isLoading,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // [Phase 2]: 注册模式的扩展字段 —— 确认密码 + 图形/邮箱验证码（registerPolicy 驱动）
                    if (!isLoginMode) {
                        Spacer(modifier = Modifier.height(12.dp))
                        RegisterExtraFields(viewModel = viewModel, enabled = !isLoading)
                    }

                    // 错误信息
                    (state as? AuthUiState.Error)?.let { err ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = err.message,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.error,
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 主按钮
                    Button(
                        onClick = {
                            if (isLoading) return@Button
                            if (isLoginMode) viewModel.signIn() else viewModel.register()
                        },
                        enabled = if (isLoginMode) viewModel.canSubmitLogin() else viewModel.canSubmitRegister(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        if (isLoading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                CircularProgressIndicator(
                                    size = 18.dp,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.size(10.dp))
                                Text(text = "处理中…")
                            }
                        } else {
                            Text(text = if (isLoginMode) "登录" else "注册")
                        }
                    }
                }
            }

            // 已登录提示（提交成功后短暂可见，因 LaunchedEffect 立刻 onNext，通常看不到）
            if (isSignedIn) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "登录成功，正在继续…",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * 登录/注册模式切换器里的单个 chip。
 *
 * 选中态用 primary 半透明背景 + primary 文字；未选用透明背景 + 灰色文字。
 * 不使用 [top.mcxiafeng.badger.ui.preference.ArrowPreference] —— 那个有箭头，不适合 mode switcher。
 */
@Composable
private fun ModeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val containerColor = if (selected) {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val contentColor = if (selected) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.subtitle,
            color = contentColor,
        )
    }
}