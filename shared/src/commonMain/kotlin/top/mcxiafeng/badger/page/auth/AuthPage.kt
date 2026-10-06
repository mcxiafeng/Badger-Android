package top.mcxiafeng.badger.page.auth

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.UserPlus
import com.composables.icons.lucide.UserRound
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.ui.Navigator
import top.mcxiafeng.badger.ui.Route
import top.mcxiafeng.badger.ui.designsystem.BadgerDesignTheme
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.utils.BadgerLog
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAG = "AuthPageTester"

private const val AUTH_PAGE_COUNT = 2
private const val PAGE_LOGIN = 0
private const val PAGE_REGISTER = 1

class AuthPage {

    companion object {

        @Composable
        fun PageAuth(
            navigator: Navigator,
            viewModel: AuthViewModel = viewModel { AuthViewModel() },
        ) {
            val submitState by viewModel.submitState.collectAsStateWithLifecycle()
            val isBusy = submitState is AuthSubmitState.Loading

            val pagerState = rememberPagerState(initialPage = PAGE_LOGIN) { AUTH_PAGE_COUNT }
            val scope = rememberCoroutineScope()
            val isRegister = pagerState.currentPage == PAGE_REGISTER

            LaunchedEffect(Unit) { viewModel.onScreenEnter() }

            // 注册页首次落定再拉策略，登录页零网络
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.settledPage }.collect { page ->
                    BadgerLog.d(TAG, "切换到第 $page 页")
                    if (page == PAGE_REGISTER) viewModel.loadRegisterPolicy()
                }
            }

            LaunchedEffect(submitState) {
                when (val state = submitState) {
                    is AuthSubmitState.Success -> when (state.kind) {
                        SuccessKind.LOGIN -> {
                            BadgerLog.d(TAG, "登录成功，返回主页")
                            navigator.pop()
                        }
                        SuccessKind.REGISTER -> {
                            BadgerLog.d(TAG, "注册成功，切回登录页")
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
                    TopAppBar(
                        title = if (isRegister) "注册" else "登录",
                        navigationIcon = {
                            IconButton(onClick = { navigator.pop() }) {
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
                    val (title, subtitle, heroIcon) = if (isRegister) {
                        Triple("创建账号", "注册后可在多台设备间同步名片", Lucide.UserPlus)
                    } else {
                        Triple("欢迎回来", "登录 Badger 账号以同步云端数据", Lucide.UserRound)
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

                    val successHint = (submitState as? AuthSubmitState.Success)?.message
                    if (successHint != null) {
                        Spacer(modifier = Modifier.height(BadgerSpacing.md))
                        Text(
                            text = successHint,
                            style = MiuixTheme.textStyles.body2,
                            color = BadgerDesignTheme.colors.success,
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
                                onForgotPassword = { navigator.push(Route.ForgotPassword) },
                                onSubmit = viewModel::signIn,
                            )
                        }
                    }
                }
            }
        }

        @Composable
        fun PageForgotPassword(
            navigator: Navigator,
            viewModel: AuthViewModel = viewModel { AuthViewModel() },
        ) {
            val submitState by viewModel.submitState.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) { viewModel.onScreenEnter() }

            LaunchedEffect(submitState) {
                if (submitState is AuthSubmitState.Success) {
                    BadgerLog.d(TAG, "密码重置成功，返回登录页")
                    navigator.pop()
                }
            }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = "忘记密码",
                        navigationIcon = {
                            IconButton(onClick = { navigator.pop() }) {
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
                    Spacer(modifier = Modifier.height(BadgerSpacing.lg))
                    AuthForgotCard(
                        viewModel = viewModel,
                        enabled = submitState !is AuthSubmitState.Loading,
                        onSubmit = viewModel::resetPassword,
                    )
                }
            }
        }
    }
}
