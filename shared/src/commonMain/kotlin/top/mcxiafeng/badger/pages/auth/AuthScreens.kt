package top.mcxiafeng.badger.pages.auth

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import top.mcxiafeng.badger.data.repository.ServerUrlHolder
import top.mcxiafeng.badger.di.KoinComponentBy
import top.mcxiafeng.badger.ui.components.EditServerUrlDialog
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.UserPlus
import com.composables.icons.lucide.UserRound
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "AuthScreens"

/** 认证域共享 VM key：主页与忘记密码二级页必须拿到同一实例（凭据/表单状态互通）。 */
private const val AUTH_VM_KEY = "auth"

/** 认证主页 pager 页数（0 = 登录，1 = 注册）。 */
private const val AUTH_PAGE_COUNT = 2
private const val PAGE_LOGIN = 0
private const val PAGE_REGISTER = 1

/**
 * 认证主页（L1）—— 登录 / 注册双模式，segment 点击与左右滑两种方式切换。
 *
 * 层级规划：
 * - L1（本页）：品牌 Hero + 登录/注册切换器 + 表单卡（HorizontalPager 承载滑动）+ 服务器状态条；
 * - L2（[ForgotPasswordScreen]）：从登录卡「忘记密码？」进入的独立二级页；
 * - 注册扩展字段（验证码 / 邮箱码）是注册表单的内联内容，不构成独立页面。
 *
 * 切换同步（单一路径防回环）：
 * - segment 点击 → `animateScrollToPage`，VM 模式由 `settledPage` 收敛后统一翻转；
 * - 左右滑 → `settledPage` 变化 → `switchToLogin/Register`（幂等：清错 + 按需拉策略）；
 * - Loading / 已登录时锁滑动（`userScrollEnabled`），防止滑动竞态覆盖 SignedIn。
 *
 * 服务器状态条：进页面自动探测连通性——成功转蓝色「已连接」展示地址（仍可点改），失败保持警示。
 */
@Composable
fun AuthScreen(
    onAuthed: () -> Unit,
    onBack: () -> Unit,
    onNavigateForgotPassword: () -> Unit,
    viewModel: AuthViewModel = koinViewModel(key = AUTH_VM_KEY),
) {
    val authMode by viewModel.authMode.collectAsState()
    val state by viewModel.state.collectAsState()
    val isLoading = state is AuthUiState.Loading
    val isBusy = state is AuthUiState.Loading || state is AuthUiState.SignedIn

    val serverUrlHolder: ServerUrlHolder = KoinComponentBy.get()
    val serverUrl by serverUrlHolder.url.collectAsState()
    val isUrlVerified by serverUrlHolder.isUrlVerified.collectAsState()
    val probing by viewModel.probing.collectAsState()
    var showServerDialog by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(initialPage = PAGE_LOGIN) { AUTH_PAGE_COUNT }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        BadgerLog.d(TAG, "AuthScreen enter, aligning to Login mode + probing server")
        viewModel.onAuthScreenEnter()
        viewModel.probeServerConnection()
    }

    LaunchedEffect(state) {
        if (state is AuthUiState.SignedIn) {
            BadgerLog.d(TAG, "AuthScreen -> onAuthed, leaving route")
            onAuthed()
        }
    }

    // 左右滑收敛 → 翻转 VM 模式（switchTo* 幂等：清残留错误 + 按需拉注册策略）
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            BadgerLog.d(TAG, "AuthScreen settled page=$page")
            when (page) {
                PAGE_REGISTER -> viewModel.switchToRegister()
                else -> viewModel.switchToLogin()
            }
        }
    }

    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    Scaffold(
        topBar = {
            TopAppBar(
                title = if (authMode == AuthMode.Register) "注册" else "登录",
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Lucide.ArrowLeft, contentDescription = "返回")
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
            val (title, subtitle, heroIcon) = if (authMode == AuthMode.Register) {
                Triple("创建账号", "注册后可在多台设备间同步名片", Lucide.UserPlus)
            } else {
                Triple("欢迎回来", "登录 Badger 账号以同步云端数据", Lucide.UserRound)
            }
            AuthHero(title = title, subtitle = subtitle, icon = heroIcon)

            Spacer(modifier = Modifier.height(BadgerSpacing.lg))
            AuthModeSwitch(
                tabs = listOf("登录", "注册"),
                selectedIndex = if (authMode == AuthMode.Register) 1 else 0,
                enabled = !isLoading,
                onSelect = { index ->
                    BadgerLog.d(TAG, "AuthScreen segment tap -> page=$index")
                    scope.launch { pagerState.animateScrollToPage(index) }
                },
            )

            // 服务器状态条常驻：探测中 / 已连接（蓝，可点改）/ 未验证（警示）
            Spacer(modifier = Modifier.height(BadgerSpacing.md))
            ServerStatusBanner(
                url = serverUrl,
                probing = probing,
                verified = isUrlVerified,
                onClick = { showServerDialog = true },
            )

            Spacer(modifier = Modifier.height(BadgerSpacing.lg))
            HorizontalPager(
                state = pagerState,
                // 登录/注册页高度差大：滑动中两页同时参与测量，用无过冲弹簧平滑高度跳变
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
                    ),
                pageSpacing = BadgerSpacing.lg,
                userScrollEnabled = !isBusy,
            ) { page ->
                when (page) {
                    PAGE_REGISTER -> AuthRegisterCard(
                        viewModel = viewModel,
                        enabled = !isLoading,
                        onSubmit = viewModel::register,
                    )
                    else -> AuthLoginCard(
                        viewModel = viewModel,
                        enabled = !isLoading,
                        onForgotPassword = onNavigateForgotPassword,
                        onSubmit = viewModel::signIn,
                    )
                }
            }
        }
    }

    if (showServerDialog) {
        EditServerUrlDialog(
            currentUrl = serverUrlHolder.url.value,
            onConfirm = { newUrl ->
                BadgerLog.d(TAG, "AuthScreen: server url confirmed, hot-applying + re-probing")
                viewModel.applyServerUrl(newUrl)
                viewModel.probeServerConnection()
                showServerDialog = false
            },
            onDismiss = { showServerDialog = false },
        )
    }
}

/**
 * 忘记密码（L2 二级页）—— 从认证主页登录卡的「忘记密码？」进入。
 *
 * 重置成功（[AuthUiState.ResetDone]）自动返回认证主页；凭据保留在共享 VM 中，
 * 返回后用户无需重输用户名。
 */
@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    viewModel: AuthViewModel = koinViewModel(key = AUTH_VM_KEY),
) {
    val state by viewModel.state.collectAsState()

    val serverUrlHolder: ServerUrlHolder = KoinComponentBy.get()
    val serverUrl by serverUrlHolder.url.collectAsState()
    val isUrlVerified by serverUrlHolder.isUrlVerified.collectAsState()
    val probing by viewModel.probing.collectAsState()
    var showServerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        BadgerLog.d(TAG, "ForgotPasswordScreen enter")
        viewModel.onForgotScreenEnter()
        viewModel.probeServerConnection()
    }

    LaunchedEffect(state) {
        if (state is AuthUiState.ResetDone) {
            BadgerLog.d(TAG, "ForgotPasswordScreen: reset done, popping back to Auth")
            onBack()
        }
    }

    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    Scaffold(
        topBar = {
            TopAppBar(
                title = "忘记密码",
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Lucide.ArrowLeft, contentDescription = "返回")
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
            AuthHero(
                title = "找回密码",
                subtitle = "输入注册邮箱，验证后设置新密码",
                icon = Lucide.KeyRound,
            )

            Spacer(modifier = Modifier.height(BadgerSpacing.md))
            ServerStatusBanner(
                url = serverUrl,
                probing = probing,
                verified = isUrlVerified,
                onClick = { showServerDialog = true },
            )

            Spacer(modifier = Modifier.height(BadgerSpacing.lg))
            AuthForgotCard(
                viewModel = viewModel,
                enabled = state !is AuthUiState.Loading,
                onSubmit = viewModel::resetPassword,
            )
        }
    }

    if (showServerDialog) {
        EditServerUrlDialog(
            currentUrl = serverUrlHolder.url.value,
            onConfirm = { newUrl ->
                BadgerLog.d(TAG, "ForgotPasswordScreen: server url confirmed, hot-applying + re-probing")
                viewModel.applyServerUrl(newUrl)
                viewModel.probeServerConnection()
                showServerDialog = false
            },
            onDismiss = { showServerDialog = false },
        )
    }
}
