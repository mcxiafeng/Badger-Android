package top.mcxiafeng.badger.pages.setupguide

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.koin.compose.viewmodel.koinViewModel
import top.mcxiafeng.badger.data.repository.ServerUrlHolder
import top.mcxiafeng.badger.di.KoinComponentBy
import top.mcxiafeng.badger.pages.auth.AuthForgotCard
import top.mcxiafeng.badger.pages.auth.AuthLoginCard
import top.mcxiafeng.badger.pages.auth.AuthMode
import top.mcxiafeng.badger.pages.auth.AuthModeSwitch
import top.mcxiafeng.badger.pages.auth.AuthRegisterCard
import top.mcxiafeng.badger.pages.auth.AuthUiState
import top.mcxiafeng.badger.pages.auth.AuthViewModel
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.User
import top.mcxiafeng.badger.utils.BadgerLog

private const val ACCOUNT_TAG = "SetupStepAccount"
private const val PAGE_INDEX = 1

/** 引导页内嵌认证表单的 key（与设置页进入的认证主页不共享 VM）。 */
private const val SETUP_AUTH_VM_KEY = "setup_auth"

/** 页内表单态（登录 / 注册 / 忘记密码）。引导 pager 内不能 push 路由，忘记密码在此页内切换。 */
private const val FORM_LOGIN = "login"
private const val FORM_REGISTER = "register"
private const val FORM_FORGOT = "forgot"

/**
 * 引导 Step 1 — 账号（登录 / 注册 / 忘记密码）。
 *
 * 设计契约：
 * - 不可跳过。必须完成登录（AuthUiState.SignedIn）才能进入下一步。
 * - 顶部展示 Step 0 已配置的服务器地址（修改入口 = 底部「上一步」）。
 * - 表单复用认证域共享卡（[AuthLoginCard] / [AuthRegisterCard] / [AuthForgotCard]），
 *   用独立 key [SETUP_AUTH_VM_KEY] 持有 VM，避免与设置页进入的认证主页共享输入。
 * - 忘记密码是页内第三态（无导航栈可 push）；重置成功（ResetDone）切回登录。
 * - 登录成功后 fire-and-forget 调 [SetupGuideViewModel.bootstrapPostLogin] 拉服务端数据。
 */
@Composable
internal fun SetupStepAccount(
    onBack: () -> Unit,
    onNext: () -> Unit,
    viewModel: AuthViewModel = koinViewModel(key = SETUP_AUTH_VM_KEY),
    setupGuideViewModel: SetupGuideViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val authMode by viewModel.authMode.collectAsState()
    val isLoading = state is AuthUiState.Loading
    val isSignedIn = state is AuthUiState.SignedIn

    var forgotMode by rememberSaveable { mutableStateOf(false) }
    val formKey = when {
        forgotMode -> FORM_FORGOT
        authMode == AuthMode.Register -> FORM_REGISTER
        else -> FORM_LOGIN
    }

    // 登录成功自动翻页（避免用户再点 Next）
    LaunchedEffect(state) {
        if (isSignedIn) {
            BadgerLog.d(ACCOUNT_TAG, "authed → advance + bootstrapPostLogin")
            // 触发登录后数据预热（拉 selfPerson + 增量同步），onNext 不等待同步完成。
            setupGuideViewModel.bootstrapPostLogin()
            onNext()
        }
    }

    // 忘记密码重置成功 → 切回登录表单
    LaunchedEffect(state) {
        if (state is AuthUiState.ResetDone) {
            BadgerLog.d(ACCOUNT_TAG, "reset done → back to login form")
            forgotMode = false
            viewModel.switchToLogin()
        }
    }

    // 上报当前页可推进性：已登录才让 Pager 解锁
    LaunchedEffect(isSignedIn) {
        setupGuideViewModel.setPageValid(PAGE_INDEX, isSignedIn)
    }

    val serverUrlHolder: ServerUrlHolder = KoinComponentBy.get()
    val serverUrl by serverUrlHolder.url.collectAsState()

    SetupStepScaffold(
        onBack = onBack,
        onNext = {
            // 已登录才能继续 —— 防御键盘 enter / TalkBack 等绕过 UI 的事件。
            if (isSignedIn) {
                BadgerLog.d(ACCOUNT_TAG, "next")
                onNext()
            } else {
                BadgerLog.w(ACCOUNT_TAG, "next blocked: not signed in")
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
                icon = Lucide.User,
            )

            Spacer(modifier = Modifier.height(BadgerSpacing.lg))

            // 服务器地址仅展示 —— 修改入口由底部「上一步」承担（Step 0）
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

            AuthModeSwitch(
                tabs = listOf("登录", "注册", "忘记密码"),
                selectedIndex = when (formKey) {
                    FORM_REGISTER -> 1
                    FORM_FORGOT -> 2
                    else -> 0
                },
                enabled = !isLoading,
                onSelect = { index ->
                    BadgerLog.d(ACCOUNT_TAG, "switch → index=$index")
                    forgotMode = index == 2
                    when (index) {
                        0 -> viewModel.switchToLogin()
                        1 -> viewModel.switchToRegister()
                    }
                },
            )

            Spacer(modifier = Modifier.height(BadgerSpacing.lg))

            AnimatedContent(
                targetState = formKey,
                transitionSpec = {
                    (fadeIn(tween(BadgerMotion.DURATION_BASE)) +
                        slideInVertically(tween(BadgerMotion.DURATION_BASE)) { it / 12 })
                        .togetherWith(
                            fadeOut(tween(BadgerMotion.DURATION_FAST)) +
                                slideOutVertically(tween(BadgerMotion.DURATION_FAST)) { -it / 12 }
                        )
                },
                label = "setupAuthForm",
            ) { key ->
                when (key) {
                    FORM_FORGOT -> AuthForgotCard(
                        viewModel = viewModel,
                        enabled = !isLoading,
                        onSubmit = viewModel::resetPassword,
                    )
                    FORM_REGISTER -> AuthRegisterCard(
                        viewModel = viewModel,
                        enabled = !isLoading,
                        onSubmit = viewModel::register,
                    )
                    else -> AuthLoginCard(
                        viewModel = viewModel,
                        enabled = !isLoading,
                        onForgotPassword = { forgotMode = true },
                        onSubmit = viewModel::signIn,
                    )
                }
            }

            Spacer(modifier = Modifier.height(BadgerSpacing.md))

            // 已登录态视觉提示 — LaunchedEffect 立刻 onNext 通常不可见，作可观测性兜底。
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
