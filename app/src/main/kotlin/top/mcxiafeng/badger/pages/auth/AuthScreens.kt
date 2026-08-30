package top.mcxiafeng.badger.pages.auth

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
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
import top.yukonga.miuix.kmp.utils.MiuixIndication

private const val TAG = "AuthScreens"

/**
 * 账号认证页面 — 登录 / 注册 / 忘记密码三态合一的 Composable。
 *
 * 设计要点（沿袭原版契约）：
 *   - 三态由 [AuthViewModel.authMode] 三态 StateFlow 承载，UI 不直接走 navigator，
 *     避免「登录/注册互相嵌套」反复 push 栈引发的体感异常；
 *   - TopAppBar 返回按钮永远一次回到主页；
 *   - [LoginScreen]/[RegisterScreen] 作为对外签名兼容层保留，内部委托 [AuthScreen]。
 *
 * 视觉重构点（[redesign-existing-projects] skill 落地）：
 *   - **品牌 hero**：72dp 圆形品牌盘替换原灰底小图标，配 Radial Gradient 主色光晕 + 大字号 headline；
 *   - **动画化模式切换**：Miuix 风的滑块 segmented control（pill 在三个 tab 之间弹性滑动，替代原 alpha 叠加 chip）；
 *   - **分块表单卡**：登录 / 注册 / 忘记密码共用同一张表单卡，字段间距统一 BadgerSpacing，
 *     loading 状态按钮内嵌环形指示器 + 「处理中…」字样，按钮底式不变更优雅；
 *   - **错误内嵌**：原本浮在按钮上方的 error 改为紧贴相关字段下方，与字段共享同一基线；
 *   - **版式光学对齐**：section header 用 sentence case + Medium 字重（subtitle），标题用 headline1，
 *     副标用 body2 + onSurfaceVariantSummary 而非纯 onSurface。
 */
@Composable
fun AuthScreen(
    initialIsLoginMode: Boolean,
    onAuthed: () -> Unit,
    onBack: () -> Unit,
    keySuffix: String,
    viewModel: AuthViewModel = koinViewModel<AuthViewModel>(key = keySuffix),
) {
    val authMode by viewModel.authMode.collectAsState()
    val isLoginMode = authMode == AuthMode.Login
    val isRegisterMode = authMode == AuthMode.Register
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val state by viewModel.state.collectAsState()

    // [修复防御]: 首帧对齐 authMode —— 先 reset 清空残留状态，再按 initialIsLoginMode 校正模式。
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

    val onSwitchMode: (AuthMode) -> Unit = { target ->
        Log.d(TAG, "AuthScreen switch mode ${authMode}->$target")
        passwordVisible = false
        when (target) {
            AuthMode.Login -> viewModel.switchToLogin()
            AuthMode.Register -> viewModel.switchToRegister()
            AuthMode.ForgotPassword -> viewModel.switchToForgotPassword()
        }
    }

    // [V2-E2E #1 + UX-Gap#2] banner 常驻逻辑改为 ServerUrlHolder.isUrlVerified 驱动。
    val accountViewModel: AccountSettingsViewModel = koinViewModel()
    val accountState by accountViewModel.state.collectAsState()
    val serverUrlHolder: ServerUrlHolder = top.mcxiafeng.badger.di.KoinComponentBy.get()
    val isUrlVerified by serverUrlHolder.isUrlVerified.collectAsState()
    val needServerHint = !isUrlVerified

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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 1️⃣ 品牌 Hero：标题/副标随模式切换淡入淡出
            HeroHeader(mode = authMode)

            Spacer(modifier = Modifier.height(BadgerSpacing.lg))

            // 2️⃣ Miuix 风格 segmented control —— 滑动 pill 实现模式切换
            ModeSegmentedControl(
                modes = listOf(
                    AuthMode.Login to "登录",
                    AuthMode.Register to "注册",
                    AuthMode.ForgotPassword to "忘记密码",
                ),
                selected = authMode,
                enabled = !isLoading,
                onSelect = onSwitchMode,
            )

            Spacer(modifier = Modifier.height(BadgerSpacing.lg))

            // 3️⃣ banner：URL 未验证时给出可点击提示卡（直接进入 [EditServerUrlDialog]）
            if (needServerHint) {
                ServerHintBanner(
                    onClick = {
                        Log.d(TAG, "server-hint banner tapped, opening EditServerUrlDialog")
                        showEditServerUrlDialog = true
                    },
                )
                Spacer(modifier = Modifier.height(BadgerSpacing.md))
            }

            // 4️⃣ 表单主体 —— 登录/注册/忘记密码三态共用同一卡片容器
            // 使用 Crossfade 让模式切换更丝滑,非同步替换避免高度跳变
            AnimatedContent(
                targetState = authMode,
                transitionSpec = {
                    (fadeIn(tween(durationMillis = 220)) +
                        slideInVertically(animationSpec = tween(220)) { it / 12 })
                        .togetherWith(fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 12 })
                },
                label = "authMode",
            ) { mode ->
                when (mode) {
                    AuthMode.Login -> LoginContent(
                        viewModel = viewModel,
                        enabled = !isLoading,
                        state = state,
                        passwordVisible = passwordVisible,
                        onTogglePasswordVisible = { passwordVisible = !passwordVisible },
                        onNavigateForgotPassword = { onSwitchMode(AuthMode.ForgotPassword) },
                    )
                    AuthMode.Register -> RegisterContent(
                        viewModel = viewModel,
                        enabled = !isLoading,
                        state = state,
                        passwordVisible = passwordVisible,
                        onTogglePasswordVisible = { passwordVisible = !passwordVisible },
                        onBackToLogin = { onSwitchMode(AuthMode.Login) },
                    )
                    AuthMode.ForgotPassword -> ForgotPasswordContent(
                        viewModel = viewModel,
                        enabled = !isLoading,
                        state = state,
                        onBackToLogin = { onSwitchMode(AuthMode.Login) },
                    )
                }
            }

            if (showEditServerUrlDialog) {
                EditServerUrlDialog(
                    currentUrl = accountState.serverUrl,
                    onConfirm = { newUrl ->
                        Log.d(TAG, "AuthScreen: EditServerUrlDialog confirmed")
                        accountViewModel.updateServerUrl(newUrl)
                        showEditServerUrlDialog = false
                    },
                    onDismiss = {
                        Log.d(TAG, "AuthScreen: EditServerUrlDialog dismissed")
                        showEditServerUrlDialog = false
                    },
                )
            }
        }
    }
}

/**
 * 品牌 Hero 头部 —— [redesign-existing-projects] "品牌 + 渐变 + 镜头锚点"。
 * 圆形 brand 盘由 Radial Gradient 模拟光照,标题/副标在模式切换时淡入淡出。
 */
@Composable
private fun HeroHeader(mode: AuthMode) {
    val (title, subtitle) = when (mode) {
        AuthMode.Login -> "欢迎回来" to "使用账号继续"
        AuthMode.Register -> "创建账号" to "完成下面几项即可开始"
        AuthMode.ForgotPassword -> "找回密码" to "输入邮箱,设置新密码"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = BadgerSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MiuixTheme.colorScheme.primary.copy(alpha = 0.42f),
                            MiuixTheme.colorScheme.primary.copy(alpha = 0.04f),
                        ),
                        center = Offset(54f, 54f),
                        radius = 130f,
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            val heroIcon = when (mode) {
                AuthMode.Login -> Icons.Filled.Person
                AuthMode.Register -> Icons.Filled.PersonAdd
                AuthMode.ForgotPassword -> Icons.Filled.LockReset
            }
            Icon(
                imageVector = heroIcon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(modifier = Modifier.height(BadgerSpacing.md))

        AnimatedContent(
            targetState = title,
            transitionSpec = {
                (fadeIn(tween(220)) +
                    slideInVertically(animationSpec = tween(220)) { it / 8 })
                    .togetherWith(fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 8 })
            },
            label = "heroTitle",
        ) { text ->
            Text(
                text = text,
                style = MiuixTheme.textStyles.headline1,
                color = MiuixTheme.colorScheme.onBackground,
            )
        }
        Spacer(modifier = Modifier.height(BadgerSpacing.xs))

        AnimatedContent(
            targetState = subtitle,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "heroSubtitle",
        ) { text ->
            Text(
                text = text,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/**
 * Miuix 模式 segmented control —— pill 在 tab 之间滑动，替代原 alpha 叠加 chip。
 *
 * 设计依据：
 *   - 选中态：surface 底 + 主色文字 + 主色 16% 阴影，区别于 surfaceVariant 浅灰底；
 *   - Pill 使用 [animateDpAsState] tween 280ms FastOutSlowInEasing 弹性滑动；
 *   - Pill 自身无 indication —— 点击由各自 tab 内部的 clickable 承担,
 *     pill 只是视觉指示器,不消费点击事件。
 */
@Composable
private fun ModeSegmentedControl(
    modes: List<Pair<AuthMode, String>>,
    selected: AuthMode,
    enabled: Boolean,
    onSelect: (AuthMode) -> Unit,
) {
    val selectedIndex = modes.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(4.dp),
    ) {
        val tabWidth = maxWidth / modes.size
        val targetOffset = tabWidth * selectedIndex
        val animatedOffset by animateDpAsState(
            targetValue = targetOffset,
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            label = "segmentOffset",
        )
        // Pill：surface 底 + 主色阴影,带轻微 lift 制造"按下会抬起"的暗示。
        // [修复防御]: 主色 colorScheme 是 @Composable 上下文读取,不能放进 drawBehind 的
        // DrawScope 闭包 —— 必须 hoist 到 pill Box 外部(@Composable 作用域)。
        val primaryTint = MiuixTheme.colorScheme.primary
        Box(
            modifier = Modifier
                .offset(x = animatedOffset)
                .width(tabWidth)
                .height(36.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.surface)
                .drawBehind {
                    // 模拟"提起的按钮"的 4dp 阴影层。
                    // 阴影 Y 偏移 6dp,只在低位绘制,留下 6dp 的"提空"感。
                    drawRoundRect(
                        color = primaryTint.copy(alpha = 0.18f),
                        cornerRadius = CornerRadius(
                            size.minDimension / 2f,
                            size.minDimension / 2f,
                        ),
                        topLeft = Offset(0f, 6f),
                        size = Size(size.width, size.height - 6f),
                    )
                },
        )

        // Tab 行 —— clickable 但 indication=null(MiuixIndication 已在 pill 上由 elevation 取代)
        Row(modifier = Modifier.fillMaxWidth()) {
            modes.forEach { (mode, label) ->
                val isSelected = mode == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(CircleShape)
                        .clickable(
                            enabled = enabled && !isSelected,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(mode) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MiuixTheme.textStyles.subtitle,
                        color = if (isSelected) {
                            MiuixTheme.colorScheme.onSurface
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        },
                    )
                }
            }
        }
    }
}

/**
 * 服务器地址 banner —— 可点击的轻量 Card。
 * 配色:errorContainer +0.5 alpha 与 Miuix 主基调保持和谐。
 */
@Composable
private fun ServerHintBanner(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = MiuixIndication(),
                onClickLabel = "修改服务器地址",
                onClick = onClick,
            ),
        insideMargin = PaddingValues(
            horizontal = BadgerSpacing.md,
            vertical = BadgerSpacing.md,
        ),
        cornerRadius = BadgerRadius.lg,
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            contentColor = MiuixTheme.colorScheme.onErrorContainer,
        ),
    ) {
        // [修复防御]: 不要在 Card 的 modifier 上额外加 .clip(CircleShape) —— Card 已经
        // 通过 cornerRadius 自带圆角,再 clip 成 CircleShape 会让一个宽而扁的卡片被
        // 50% 圆角切成只剩中间一点椭圆可见(因为 fillMaxWidth 让宽度 >> 高度,而
        // RoundedCornerShape(50%) 在窄高矩形上等价于椭圆)。
        Text(
            text = "当前服务器地址未配置（默认 ${DEFAULT_SERVER_URL}），点此修改 →",
            style = MiuixTheme.textStyles.body2,
        )
    }
}

// =================================================================
// 表单区(登录 / 注册 / 忘记密码) —— 三态共享设计 token,字段间距统一 BadgerSpacing.md
// =================================================================

/**
 * 字段上行 label(Text) —— 与 miuix TextField label placeholder 区分,用于语义标题("用户名" / "邮箱")。
 * 改为句首小写、字号一致,与 body2 区分靠字重。
 */
@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

/**
 * 字段级错误提示 —— 紧贴相关 TextField 下方内嵌,替换原模式内浮在按钮上方的错误信息。
 * [修复防御]: 与按钮点击解耦,避免「点登录 → 错误出现在按钮下 → 与按钮状态割裂」体验。
 */
@Composable
private fun FieldError(hint: String) {
    Text(
        text = hint,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.error,
    )
}

/**
 * 登录模式表单 —— 用户名、密码 + 忘记密码入口。
 */
@Composable
private fun LoginContent(
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

/**
 * 注册模式表单 —— 用户名 / 邮箱 / 密码 / 二次密码 + 验证码（按 registerPolicy）。
 */
@Composable
private fun RegisterContent(
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

/**
 * 忘记密码表单 —— 邮箱、邮箱验证码、新密码两次。
 */
@Composable
private fun ForgotPasswordContent(
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

/**
 * 主按钮内容 —— loading 时内嵌 [CircularProgressIndicator] + 提示文字,非 loading 只显示文字。
 * 集中此渲染以避免在 LoginContent/RegisterContent/ForgotPasswordContent 重复同一段 Row+Spacer+Text。
 */
@Composable
private fun PrimaryButtonContent(isLoading: Boolean, label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                size = 16.dp,
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(BadgerSpacing.sm))
        }
        Text(text = if (isLoading) "处理中…" else label)
    }
}

// =================================================================
// 对外签名兼容层
// =================================================================

/**
 * 旧 LoginScreen 包装 —— 完全委托 [AuthScreen],固定初始模式为登录。
 *
 * onNavigateToRegister 参数已被忽略:模式切换由 [AuthScreen] 内部的 segmented control 完成,
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
 * 旧 RegisterScreen 包装 —— 完全委托 [AuthScreen],固定初始模式为注册。
 *
 * onNavigateToLogin 参数已被忽略:模式切换由 [AuthScreen] 内部的 segmented control 完成。
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
