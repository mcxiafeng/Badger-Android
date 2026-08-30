package top.mcxiafeng.badger.pages.auth

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import top.mcxiafeng.badger.data.repository.ServerUrlHolder
import top.mcxiafeng.badger.pages.settings.AccountSettingsViewModel
import top.mcxiafeng.badger.pages.settings.DEFAULT_SERVER_URL
import top.mcxiafeng.badger.pages.settings.EditServerUrlDialog
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.utils.MiuixIndication
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAG = "AuthScreens"

/**
 * 账号认证页面 — 登录 / 注册共用同一路由（[top.mcxiafeng.badger.ui.navigation.Route.Login]）。
 *
 * 设计要点（对应用户反馈"登录注册可以互相嵌套"的 bug）：
 *   - 把登录和注册做成**同一个 Composable**，内部用 [isLoginMode] 切换显示模式；
 *   - 用户在登录页点击"立即注册"，仅切换 isLoginMode = false，**不**调用 navigator 切路由，
 *     也就不往栈里 push 新页面；
 *   - 因此 TopAppBar 的返回按钮永远一次回到主页，不会出现"反复点切换后栈溢出"的体感异常。
 *
 * 老版本的两套屏（[LoginScreen] + [RegisterScreen]）保留为薄包装，仅作为对外签名兼容层，
 * 内部都委托到 [AuthScreen]。
 */
@Composable
fun AuthScreen(
    initialIsLoginMode: Boolean,
    onAuthed: () -> Unit,
    onBack: () -> Unit,
    keySuffix: String,
    viewModel: AuthViewModel = koinViewModel<AuthViewModel>(key = keySuffix),
) {
    // [A3]: UI 模式以 viewModel.authMode（三态 StateFlow）为唯一数据源。
    // 兼容层传入的 initialIsLoginMode（boolean）只在首帧根据 VM 尚未初始化 authMode
    // 时作为"用户本意模式"兜底，避免 LoginScreen/RegisterScreen 包装造成的闪烁。
    val authMode by viewModel.authMode.collectAsState()
    val isLoginMode = authMode == AuthMode.Login
    val isRegisterMode = authMode == AuthMode.Register
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val state by viewModel.state.collectAsState()

    // [修复防御]: 首帧对齐 authMode —— 先 reset 清空残留状态，再按 initialIsLoginMode 校正模式。
    // 合并为单一 LaunchedEffect 避免 reset() 覆盖 switchToRegister() 的竞态。
    LaunchedEffect(keySuffix) {
        viewModel.reset()
        if (!initialIsLoginMode && viewModel.authMode.value == AuthMode.Login) {
            Log.d(TAG, "AuthScreen initial register mode, aligning authMode")
            viewModel.switchToRegister()
        }
        Log.d(TAG, "AuthScreen entered, authMode=${viewModel.authMode.value}, key=$keySuffix")
    }

    LaunchedEffect(state) {
        if (state is AuthUiState.SignedIn) {
            Log.d(TAG, "AuthScreen -> onAuthed, leaving route")
            onAuthed()
        }
    }

    // 模式切换由 VM 的 authMode 承载，不走 navigator —— 不污染路由栈。
    // [修复防御]: 切换时清空错误、复位密码可见性；忘记密码→登录时保留邮箱便于衔接。
    val onSwitchMode: (AuthMode) -> Unit = { target ->
        Log.d(TAG, "AuthScreen switch mode ${authMode}->$target")
        passwordVisible = false
        when (target) {
            AuthMode.Login -> viewModel.switchToLogin()
            AuthMode.Register -> viewModel.switchToRegister()
            AuthMode.ForgotPassword -> viewModel.switchToForgotPassword()
        }
    }

    // [V2-E2E #1 + UX-Gap#2] banner 常驻逻辑改为 [ServerUrlHolder.isUrlVerified] 驱动:
//   - URL 未配 / 用户改了: 服务器地址配置"未验证" → banner 显示
//   - 用户成功登录（[AuthViewModel.signIn/register] onSuccess 调 markUrlVerified）:
//     配置"已验证" → banner 隐藏
//
// 修复"填错也消失"的盲点 —— 之前用 (serverUrl == DEFAULT_SERVER_URL) 判定
// 一旦用户保存任何非默认 URL 就 false, banner 立即隐藏, 用户再次陷入"找不到入口"。
// 现在只有真正"验证过"才隐藏, 它会一直挂着直到登录成功或用户主动改 URL。
//
// ⚠️ 不要再写 rememberSaveable 一次性快照: prefs 改 / 用户改 URL / 登录成功
// 三种状态变更都要让 banner 实时刷新,只能由 StateFlow 派生。
val accountViewModel: AccountSettingsViewModel = koinViewModel()
val accountState by accountViewModel.state.collectAsState()  // dialog currentUrl 用
val serverUrlHolder: ServerUrlHolder = top.mcxiafeng.badger.di.KoinComponentBy.get()
val isUrlVerified by serverUrlHolder.isUrlVerified.collectAsState()
val needServerHint = !isUrlVerified

    // [UX-Gap]: 用户在登录页撞墙（点登录失败 N 次）时，banner 不可点击 = 没有逃生通道。
    // 改为可点击 → 弹 [EditServerUrlDialog]（复用现有 dialog，零新代码）。
    // dialog 内部已处理 onPositive 校验（凭据嗅探、scheme 校验、path 剥离）。
    var showEditServerUrlDialog by remember { mutableStateOf(false) }

    if (needServerHint) {
        androidx.compose.runtime.SideEffect {
            Log.w(TAG, "AuthScreen: isUrlVerified=false, server URL not verified, showing hint banner")
        }
    }

    val isLoading = state is AuthUiState.Loading
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = {
            TopAppBar(
                title = when (authMode) {
                    AuthMode.Login -> "登录"
                    AuthMode.Register -> "注册"
                    AuthMode.ForgotPassword -> "忘记密码"
                },
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BadgerSpacing.lg, vertical = BadgerSpacing.lg),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 顶部图标 —— 不再有标题 / 副标题（按用户反馈）。
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(modifier = Modifier.height(BadgerSpacing.xl))

            // [V2-E2E #1 + UX-Gap] 当前服务器地址仍是 emulator 默认值 → 顶部柔和警告 Card。
            // 需求:浅色 Miuix 主题下 errorContainer 偏粉,深色下偏暗。
            // 加 0.55 alpha 把饱和度再压一档,既保留"这是警告"的语义,又不会
            // 跳出整套 Miuix 主题基调。
            //
            // [UX-Gap]: banner 现可点击 —— 用户在登录页反复失败时（典型真机场景:
            // 10.0.2.2:8080 连不通 → 看不到任何线索），点此直接弹 [EditServerUrlDialog]
            // 复用 AccountProfilePage 的 dialog,零新逻辑,onConfirm 走
            // [AccountSettingsViewModel.updateServerUrl] 标准三步链路（写 prefs → 广播 → 热更）。
            // banner 的 visible 条件派生自 accountState.serverUrl,用户保存新 URL 后 banner 自动消失。
            if (needServerHint) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            // [a11y/UX]: banner 现可点击,必须给按下反馈 —
                            // MiuixIndication 提供 miuix 主题一致的水波纹,
                            // 沿用 ContactFieldComponents/PlatformDetailDialog 同款。
                            // 之前 indication = null 是在 banner "不可点击"语义下
                            // 才合理的,现在变可点击必须配套给上。
                            indication = MiuixIndication(),
                            // [a11y]: TalkBack 朗读替代默认"按钮",给出动作意图。
                            onClickLabel = "修改服务器地址",
                            onClick = {
                                Log.d(TAG, "AuthScreen: server-hint banner clicked, opening EditServerUrlDialog")
                                showEditServerUrlDialog = true
                            },
                        ),
                    insideMargin = PaddingValues(horizontal = BadgerSpacing.md, vertical = BadgerSpacing.md),
                    colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                        contentColor = MiuixTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text(
                        text = "当前服务器地址未配置（默认 ${DEFAULT_SERVER_URL}），点此修改 →",
                        style = MiuixTheme.textStyles.body2,
                    )
                }
                Spacer(modifier = Modifier.height(BadgerSpacing.md))
            }

            // [UX-Gap] 服务器地址编辑 dialog —— 复用 AccountProfilePage 已有的
            // [EditServerUrlDialog]。onConfirm 走 [AccountSettingsViewModel.updateServerUrl]
            // 标准三步链路（写 prefs → ServerUrlHolder 广播 → ServerApiFactory 热更 baseUrl）。
            // dialog 关闭后 needServerHint 因 accountState.serverUrl 已变化自动变 false，
            // banner 实时隐藏，无需手动同步。
            if (showEditServerUrlDialog) {
                EditServerUrlDialog(
                    currentUrl = accountState.serverUrl,
                    onConfirm = { newUrl ->
                        Log.d(TAG, "AuthScreen: EditServerUrlDialog confirmed, delegating to AccountSettingsVM")
                        accountViewModel.updateServerUrl(newUrl)
                        showEditServerUrlDialog = false
                    },
                    onDismiss = {
                        Log.d(TAG, "AuthScreen: EditServerUrlDialog dismissed")
                        showEditServerUrlDialog = false
                    },
                )
            }

            // 模式切换器（登录 / 注册 chip）
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(BadgerSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.sm),
                ) {
                    ModeChip(
                        text = "登录",
                        selected = isLoginMode,
                        onClick = {
                            if (isLoginMode) return@ModeChip
                            onSwitchMode(AuthMode.Login)
                        },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f),
                    )
                    ModeChip(
                        text = "注册",
                        selected = isRegisterMode,
                        onClick = {
                            if (isRegisterMode) return@ModeChip
                            onSwitchMode(AuthMode.Register)
                        },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(BadgerSpacing.xl))

            // [A3]: 三态表单卡片 —— 忘记密码模式渲染 ForgotPasswordContent，
            // 登录/注册走原表单。
            if (authMode == AuthMode.ForgotPassword) {
                ForgotPasswordContent(
                    viewModel = viewModel,
                    enabled = !isLoading,
                    onBackToLogin = { onSwitchMode(AuthMode.Login) },
                )
            } else {
                AuthFormContent(
                    viewModel = viewModel,
                    isLoginMode = isLoginMode,
                    enabled = !isLoading,
                    passwordVisible = passwordVisible,
                    onTogglePasswordVisible = { passwordVisible = !passwordVisible },
                    state = state,
                    onBackToLogin = { onSwitchMode(AuthMode.Login) },
                    onNavigateForgotPassword = { onSwitchMode(AuthMode.ForgotPassword) },
                )
            }
        }
    }
}

/**
 * 登录 / 注册表单内容（由 [AuthScreen] 在非忘记密码模式下渲染）。
 * 与原逻辑等价：isLoginMode 决定用户名/密码/邮箱的字段形态与提交目标。
 * 提取为独立 composable 便于 [AuthScreen] 三态切换时保持 Scaffold/TopAppBar 不重建。
 */
@Composable
private fun AuthFormContent(
    viewModel: AuthViewModel,
    isLoginMode: Boolean,
    enabled: Boolean,
    passwordVisible: Boolean,
    onTogglePasswordVisible: () -> Unit,
    state: AuthUiState,
    onBackToLogin: () -> Unit,
    onNavigateForgotPassword: () -> Unit,
) {
    val isLoading = state is AuthUiState.Loading
    // 表单 Card
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(BadgerSpacing.lg)) {
            // 用户名
            Text(
                text = "用户名",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(modifier = Modifier.height(BadgerSpacing.xs))
            TextField(
                value = viewModel.username.value,
                onValueChange = viewModel.onUsername,
                label = if (isLoginMode) "用户名" else "用户名 (3-32 字符)",
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

            // 邮箱（仅注册模式）
            if (!isLoginMode) {
                Spacer(modifier = Modifier.height(BadgerSpacing.md))
                Text(
                    text = "邮箱",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(modifier = Modifier.height(BadgerSpacing.xs))
                TextField(
                    value = viewModel.email.value,
                    onValueChange = viewModel.onEmail,
                    label = "邮箱（必填）",
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
            }

            Spacer(modifier = Modifier.height(BadgerSpacing.md))
            Text(
                text = "密码",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(modifier = Modifier.height(BadgerSpacing.xs))
            TextField(
                value = viewModel.password.value,
                onValueChange = viewModel.onPassword,
                label = if (isLoginMode) "密码" else "密码 (>=8 字符)",
                useLabelAsPlaceholder = true,
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = if (isLoginMode) ImeAction.Done else ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (isLoginMode) viewModel.signIn()
                    },
                ),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onTogglePasswordVisible) {
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
                Spacer(modifier = Modifier.height(BadgerSpacing.md))
                RegisterExtraFields(viewModel = viewModel, enabled = enabled)
            }

            // [修复防御]: 注册按钮被 canSubmitRegister 禁用 (username 3-32 / password ≥ 8 / email 合法)
            // 时,默认只灰掉按钮 -> 用户根本不知道为什么不能点。这里在按钮上方实时显
            // 示当前缺什么条件,让"按钮是禁用状态"变成有引导的可操作状态。
            if (!isLoginMode) {
                val u = viewModel.username.value
                val pw = viewModel.password.value
                val em = viewModel.email.value
                val hint = when {
                    u.isEmpty() -> null
                    u.length < 3 || u.length > 32 -> "用户名长度需 3-32 字符"
                    pw.isEmpty() -> null
                    pw.length < 8 -> "密码至少 8 位"
                    // [Phase 2]: 邮箱必填 + 两次密码一致（与服务端校验对齐）
                    !viewModel.isValidEmailForHint(em) -> "请填写有效邮箱"
                    viewModel.passwordAgain.value != pw -> "两次密码不一致"
                    else -> null
                }
                if (hint != null) {
                    Spacer(modifier = Modifier.height(BadgerSpacing.sm))
                    Text(
                        text = hint,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
            }

            // 错误信息
            (state as? AuthUiState.Error)?.let { err ->
                Spacer(modifier = Modifier.height(BadgerSpacing.md))
                Text(
                    text = err.message,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(BadgerSpacing.xl))

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

            // [A3]: 登录模式底部增加"忘记密码？"入口 —— 一键切到忘记密码模式。
            // 注册模式不显示（忘记密码与注册无业务关联）。
            if (isLoginMode) {
                Spacer(modifier = Modifier.height(BadgerSpacing.sm))
                TextButton(
                    text = "忘记密码？",
                    enabled = enabled,
                    onClick = onNavigateForgotPassword,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // [修复防御]: 模式切换已经由上方的 chip 完成,这里不再放
            // "还没有账号？立即注册" / "已有账号？返回登录" 按钮 —— 两个入口
            // 重复且增加误触。保留主按钮独占表单底部,视觉重心更清晰。
        }
    }
}

/**
 * 模式切换器里的单个 chip。
 *
 * 选中态：primary 半透明底 + primary 字；
 * 未选中：surfaceVariant 半透明底 + 灰字。
 *
 * 点击时如果当前已选中就直接 no-op，避免无意义的 state 切换。
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

/**
 * 忘记密码表单内容 —— [A3] 核心新增。
 *
 * 流程：邮箱输入 → 发送验证码 → 验证码 + 新密码（两次）→ 提交。
 * 与 [RegisterExtraFields] 的邮箱验证码发送逻辑语义一致（复用
 * [top.mcxiafeng.badger.pages.auth.AuthViewModel.sendForgotCode]），
 * 但忘记密码模式不展示图形验证码与注册策略 —— 那是注册专属校验。
 */
@Composable
private fun ForgotPasswordContent(
    viewModel: AuthViewModel,
    enabled: Boolean,
    onBackToLogin: () -> Unit,
) {
    // 邮箱
    Text(
        text = "邮箱",
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
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

    // [A3]: 邮箱合法即显示"发送验证码"操作（与注册策略的 requireEmailCode 无依赖）。
    // dev 明文回显 / SMTP 提示都走 viewModel.forgotCodeHint。
    Spacer(modifier = Modifier.height(BadgerSpacing.sm))
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = viewModel.forgotCode.value,
            onValueChange = viewModel.onForgotCode,
            label = "邮箱验证码",
            useLabelAsPlaceholder = true,
            enabled = enabled && !viewModel.sendingForgotCode.value,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(BadgerSpacing.sm))
        Button(
            onClick = viewModel::sendForgotCode,
            enabled = enabled && !viewModel.sendingForgotCode.value,
            modifier = Modifier.padding(top = BadgerSpacing.sm),
        ) {
            if (viewModel.sendingForgotCode.value) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(size = 14.dp, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
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

    // 新密码 + 确认新密码
    Spacer(modifier = Modifier.height(BadgerSpacing.md))
    Text(
        text = "新密码",
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
    Spacer(modifier = Modifier.height(BadgerSpacing.xs))
    TextField(
        value = viewModel.forgotNewPassword.value,
        onValueChange = viewModel.onForgotNewPassword,
        label = "新密码 (>=8 字符)",
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
    Text(
        text = "确认新密码",
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
    Spacer(modifier = Modifier.height(BadgerSpacing.xs))
    TextField(
        value = viewModel.forgotNewPasswordAgain.value,
        onValueChange = viewModel.onForgotNewPasswordAgain,
        label = "确认新密码（需与一致）",
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
                if (enabled) viewModel.resetPassword()
            },
        ),
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )

    // 提交按钮
    Spacer(modifier = Modifier.height(BadgerSpacing.xl))
    Button(
        onClick = {
            if (enabled) viewModel.resetPassword()
        },
        enabled = viewModel.canSubmitForgotPassword(),
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColorsPrimary(),
    ) {
        if (viewModel.state.value is AuthUiState.Loading) {
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

    // 错误信息（提交失败 / 校验失败）
    (viewModel.state.value as? AuthUiState.Error)?.let { err ->
        Spacer(modifier = Modifier.height(BadgerSpacing.md))
        Text(
            text = err.message,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.error,
        )
    }

    Spacer(modifier = Modifier.height(BadgerSpacing.sm))
    TextButton(
        text = "返回登录",
        enabled = enabled,
        onClick = onBackToLogin,
        modifier = Modifier.fillMaxWidth(),
    )
}

// =================================================================
// 对外签名兼容层：保留 LoginScreen / RegisterScreen，
// 内部委托给 AuthScreen(initialIsLoginMode = true / false)。
// 已有的调用点（App.kt 等）无需改动。
// =================================================================

/**
 * 旧 LoginScreen 包装 —— 完全委托 [AuthScreen]，固定初始模式为登录。
 *
 * onNavigateToRegister 参数已被忽略：模式切换由 [AuthScreen] 内部的 chip 完成,
 * 不再走 navigator.navigate(Route.Register)。
 */
@Composable
fun LoginScreen(
    onAuthed: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onBack: () -> Unit,
    viewModel: AuthViewModel = koinViewModel<AuthViewModel>(key = "login"),
) {
    @Suppress("UNUSED_PARAMETER")
    val noOp = onNavigateToRegister
    AuthScreen(
        initialIsLoginMode = true,
        onAuthed = onAuthed,
        onBack = onBack,
        keySuffix = "login",
    )
}

/**
 * 旧 RegisterScreen 包装 —— 完全委托 [AuthScreen]，固定初始模式为注册。
 *
 * onNavigateToLogin 参数已被忽略：模式切换由 [AuthScreen] 内部的 chip 完成。
 */
@Composable
fun RegisterScreen(
    onAuthed: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onBack: () -> Unit,
    viewModel: AuthViewModel = koinViewModel<AuthViewModel>(key = "register"),
) {
    @Suppress("UNUSED_PARAMETER")
    val noOp = onNavigateToLogin
    AuthScreen(
        initialIsLoginMode = false,
        onAuthed = onAuthed,
        onBack = onBack,
        keySuffix = "register",
    )
}

