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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import top.mcxiafeng.badger.pages.auth.AuthMode
import top.mcxiafeng.badger.pages.auth.AuthUiState
import top.mcxiafeng.badger.pages.auth.AuthViewModel
import top.mcxiafeng.badger.pages.auth.RegisterExtraFields
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val ACCOUNT_TAG = "SetupStepAccount"
private const val PAGE_INDEX = 1

/**
 * 引导 Step 1 — 账号（登录 / 注册 / 忘记密码）。
 *
 * 设计契约：
 * - 不可跳过。必须完成登录（AuthUiState.SignedIn）才能进入下一步。
 * - 顶部展示 Step 0 已配置的服务器地址 + 「修改」入口，让用户能立即回到 Step 0 调整。
 * - 模式切换器：[A2] 三态（Login / Register / ForgotPassword）共享 AuthViewModel.authMode。
 *   忘记密码切到 [ForgotPasswordForm]，复用 VM 已有的 forgotEmail/forgotCode/...
 *   字段与 sendForgotCode/resetPassword 行为。
 * - 登录成功后 fire-and-forget 调 [SetupGuideViewModel.bootstrapPostLogin] 拉服务端数据。
 * - 不复用 LoginScreen/RegisterScreen：引导场景用独立 key `setup_auth` 持有 VM，
 *   避免与从设置页进入的登录页共享输入。
 */
@Composable
internal fun SetupStepAccount(
    onBack: () -> Unit,
    onNext: () -> Unit,
    viewModel: AuthViewModel = koinViewModel(key = "setup_auth"),
    setupGuideViewModel: SetupGuideViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val authMode by viewModel.authMode.collectAsState()
    val isLoading = state is AuthUiState.Loading
    val isSignedIn = state is AuthUiState.SignedIn

    var passwordVisible by remember { mutableStateOf(false) }

    // [修复防御]: 注册策略预加载 —— 切到注册时按需拉验证码配置。
    LaunchedEffect(authMode) {
        if (authMode == AuthMode.Register) {
            Log.d(ACCOUNT_TAG, "register mode → ensureRegisterPolicy")
            viewModel.ensureRegisterPolicy()
        }
    }

    // 登录成功自动翻页（避免用户再点 Next）
    LaunchedEffect(state) {
        if (state is AuthUiState.SignedIn) {
            Log.d(ACCOUNT_TAG, "authed → advance")
            // [修复防御]: 触发登录后数据预热 —— 拉 selfPerson 资料 + 增量同步 Person/Collection/Tag。
            // runSync 内部用 _isSyncing 锁住 Step 2/3 直推路径,避免「本地空表 + 用户输入」竞态。
            // onNext 不等待同步完成(用户可继续看 Step 2/3 的 UI 进度,saveUserProfile / 直推
            // 在 runSync 退出前会被同 VM 的 isSyncing 守卫)。
            setupGuideViewModel.bootstrapPostLogin()
            onNext()
        }
    }

    // [修复防御]: 上报当前页可推进性。已登录才让 Pager 解锁。
    LaunchedEffect(isSignedIn) {
        setupGuideViewModel.setPageValid(PAGE_INDEX, isSignedIn)
    }

    // [修复防御]: ServerUrlHolder 是 process-singleton,通过 Koin 拿同一份 state。
    // 这里直接 collect 让 server URL 变化时 Step 1 的展示实时刷新。
    val serverUrlHolder: top.mcxiafeng.badger.data.repository.ServerUrlHolder =
        top.mcxiafeng.badger.di.KoinComponentBy.get()
    val serverUrl by serverUrlHolder.url.collectAsState()

    SetupStepScaffold(
        onBack = onBack,
        onNext = {
            // 已登录才能继续 —— 防御键盘 enter / TalkBack 等绕过 UI 的事件。
            if (state is AuthUiState.SignedIn) {
                Log.d(ACCOUNT_TAG, "next")
                onNext()
            } else {
                Log.w(ACCOUNT_TAG, "next blocked: not signed in")
            }
        },
        nextEnabled = isSignedIn,
        nextText = "继续",
        backText = "上一步",
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BadgerSpacing.xl, vertical = BadgerSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StepHeader(
                title = "登录或注册",
                subtitle = "登录后可启用云端备份、跨设备同步、短链分享",
                icon = Icons.Filled.Person,
            )

            Spacer(modifier = Modifier.height(BadgerSpacing.lg))

            // [修复防御]: 服务器地址仅作展示 —— 「修改」入口由底部「上一步」按钮承担,
            // 避免页面顶部一个 TextButton + 底部一对 Back/Next 的三按钮拥挤感。
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "服务器",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = serverUrl,
                    style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(BadgerSpacing.lg))

            // 模式切换器：登录 / 注册 / 忘记密码 — 三态 segmented control
            // [修复防御]: 以 (mode, label) 列表驱动,避免为每种态写一份 if/else 与 diff by AuthMode 的分支。
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(BadgerSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.sm),
                ) {
                    val modeChips = listOf(
                        AuthMode.Login to "登录",
                        AuthMode.Register to "注册",
                        AuthMode.ForgotPassword to "忘记密码",
                    )
                    modeChips.forEach { (mode, label) ->
                        ModeChip(
                            text = label,
                            selected = authMode == mode,
                            onClick = {
                                if (!isLoading && authMode != mode) {
                                    Log.d(ACCOUNT_TAG, "switch → $mode")
                                    when (mode) {
                                        AuthMode.Login -> viewModel.switchToLogin()
                                        AuthMode.Register -> viewModel.switchToRegister()
                                        AuthMode.ForgotPassword -> viewModel.switchToForgotPassword()
                                    }
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(BadgerSpacing.lg))

            // 表单卡 —— 按 authMode 三态分支。
            // 登录 / 注册用原表单;忘记密码走 [ForgotPasswordForm]。
            // [修复防御]: 分支在 Card 内 — 模式切换时分段重渲染,但 Card 容器保持
            // 位置稳定,避免外部布局抖动。
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(BadgerSpacing.lg)) {
                    when (authMode) {
                        AuthMode.ForgotPassword -> ForgotPasswordForm(
                            viewModel = viewModel,
                            passwordVisible = passwordVisible,
                            onTogglePasswordVisible = { passwordVisible = !passwordVisible },
                        )
                        else -> {
                            val isLogin = authMode == AuthMode.Login
                            FieldLabel("用户名")
                            TextField(
                                value = viewModel.username.value,
                                onValueChange = viewModel.onUsername,
                                label = if (isLogin) "用户名" else "用户名 (3-32 字符)",
                                useLabelAsPlaceholder = true,
                                enabled = !isLoading,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Ascii,
                                    capitalization = KeyboardCapitalization.None,
                                    autoCorrectEnabled = false,
                                    imeAction = ImeAction.Next,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )

                            if (!isLogin) {
                                Spacer(modifier = Modifier.height(BadgerSpacing.md))
                                FieldLabel("邮箱")
                                TextField(
                                    value = viewModel.email.value,
                                    onValueChange = viewModel.onEmail,
                                    label = "邮箱（必填）",
                                    useLabelAsPlaceholder = true,
                                    enabled = !isLoading,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Email,
                                        capitalization = KeyboardCapitalization.None,
                                        autoCorrectEnabled = false,
                                        imeAction = ImeAction.Next,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            Spacer(modifier = Modifier.height(BadgerSpacing.md))
                            FieldLabel("密码")
                            TextField(
                                value = viewModel.password.value,
                                onValueChange = viewModel.onPassword,
                                label = if (isLogin) "密码" else "密码 (>=8 字符)",
                                useLabelAsPlaceholder = true,
                                enabled = !isLoading,
                                visualTransformation = if (passwordVisible) VisualTransformation.None
                                else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    capitalization = KeyboardCapitalization.None,
                                    autoCorrectEnabled = false,
                                    imeAction = ImeAction.Next,
                                ),
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff
                                            else Icons.Filled.Visibility,
                                            contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )

                            if (!isLogin) {
                                Spacer(modifier = Modifier.height(BadgerSpacing.md))
                                RegisterExtraFields(viewModel = viewModel, enabled = !isLoading)
                            }

                            (state as? AuthUiState.Error)?.let { err ->
                                Spacer(modifier = Modifier.height(BadgerSpacing.md))
                                Text(
                                    text = err.message,
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.error,
                                )
                            }

                            Spacer(modifier = Modifier.height(BadgerSpacing.xl))

                            Button(
                                onClick = {
                                    if (isLoading) return@Button
                                    if (isLogin) viewModel.signIn() else viewModel.register()
                                },
                                enabled = if (isLogin) viewModel.canSubmitLogin()
                                else viewModel.canSubmitRegister(),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColorsPrimary(),
                            ) {
                                if (isLoading) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                    ) {
                                        CircularProgressIndicator(size = 18.dp, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.size(10.dp))
                                        Text(text = "处理中…")
                                    }
                                } else {
                                    Text(text = if (isLogin) "登录" else "注册")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(BadgerSpacing.md))

            // [修复防御]: 已登录态视觉提示 — 提交成功后短暂可见，
            // 因 LaunchedEffect 立刻 onNext 通常不可见，作可观测性兜底。
            if (isSignedIn) {
                Text(
                    text = "登录成功，正在继续…",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** 表单字段统一标签 —— 紧凑、可读、与 Miuix 风格一致。 */
@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
    Spacer(modifier = Modifier.height(BadgerSpacing.xs))
}

/** 登录/注册模式切换器里的单个 chip。 */
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
            .clip(RoundedCornerShape(20.dp))
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

/**
 * 引导页内嵌的「忘记密码」表单。
 *
 * 与 [top.mcxiafeng.badger.pages.auth.ForgotPasswordContent] 同构（邮箱 + 验证码 + 新密码两次），
 * 但使用本 step 的 FieldLabel / Spacing 视觉 token,与上下 step 保持一致。
 *
 * [修复防御]: 字段全用 viewModel 的 [AuthViewModel.forgotEmail]/[forgotCode]/[forgotNewPassword]
 * 状态,共享 [AuthViewModel.sendForgotCode] / [resetPassword]。key=`setup_auth` 保证不与设置页
 * 进入的 auth VM 状态串。
 *
 * [Compose 优化]: 与 AuthScreens 同构版对齐 IME 链路 ——
 *   - 邮箱 [KeyboardType.Email] + ImeAction.Next
 *   - 验证码 [KeyboardType.Number] + ImeAction.Next（收完码 → 跳到新密码）
 *   - 新密码 / 确认密码 [KeyboardType.Password] + autoCorrectEnabled=false
 *   - 最后一个字段 ImeAction.Done + [KeyboardActions.onDone] 触发重置(绕过 UI 控件)
 *   - 全部 [singleLine] = true —— 防止 IME 把多行内容塞进密码字段
 *
 * 注意：Miuix TextField 内部不暴露 `singleLine` 参数,需通过 `maxLines` 软约束;但 onValueChange
 * 已在 VM 层用 ASCII 可见字符 + 过滤换行制表,等价于 singleLine 语义。
 */
@Composable
private fun ForgotPasswordForm(
    viewModel: AuthViewModel,
    passwordVisible: Boolean,
    onTogglePasswordVisible: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val isLoading = state is AuthUiState.Loading

    FieldLabel("邮箱")
    TextField(
        value = viewModel.forgotEmail.value,
        onValueChange = viewModel.onForgotEmail,
        label = "注册时使用的邮箱",
        useLabelAsPlaceholder = true,
        enabled = !isLoading && !viewModel.sendingForgotCode.value,
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = viewModel.forgotCode.value,
            onValueChange = viewModel.onForgotCode,
            label = "6 位数字验证码",
            useLabelAsPlaceholder = true,
            enabled = !isLoading && !viewModel.sendingForgotCode.value,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(BadgerSpacing.sm))
        Button(
            onClick = {
                if (!isLoading && !viewModel.sendingForgotCode.value) viewModel.sendForgotCode()
            },
            enabled = !isLoading && !viewModel.sendingForgotCode.value,
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
    viewModel.forgotCodeHint.value?.let { hint ->
        Spacer(modifier = Modifier.height(BadgerSpacing.xs))
        Text(
            text = hint,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }

    Spacer(modifier = Modifier.height(BadgerSpacing.md))
    FieldLabel("新密码")
    TextField(
        value = viewModel.forgotNewPassword.value,
        onValueChange = viewModel.onForgotNewPassword,
        label = "至少 8 位",
        useLabelAsPlaceholder = true,
        enabled = !isLoading,
        visualTransformation = if (passwordVisible) VisualTransformation.None
        else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Next,
        ),
        trailingIcon = {
            IconButton(onClick = onTogglePasswordVisible) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff
                    else Icons.Filled.Visibility,
                    contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(BadgerSpacing.md))
    FieldLabel("确认新密码")
    TextField(
        value = viewModel.forgotNewPasswordAgain.value,
        onValueChange = viewModel.onForgotNewPasswordAgain,
        label = "再输入一次",
        useLabelAsPlaceholder = true,
        enabled = !isLoading,
        visualTransformation = if (passwordVisible) VisualTransformation.None
        else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                // [修复防御]: 键盘 enter 等绕过 UI 控件的事件 —— 兜底提交。
                if (!isLoading && viewModel.canSubmitForgotPassword()) viewModel.resetPassword()
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    )

    (state as? AuthUiState.Error)?.let { err ->
        Spacer(modifier = Modifier.height(BadgerSpacing.md))
        Text(
            text = err.message,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.error,
        )
    }

    Spacer(modifier = Modifier.height(BadgerSpacing.xl))

    Button(
        onClick = {
            if (!isLoading && viewModel.canSubmitForgotPassword()) viewModel.resetPassword()
        },
        enabled = !isLoading && viewModel.canSubmitForgotPassword(),
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColorsPrimary(),
    ) {
        if (isLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(size = 18.dp, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.size(10.dp))
                Text(text = "处理中…")
            }
        } else {
            Text(text = "重置密码")
        }
    }
}
