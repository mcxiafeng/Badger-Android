package top.mcxiafeng.badger.pages.account

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import top.mcxiafeng.badger.data.sync.ConflictItem
import top.mcxiafeng.badger.data.sync.SyncProgress
import top.mcxiafeng.badger.ui.components.DialogButtonRow
import top.mcxiafeng.badger.ui.components.WorkspacePullRefresh
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.glass.Button
import top.mcxiafeng.badger.ui.glass.IconButton
import top.mcxiafeng.badger.ui.glass.Scaffold
import top.mcxiafeng.badger.ui.glass.TopAppBar
import top.mcxiafeng.badger.ui.glass.WindowDialog
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.mcxiafeng.badger.ui.components.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAG = "AccountPageTester"

@Composable
fun AccountUiHost(onImport: () -> Unit, viewModel: AccountViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, viewModel) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.observeForegroundSync() }
    }
    LaunchedEffect(viewModel) { viewModel.refreshImportOffer() }
    if (state.offerImport) WindowDialog(show = true, title = "导入本地资料？", onDismissRequest = viewModel::dismissOffer) {
        Column(verticalArrangement = Arrangement.spacedBy(BadgerSpacing.lg)) {
            Text("复制到“${state.name}”，含备注、来源记录和图片；本地原件保留。")
            DialogButtonRow(negativeText = "暂不导入", positiveText = "查看资料",
                onNegative = viewModel::dismissOffer, onPositive = { viewModel.dismissOffer(); onImport() })
        }
    }
}

@Composable
fun AccountPage(section: String, onBack: () -> Unit, onLogin: () -> Unit, viewModel: AccountViewModel = hiltViewModel(),
    onManageTags: (() -> Unit)? = null, onReauthenticate: (() -> Unit)? = null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    var logoutDialog by rememberSaveable { mutableStateOf(false) }
    val pageStates = rememberSaveableStateHolder()
    LaunchedEffect(section, viewModel) { viewModel.open(section) }
    val inChildSection = section == "overview" && state.section != section
    val back = {
        Log.d(TAG, "返回账号任务，section=${state.section} detail=${state.selectedConflict != null}")
        when {
            state.selectedConflict != null -> viewModel.showConflict(null)
            inChildSection -> viewModel.open(section)
            else -> onBack()
        }
    }
    BackHandler(enabled = state.selectedConflict != null || inChildSection, onBack = back)
    Scaffold(topBar = { TopAppBar(title = accountTitle(state), navigationIcon = {
        IconButton(onClick = back, icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
    }) }) { inset ->
        Box(Modifier.fillMaxSize().padding(inset), contentAlignment = Alignment.TopCenter) {
            val pageKey = state.selectedConflict ?: state.section
            key(pageKey) { pageStates.SaveableStateProvider(pageKey) {
                AccountContent(state, progress, Modifier.widthIn(max = BadgerSize.contentMaxWidth).fillMaxSize(),
                    viewModel, onLogin, { logoutDialog = true }, onManageTags, onReauthenticate ?: onLogin)
            } }
        }
    }
    if (logoutDialog) AccountLogoutDialog(state.name, !state.busy, { logoutDialog = false }) {
        logoutDialog = false
        viewModel.logout()
    }
}

@Composable
private fun AccountContent(state: AccountUiState, progress: SyncProgress, modifier: Modifier,
    viewModel: AccountViewModel, onLogin: () -> Unit, onLogout: () -> Unit, onManageTags: (() -> Unit)?,
    onReauthenticate: () -> Unit) {
    if (state.section == "import" && state.importEmpty && state.error == null) {
        AccountImportEmpty(state, viewModel::rebuildImport, modifier)
        return
    }
    if (state.section == "conflicts" && state.conflicts.isEmpty() && !state.busy && state.error == null) {
        AccountEmptyState("没有待处理的冲突", state.message, modifier)
        return
    }
    val items: LazyListScope.() -> Unit = {
        accountFeedback(state, viewModel, onReauthenticate)
        when (state.section) {
            "overview" -> item { AccountOverview(state, progress, onLogin, viewModel::open,
                viewModel::synchronize, onLogout, onManageTags, onReauthenticate) }
            "import" -> importItems(state, viewModel)
            "conflicts" -> conflictItems(state, viewModel)
        }
    }
    if (state.section == "overview" && !state.local) {
        WorkspacePullRefresh(viewModel.refreshing, viewModel::synchronize, modifier,
            enabled = !state.busy && !needsAccountLogin(progress.message)) {
            AccountList(Modifier.fillMaxSize(), items)
        }
    } else {
        AccountList(modifier, items)
    }
}

@Composable
private fun AccountList(modifier: Modifier, content: LazyListScope.() -> Unit) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(BadgerSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(BadgerSpacing.cardGap), content = content)
}

private fun LazyListScope.accountFeedback(state: AccountUiState, viewModel: AccountViewModel, onLogin: () -> Unit) {
    state.error?.let { message -> item(key = "operation_error") {
        AccountOperationError(message, state.section == "import", state.busy, retry = {
            when (state.section) {
                "import" -> viewModel.rebuildImport()
                "conflicts" -> viewModel.open("conflicts")
                else -> viewModel.synchronize()
            }
        }, onLogin = onLogin)
    } }
    state.message?.let { message -> item(key = "operation_result") {
        Text(message, style = MiuixTheme.textStyles.body2,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    } }
    if (state.busy && state.section != "overview") item(key = "operation_loading") {
        CircularProgressIndicator(size = BadgerSpacing.xl,
            modifier = Modifier.semantics { contentDescription = "正在处理" })
    }
}

private fun LazyListScope.importItems(state: AccountUiState, viewModel: AccountViewModel) {
    item(key = "import_target") { ImportScopeInfo(state.name) }
    val plan = state.importPlan
    if (plan != null) {
        items(plan.items, key = { it.key }) { LocalImportRow(it, !state.busy, viewModel::selectImport) }
        item(key = "import_confirm") {
            Column(verticalArrangement = Arrangement.spacedBy(BadgerSpacing.md)) {
                Text("含备注、来源记录和图片，原件保留；名片夹仅带入所选联系人。", style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Button(onClick = viewModel::confirmImport, enabled = !state.busy && state.importSelectionCount > 0,
                    colors = ButtonDefaults.buttonColorsPrimary(), modifier = Modifier.fillMaxWidth()) {
                    Text("导入所选（${state.importSelectionCount} 项）")
                }
            }
        }
    } else if (!state.busy && state.error == null) item(key = "import_reload") {
        Button(onClick = { viewModel.open("import") }, modifier = Modifier.fillMaxWidth()) { Text("重新查看本地资料") }
    }
}

private fun LazyListScope.conflictItems(state: AccountUiState, viewModel: AccountViewModel) {
    val selected = state.conflicts.firstOrNull { it.key == state.selectedConflict }
    if (selected != null) {
        item(key = "conflict_identity") { ConflictHeading(selected) }
        items(selected.differences) { DifferenceCard(it) }
        item(key = "conflict_resolution") { ConflictResolution(selected, state.busy) { local, both ->
            viewModel.resolveConflict(selected, local, both)
        } }
    } else {
        items(state.conflicts, key = { it.key }) { conflict ->
            Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues()) {
                ArrowPreference(title = conflict.title, summary = "${conflict.differences.size} 项分歧",
                    enabled = !state.busy, onClick = { viewModel.showConflict(conflict.key) })
            }
        }
    }
}

@Composable
private fun ConflictHeading(conflict: ConflictItem) {
    Column(verticalArrangement = Arrangement.spacedBy(BadgerSpacing.sm)) {
        Text(conflict.title, style = MiuixTheme.textStyles.title3)
        Text("${conflict.differences.size} 项分歧", style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun AccountEmptyState(title: String, message: String?, modifier: Modifier) {
    Box(modifier.padding(BadgerSpacing.lg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BadgerSpacing.md)) {
            Text(title, style = MiuixTheme.textStyles.body1)
            message?.let { Text(it, style = MiuixTheme.textStyles.body2) }
        }
    }
}

@Composable
private fun AccountLogoutDialog(name: String, enabled: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    WindowDialog(show = true, title = "退出“$name”？", onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(BadgerSpacing.lg)) {
            Text("退出后返回本地用户，本机资料（含未同步内容）保留。")
            DialogButtonRow(negativeText = "取消", positiveText = "退出账号", onNegative = onDismiss,
                positiveEnabled = enabled, onPositive = onConfirm)
        }
    }
}

private fun accountTitle(state: AccountUiState): String = when {
    state.selectedConflict != null -> "核对资料差异"
    state.section == "import" -> "本地资料导入"
    state.section == "conflicts" -> "冲突处理"
    else -> "用户与同步"
}
