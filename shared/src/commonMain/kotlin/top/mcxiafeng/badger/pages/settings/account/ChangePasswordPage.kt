package top.mcxiafeng.badger.pages.settings.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import org.koin.compose.viewmodel.koinViewModel
import top.mcxiafeng.badger.pages.settings.components.SETTINGS_SNACKBAR_DURATION_MS
import top.mcxiafeng.badger.pages.settings.components.SettingsSubPageScaffold
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.navigation.SettingsPage
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Lucide

/**
 * 修改密码页（重写：共享脚手架 + snackbar 时长常量）。
 *
 * 旧密码 + 新密码 + 确认 + 提交。成功 → snackbar → 返回；失败 → snackbar。
 */
@Composable
internal fun ChangePasswordPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ChangePasswordViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    // 密码不用 rememberSaveable，避免明文写入 savedInstanceState
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.success) {
        if (uiState.success) {
            snackbarHostState.showSnackbar(
                "密码修改成功",
                duration = SnackbarDuration.Custom(SETTINGS_SNACKBAR_DURATION_MS),
            )
            viewModel.consumeSuccess()
            onBack()
        }
    }

    LaunchedEffect(uiState.error) {
        val msg = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            msg,
            duration = SnackbarDuration.Custom(SETTINGS_SNACKBAR_DURATION_MS),
        )
        viewModel.clearError()
    }

    SettingsSubPageScaffold(
        title = SettingsPage.ChangePassword.title,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
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
                    imageVector = if (passwordVisible) Lucide.EyeOff else Lucide.Eye,
                    contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                )
            }
        },
    )
}
