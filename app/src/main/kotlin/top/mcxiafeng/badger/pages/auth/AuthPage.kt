package top.mcxiafeng.badger.pages.auth


import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.ui.designsystem.BadgerDesignColors
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAG = "AuthPageTester"

private const val AUTH_PAGE_COUNT = 2
private const val PAGE_LOGIN = 0
private const val PAGE_REGISTER = 1

/**
 * 登录/注册页（移植自 dev 分支 page/auth/AuthPage）。
 * 登录成功经 [onBack] 返回入口页；忘记密码是三级页，由 [onNavigateToForgotPassword] 进入。
 */
@Composable
fun AuthPage(
    onBack: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onNavigateToServerAddresses: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val submitState by viewModel.submitState.collectAsStateWithLifecycle()
    val isBusy = submitState is AuthSubmitState.Loading
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val isUrlVerified by viewModel.isUrlVerified.collectAsStateWithLifecycle()
    val probing by viewModel.probing.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(initialPage = PAGE_LOGIN) { AUTH_PAGE_COUNT }
    val scope = rememberCoroutineScope()
    val isRegister = pagerState.currentPage == PAGE_REGISTER
    val leavePage = { viewModel.onScreenExit(); onBack() }
    BackHandler(enabled = isBusy, onBack = leavePage)
    DisposableEffect(viewModel) { onDispose { viewModel.onScreenExit() } }

    LaunchedEffect(Unit) {
        viewModel.onScreenEnter()
        // 进页自动探测连通性（已验证则幂等跳过）
        viewModel.probeServerConnection()
    }

    // 注册页首次落定再拉策略，登录页零网络
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            Log.d(TAG, "切换到第 $page 页")
            if (page == PAGE_REGISTER) viewModel.loadRegisterPolicy()
        }
    }

    LaunchedEffect(submitState) {
        when (val state = submitState) {
            is AuthSubmitState.Success -> when (state.kind) {
                SuccessKind.LOGIN -> {
                    Log.d(TAG, "登录成功，返回主页")
                    onBack()
                }
                SuccessKind.REGISTER -> {
                    Log.d(TAG, "注册成功，切回登录页")
                    pagerState.animateScrollToPage(PAGE_LOGIN)
                }
                // 重置密码的成功跳转由忘记密码页自己处理
                SuccessKind.PASSWORD_RESET -> Unit
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = if (isRegister) "注册" else "登录",
                navigationIcon = {
                    IconButton(onClick = { viewModel.onScreenExit(); onBack() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                .padding(start = BadgerSpacing.lg, end = BadgerSpacing.lg,
                    top = BadgerSpacing.lg + BadgerSpacing.scrollContentTopGap, bottom = BadgerSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val (title, subtitle, heroIcon) = if (isRegister) {
                Triple("创建账号", "注册后可在多台设备间同步名片", Icons.Outlined.PersonAdd)
            } else {
                Triple("欢迎回来", "登录 Badger 账号以同步云端数据", Icons.Outlined.Person)
            }
            AuthHero(title = title, subtitle = subtitle, icon = heroIcon)

            Spacer(modifier = Modifier.height(BadgerSpacing.lg))
            AuthModeSwitch(
                tabs = listOf("登录", "注册"),
                selectedIndex = pagerState.currentPage,
                enabled = !isBusy,
                onSelect = { index ->
                    scope.launch { pagerState.animateScrollToPage(index) }
                },
            )

            // 服务器状态条常驻：探测中 / 已连接（蓝）/ 未验证（警示）；点击进入连接地址管理
            Spacer(modifier = Modifier.height(BadgerSpacing.md))
            ServerStatusBanner(
                url = serverUrl,
                probing = probing,
                verified = isUrlVerified,
                onClick = {
                    viewModel.onScreenExit()
                    onNavigateToServerAddresses()
                },
            )

            val successColor = if (isSystemInDarkTheme()) {
                BadgerDesignColors.successDark
            } else {
                BadgerDesignColors.successLight
            }
            val successHint = (submitState as? AuthSubmitState.Success)?.message
            if (successHint != null) {
                Spacer(modifier = Modifier.height(BadgerSpacing.md))
                Text(
                    text = successHint,
                    style = MiuixTheme.textStyles.body2,
                    color = successColor,
                )
            }

            Spacer(modifier = Modifier.height(BadgerSpacing.lg))
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ),
                pageSpacing = BadgerSpacing.lg,
                userScrollEnabled = !isBusy,
            ) { page ->
                when (page) {
                    PAGE_REGISTER -> AuthRegisterCard(
                        viewModel = viewModel,
                        enabled = !isBusy,
                        onSubmit = viewModel::register,
                    )
                    else -> AuthLoginCard(
                        viewModel = viewModel,
                        enabled = !isBusy,
                        onForgotPassword = {
                            viewModel.onScreenExit()
                            onNavigateToForgotPassword()
                        },
                        onSubmit = viewModel::signIn,
                    )
                }
            }
        }
    }
}

/** 忘记密码页（三级路由）；与登录页共用 activity 作用域的 [AuthViewModel]。 */
@Composable
fun ForgotPasswordPage(
    onBack: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val submitState by viewModel.submitState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.onScreenEnter() }

    LaunchedEffect(submitState) {
        if (submitState is AuthSubmitState.Success) {
            Log.d(TAG, "密码重置成功，返回登录页")
            onBack()
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "忘记密码",
                navigationIcon = {
                    IconButton(onClick = { viewModel.onScreenExit(); onBack() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                .padding(start = BadgerSpacing.lg, end = BadgerSpacing.lg,
                    top = BadgerSpacing.lg + BadgerSpacing.scrollContentTopGap, bottom = BadgerSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AuthHero(
                title = "找回密码",
                subtitle = "输入注册邮箱，验证后设置新密码",
                icon = Icons.Outlined.Key,
            )
            Spacer(modifier = Modifier.height(BadgerSpacing.lg))
            AuthForgotCard(
                viewModel = viewModel,
                enabled = submitState !is AuthSubmitState.Loading,
                onSubmit = viewModel::resetPassword,
            )
        }
    }
}
