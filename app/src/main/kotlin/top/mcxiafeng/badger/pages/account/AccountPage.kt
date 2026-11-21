package top.mcxiafeng.badger.pages.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.mcxiafeng.badger.data.sync.ConflictItem
import top.mcxiafeng.badger.data.sync.SyncProgress
import top.mcxiafeng.badger.ui.components.DialogButtonRow
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun AccountUiHost(onImport: () -> Unit, viewModel: AccountViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.startAutomaticSync() }
    if (state.offerImport) WindowDialog(show = true, title = "导入本地资料？", onDismissRequest = viewModel::dismissOffer) {
        Column(verticalArrangement = Arrangement.spacedBy(BadgerSpacing.lg)) {
            Text("是否将本地用户的资料复制到“${state.name}”？包括备注、扫描来源及图片，本地原件会保留。")
            DialogButtonRow(negativeText = "暂不导入", positiveText = "查看资料",
                onNegative = viewModel::dismissOffer, onPositive = { viewModel.dismissOffer(); onImport() })
        }
    }
}

@Composable
fun AccountPage(section: String, onBack: () -> Unit, onLogin: () -> Unit, viewModel: AccountViewModel = hiltViewModel(),
    onManageTags: (() -> Unit)? = null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    var logoutDialog by remember { mutableStateOf(false) }
    LaunchedEffect(section) { viewModel.open(section) }
    val back = {
        when {
            state.selectedConflict != null -> viewModel.showConflict(null)
            state.section != section -> viewModel.open(section)
            else -> onBack()
        }
    }
    BackHandler(enabled = state.selectedConflict != null || state.section != section, onBack = back)
    Scaffold(topBar = { TopAppBar(title = accountTitle(state), navigationIcon = {
        IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
    }) }) { inset ->
        AccountContent(state, progress, Modifier.padding(inset), viewModel, onLogin, { logoutDialog = true }, onManageTags)
    }
    if (logoutDialog) WindowDialog(show = true, title = "退出云端账号？", onDismissRequest = { logoutDialog = false }) {
        Column(verticalArrangement = Arrangement.spacedBy(BadgerSpacing.lg)) {
            Text("退出后返回本地用户，此账号在本机的资料和未同步内容会保留。")
            DialogButtonRow(negativeText = "取消", positiveText = "退出账号", onNegative = { logoutDialog = false },
                onPositive = { logoutDialog = false; viewModel.logout() })
        }
    }
}

@Composable
private fun AccountContent(state: AccountUiState, progress: SyncProgress, modifier: Modifier,
    viewModel: AccountViewModel, onLogin: () -> Unit, onLogout: () -> Unit, onManageTags: (() -> Unit)?) {
    val selected = state.conflicts.firstOrNull { it.key == state.selectedConflict }
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(BadgerSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(BadgerSpacing.cardGap)) {
        state.error?.let { message -> item {
            AccountOperationError(message, state.section == "import", state.busy, viewModel::rebuildImport)
        } }
        state.message?.let { message -> item { Text(message, style = MiuixTheme.textStyles.body2) } }
        if (state.busy) item { Text("正在处理…", style = MiuixTheme.textStyles.body2) }
        when (state.section) {
            "overview" -> item { AccountOverview(state, progress, onLogin, viewModel::open, viewModel::synchronize, onLogout, onManageTags) }
            "import" -> {
                val plan = state.importPlan
                if (plan != null) {
                    item { ImportScopeInfo(state.name, state.serverAddress) }
                    items(plan.items, key = { it.key }) { LocalImportRow(it, !state.busy, viewModel::selectImport) }
                    item { Button(onClick = viewModel::confirmImport, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("确认导入") } }
                } else if (!state.busy) item {
                    Button(onClick = { viewModel.open("import") }, modifier = Modifier.fillMaxWidth()) { Text("重新查看本地资料") }
                }
            }
            "conflicts" -> {
                if (selected != null) {
                    item { Text(selected.title, style = MiuixTheme.textStyles.title3) }
                    items(selected.differences) { DifferenceCard(it) }
                    item { ConflictResolution(selected, state.busy) { local, both -> viewModel.resolveConflict(selected.key, local, both) } }
                } else if (state.conflicts.isEmpty() && !state.busy) item { Text("没有待处理的冲突") }
                else items(state.conflicts, key = { it.key }) { conflict ->
                    Card(onClick = { viewModel.showConflict(conflict.key) }, insideMargin = PaddingValues(BadgerSpacing.lg)) {
                        Column(verticalArrangement = Arrangement.spacedBy(BadgerSpacing.sm)) {
                            Text(conflict.title, style = MiuixTheme.textStyles.title4)
                            Text("${conflict.differences.size} 项分歧", style = MiuixTheme.textStyles.body2)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountOverview(state: AccountUiState, progress: SyncProgress, onLogin: () -> Unit,
    onSection: (String) -> Unit, onSync: () -> Unit, onLogout: () -> Unit, onManageTags: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(BadgerSpacing.lg)) {
        Text(state.name, style = MiuixTheme.textStyles.title3)
        if (state.local) {
            Text("资料保存在本机，登录后可以选择导入云端账号。", style = MiuixTheme.textStyles.body2)
            Button(onClick = onLogin, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("登录云端账号") }
        } else {
            Text(state.serverAddress, style = MiuixTheme.textStyles.body2)
            Text(progress.message, style = MiuixTheme.textStyles.body2)
            Button(onClick = onSync, enabled = !state.busy && !progress.running, modifier = Modifier.fillMaxWidth()) { Text("同步资料") }
            Card(insideMargin = PaddingValues()) {
                if (progress.tagNameConflict && onManageTags != null)
                    ArrowPreference(title = "调整标签名称", summary = "先处理同名标签，再继续同步", onClick = onManageTags)
                ArrowPreference(title = "本地资料导入", summary = "预览后复制到此账号", onClick = { onSection("import") })
                ArrowPreference(title = "冲突处理", summary = if (progress.conflicts > 0) "${progress.conflicts} 项待处理" else "查看需要你决定的内容", onClick = { onSection("conflicts") })
                ArrowPreference(title = "登录与切换账号", onClick = onLogin)
                ArrowPreference(title = "退出账号", summary = "返回本地用户，本机资料保留", onClick = onLogout)
            }
        }
    }
}

private fun accountTitle(state: AccountUiState): String = when (state.section) {
    "import" -> "导入本地资料"
    "conflicts" -> "冲突处理"
    else -> "用户与同步"
}
