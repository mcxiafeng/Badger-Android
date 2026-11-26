package top.mcxiafeng.badger.pages.account

import top.mcxiafeng.badger.ui.glass.scrollTopFade
import top.mcxiafeng.badger.ui.glass.springOverscroll

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import top.mcxiafeng.badger.ui.designsystem.BadgerShapes
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import top.mcxiafeng.badger.ui.designsystem.continuousClip as clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.mcxiafeng.badger.data.account.AccountScope
import top.mcxiafeng.badger.ui.components.DialogButtonRow
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.glass.Button
import top.mcxiafeng.badger.ui.glass.IconButton
import top.mcxiafeng.badger.ui.glass.Scaffold
import top.mcxiafeng.badger.ui.glass.TextButton
import top.mcxiafeng.badger.ui.glass.TopAppBar
import top.mcxiafeng.badger.ui.glass.WindowDialog
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.mcxiafeng.badger.ui.components.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AccountSwitchPage(
    onBack: () -> Unit,
    onLoginNewAccount: () -> Unit,
    viewModel: AccountSwitchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) { onDispose { viewModel.onExit() } }
    val back = {
        when {
            state.showLocalConfirmation -> viewModel.dismissLocalConfirmation()
            state.selected != null -> viewModel.showAccounts()
            else -> { viewModel.onExit(); onBack() }
        }
    }
    AccountSwitchScreen(state, AccountSwitchCallbacks(
        onBack = back,
        onSelect = viewModel::select,
        onPassword = viewModel::onPassword,
        onTogglePassword = viewModel::togglePasswordVisibility,
        onSubmit = viewModel::signIn,
        onLoginNewAccount = { viewModel.onExit(); onLoginNewAccount() },
        onDismissLocal = viewModel::dismissLocalConfirmation,
        onConfirmLocal = viewModel::confirmLocal,
    ))
}

internal data class AccountSwitchCallbacks(
    val onBack: () -> Unit = {},
    val onSelect: (String) -> Unit = {},
    val onPassword: (String) -> Unit = {},
    val onTogglePassword: () -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onLoginNewAccount: () -> Unit = {},
    val onDismissLocal: () -> Unit = {},
    val onConfirmLocal: () -> Unit = {},
)

@Composable
internal fun AccountSwitchScreen(state: AccountSwitchUiState, callbacks: AccountSwitchCallbacks) {
    BackHandler(onBack = callbacks.onBack)
    Scaffold(topBar = { TopAppBar(title = "切换账号", navigationIcon = {
        IconButton(onClick = callbacks.onBack, modifier = Modifier.sizeIn(
            minWidth = BadgerSize.minimumTouchTarget, minHeight = BadgerSize.minimumTouchTarget)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
    }) }) { inset ->
        Box(Modifier.fillMaxSize().padding(inset).imePadding(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(modifier = Modifier.widthIn(max = BadgerSize.contentMaxWidth).fillMaxSize().scrollTopFade().springOverscroll(),
                contentPadding = PaddingValues(start = BadgerSpacing.lg, end = BadgerSpacing.lg,
                    top = BadgerSpacing.lg + BadgerSpacing.scrollContentTopGap, bottom = BadgerSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(BadgerSpacing.cardGap)) {
                if (state.selected == null) {
                    item { CurrentSwitchAccount(state.current) }
                    items(state.accounts.filter { it.key != state.current.key }, key = { it.key }) { account ->
                        SwitchAccountRow(account, !state.busy) { callbacks.onSelect(account.key) }
                    }
                    item { TextButton(text = "登录其他账号", enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = BadgerSize.minimumTouchTarget),
                        colors = ButtonDefaults.textButtonColorsPrimary(), onClick = callbacks.onLoginNewAccount) }
                    state.error?.let { message -> item { AccountSwitchError(message) } }
                } else {
                    item { SwitchVerification(state, callbacks) }
                }
            }
        }
    }
    if (state.showLocalConfirmation) LocalAccountConfirmation(state.current.username, callbacks)
}

@Composable
private fun CurrentSwitchAccount(account: AccountScope) {
    Row(Modifier.fillMaxWidth().padding(BadgerSpacing.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.md)) {
        Column(Modifier.weight(1f)) { SwitchAccountIdentity(account) }
        Text("当前使用", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.primary)
    }
}

@Composable
private fun SwitchAccountIdentity(account: AccountScope) {
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(BadgerSpacing.sm)) {
            Text(account.username, style = MiuixTheme.textStyles.title4)
            if (!account.isLocal) Text(account.baseUrl, style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun SwitchAccountRow(account: AccountScope, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(BadgerShapes.Surface)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .heightIn(min = BadgerSize.minimumTouchTarget).padding(BadgerSpacing.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.sm)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BadgerSpacing.sm)) {
            Text(account.username, style = MiuixTheme.textStyles.title4)
            if (!account.isLocal) Text(account.baseUrl,
                style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
private fun SwitchVerification(state: AccountSwitchUiState, callbacks: AccountSwitchCallbacks) {
    Column(verticalArrangement = Arrangement.spacedBy(BadgerSpacing.lg)) {
        SwitchAccountIdentity(checkNotNull(state.selected))
        AccountSwitchPassword(state, callbacks)
        state.error?.let { AccountSwitchError(it) }
        Button(onClick = callbacks.onSubmit, enabled = state.canSubmit, colors = ButtonDefaults.buttonColorsPrimary(),
            modifier = Modifier.fillMaxWidth().heightIn(min = BadgerSize.minimumTouchTarget)) {
            Text(if (state.busy) "正在验证…" else "登录并切换")
        }
        if (state.error != null) TextButton(text = "登录其他账号", onClick = callbacks.onLoginNewAccount, enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = BadgerSize.minimumTouchTarget))
    }
}

@Composable
private fun AccountSwitchPassword(state: AccountSwitchUiState, callbacks: AccountSwitchCallbacks) {
    val hiddenPassword = remember { PasswordVisualTransformation() }
    TextField(value = state.password, onValueChange = callbacks.onPassword, label = "密码",
        singleLine = true, enabled = !state.busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = BadgerSize.minimumTouchTarget)
            .semantics { contentDescription = "账号密码" },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (state.canSubmit) callbacks.onSubmit() }),
        visualTransformation = if (state.passwordVisible) VisualTransformation.None else hiddenPassword,
        trailingIcon = {
            IconButton(onClick = callbacks.onTogglePassword, modifier = Modifier.sizeIn(
                minWidth = BadgerSize.minimumTouchTarget, minHeight = BadgerSize.minimumTouchTarget)) {
                Icon(if (state.passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (state.passwordVisible) "隐藏密码" else "显示密码")
            }
        })
}

@Composable
private fun AccountSwitchError(message: String) {
    Text(message, color = MiuixTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
private fun LocalAccountConfirmation(currentName: String, callbacks: AccountSwitchCallbacks) {
    WindowDialog(show = true, title = "切换到本地用户？", onDismissRequest = callbacks.onDismissLocal) {
        Column(verticalArrangement = Arrangement.spacedBy(BadgerSpacing.lg)) {
            Text("退出“$currentName”，本机资料（含未同步内容）保留。")
            DialogButtonRow(negativeText = "取消", positiveText = "切换",
                onNegative = callbacks.onDismissLocal, onPositive = callbacks.onConfirmLocal)
        }
    }
}
