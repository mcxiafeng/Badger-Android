package top.mcxiafeng.badger.pages.settings.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 修改密码页。
 *
 * 旧密码 + 新密码 + 确认新密码 + 提交按钮。成功后弹 snackbar 并返回上一页。
 */
@Composable
internal fun ChangePasswordPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ChangePasswordViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    // 密码不用 rememberSaveable，避免明文写入 savedInstanceState
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    // [修复防御]: 成功后弹 snackbar 并返回；consumeSuccess 防止配置变更后重复触发
    LaunchedEffect(uiState.success) {
        if (uiState.success) {
            snackbarHostState.showSnackbar("密码修改成功", duration = SnackbarDuration.Custom(1500))
            viewModel.consumeSuccess()
            onBack()
        }
    }

    LaunchedEffect(uiState.error) {
        val msg = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Custom(1800))
        viewModel.clearError()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = "修改密码",
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = BadgerSpacing.md, vertical = BadgerSpacing.sm),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(BadgerSpacing.lg)) {
                    PasswordField(
                        label = "旧密码",
                        value = oldPassword,
                        onValueChange = { oldPassword = it },
                        passwordVisible = passwordVisible,
                        onToggleVisibility = { passwordVisible = !passwordVisible },
                        imeAction = ImeAction.Next,
                        focusManager = focusManager,
                    )
                    Spacer(Modifier.height(BadgerSpacing.lg))
                    PasswordField(
                        label = "新密码",
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        passwordVisible = passwordVisible,
                        onToggleVisibility = { passwordVisible = !passwordVisible },
                        imeAction = ImeAction.Next,
                        focusManager = focusManager,
                    )
                    Spacer(Modifier.height(BadgerSpacing.lg))
                    PasswordField(
                        label = "确认新密码",
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        passwordVisible = passwordVisible,
                        onToggleVisibility = { passwordVisible = !passwordVisible },
                        imeAction = ImeAction.Done,
                        focusManager = focusManager,
                        onDone = {
                            focusManager.clearFocus()
                            viewModel.changePassword(oldPassword, newPassword, confirmPassword)
                        },
                    )
                    Spacer(Modifier.height(BadgerSpacing.xl))
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.changePassword(oldPassword, newPassword, confirmPassword)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.loading &&
                            oldPassword.isNotBlank() &&
                            newPassword.isNotBlank() &&
                            confirmPassword.isNotBlank(),
                    ) {
                        Text(text = if (uiState.loading) "提交中..." else "确认修改")
                    }
                }
            }
        }
    }
}

/** 密码输入字段：label + 密码遮掩 + 可见性切换 + IME action。 */
@Composable
private fun PasswordField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    passwordVisible: Boolean,
    onToggleVisibility: () -> Unit,
    imeAction: ImeAction,
    focusManager: FocusManager,
    onDone: (() -> Unit)? = null,
) {
    Text(
        text = label,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
    Spacer(Modifier.height(BadgerSpacing.xs))
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
            onDone = onDone?.let { { it() } },
        ),
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                )
            }
        },
    )
}