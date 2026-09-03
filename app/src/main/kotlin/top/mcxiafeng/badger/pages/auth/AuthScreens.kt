package top.mcxiafeng.badger.pages.auth

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import org.koin.androidx.compose.koinViewModel
import top.mcxiafeng.badger.data.repository.ServerUrlHolder
import top.mcxiafeng.badger.pages.settings.account.AccountSettingsViewModel
import top.mcxiafeng.badger.pages.settings.account.EditServerUrlDialog
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState

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
