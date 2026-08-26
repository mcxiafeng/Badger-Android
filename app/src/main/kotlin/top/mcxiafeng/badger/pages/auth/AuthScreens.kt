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
    onNavigateToServerSettings: (() -> Unit)? = null,
    viewModel: AuthViewModel = koinViewModel<AuthViewModel>(key = keySuffix),
) {
    // [修复防御]: 用 rememberSaveable 让模式（登录/注册）跟随 LaunchedEffect(initialIsLoginMode)
    // 初始化，避免屏幕重建后回到默认登录模式。
    var isLoginMode by rememberSaveable(viewModel) {
        mutableStateOf(initialIsLoginMode)
    }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        Log.d(TAG, "AuthScreen entered, isLoginMode=$isLoginMode, key=$keySuffix")
        viewModel.reset()
    }

    // 模式切换由本地 state 切换，不走 navigator —— 不污染路由栈。
    // [修复防御]: 这里在切换模式时清空错误,不重置输入 —— 切回原模式时输入内容还在,
    // 用户体验更连续。
    val onSwitchMode = {
        val nowLogin = !isLoginMode
        Log.d(TAG, "AuthScreen switch mode ${if (isLoginMode) "login->register" else "register->login"}")
        isLoginMode = nowLogin
        passwordVisible = false
        if (nowLogin) viewModel.switchToLogin() else viewModel.switchToRegister()
    }

    LaunchedEffect(state) {
        if (state is AuthUiState.SignedIn) {
            Log.d(TAG, "AuthScreen -> onAuthed, leaving route")
            onAuthed()
        }
    }

    // [V2-E2E #1]: 启动期 / 登录页检测 server URL 是否被用户主动配置。
    // isServerUrlConfigured() == false 表示当前还是默认 10.0.2.2:8080,
    // 真机/真模拟器连不通 → 顶部展示警告 + 一键跳服务器设置。
    val context = androidx.compose.ui.platform.LocalContext.current
    val needServerHint = rememberSaveable { mutableStateOf(!top.mcxiafeng.badger.data.isServerUrlConfigured(context)) }
    if (needServerHint.value) {
        // 复用 BasicComponent 风格发出警告 — Card + TextButton
        // 这里只给"登录注册都可用，但 URL 未配置"的引导条;不强制阻塞登录。
        // [修复防御]: 用 mutableStateOf 而非 State Flow,避免每次重建都重读 prefs。
        androidx.compose.runtime.SideEffect {
            Log.w(TAG, "AuthScreen: server URL not configured, showing hint banner")
        }
    }

    val isLoading = state is AuthUiState.Loading
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = {
            TopAppBar(
                title = if (isLoginMode) "登录" else "注册",
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
                .padding(horizontal = 16.dp, vertical = 16.dp),
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
            Spacer(modifier = Modifier.height(20.dp))

            // [V2-E2E #1] 启动期 server URL 未配置 → 顶部柔和淡红单行提示。
            // 需求:浅色 Miuix 主题下 errorContainer 偏粉,深色下偏暗。
            // 加 0.55 alpha 把饱和度再压一档,既保留"这是警告"的语义,又不会
            // 跳出整套 Miuix 主题基调。
            //
            // [修复防御]: 之前用 hardcode Color(0xFFB00020) + Color.White
            // 强对比,用户反馈太刺眼、破坏整体观感。这里退到 Miuix 自带
            // errorContainer token + 0.55 alpha,柔和很多。
            //
            // [修复防御]: 登录页不再提供"修改服务器地址"入口 —— 服务端地址
            // 属于配置项,普通用户不应在登录页随手改;此处只展示状态提示,
            // 引导条不可点击、不跳 ServerSettingsPage。改 URL 这件事发生在
            // App.kt 启动期或设置页(原 V2-E2E #1 路径不变,仅取消此 onClick)。
            if (needServerHint.value) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                        contentColor = MiuixTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text(
                        text = "当前配置的服务器不可用,请检查网络或稍后重试",
                        style = MiuixTheme.textStyles.body2,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 模式切换器（登录 / 注册 chip）
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ModeChip(
                        text = "登录",
                        selected = isLoginMode,
                        onClick = {
                            if (isLoginMode) return@ModeChip
                            onSwitchMode()
                        },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f),
                    )
                    ModeChip(
                        text = "注册",
                        selected = !isLoginMode,
                        onClick = {
                            if (!isLoginMode) return@ModeChip
                            onSwitchMode()
                        },
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
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            imeAction = if (isLoginMode) ImeAction.Next else ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // 邮箱（仅注册模式）
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
                            label = "邮箱（可选）",
                            useLabelAsPlaceholder = true,
                            enabled = !isLoading,
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
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (isLoginMode) viewModel.signIn() else viewModel.register()
                            },
                        ),
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
                            em.isNotEmpty() && !viewModel.isValidEmailForHint(em) -> "邮箱格式不正确"
                            else -> null
                        }
                        if (hint != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = hint,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.error,
                            )
                        }
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

                    // [修复防御]: 模式切换已经由上方的 chip 完成,这里不再放
                    // "还没有账号？立即注册" / "已有账号？返回登录" 按钮 —— 两个入口
                    // 重复且增加误触。保留主按钮独占表单底部,视觉重心更清晰。
                }
            }
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
    onNavigateToServerSettings: (() -> Unit)? = null,
    viewModel: AuthViewModel = koinViewModel<AuthViewModel>(key = "login"),
) {
    @Suppress("UNUSED_PARAMETER")
    val noOp = onNavigateToRegister
    AuthScreen(
        initialIsLoginMode = true,
        onAuthed = onAuthed,
        onBack = onBack,
        keySuffix = "login",
        onNavigateToServerSettings = onNavigateToServerSettings,
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

