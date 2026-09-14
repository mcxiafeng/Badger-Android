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

private const val SETUP_AUTH_VM_KEY = "setup_auth"

private const val FORM_LOGIN = "login"
private const val FORM_REGISTER = "register"
private const val FORM_FORGOT = "forgot"

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

    
    LaunchedEffect(state) {
        if (isSignedIn) {
            BadgerLog.d(ACCOUNT_TAG, "authed → advance + bootstrapPostLogin")
            
            setupGuideViewModel.bootstrapPostLogin()
            onNext()
        }
    }

    
    LaunchedEffect(state) {
        if (state is AuthUiState.ResetDone) {
            BadgerLog.d(ACCOUNT_TAG, "reset done → back to login form")
            forgotMode = false
            viewModel.switchToLogin()
        }
    }

    
    LaunchedEffect(isSignedIn) {
        setupGuideViewModel.setPageValid(PAGE_INDEX, isSignedIn)
    }

    val serverUrlHolder: ServerUrlHolder = KoinComponentBy.get()
    val serverUrl by serverUrlHolder.url.collectAsState()

    SetupStepScaffold(
        onBack = onBack,
        onNext = {
            
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
