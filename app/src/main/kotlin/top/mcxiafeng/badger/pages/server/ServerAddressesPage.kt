package top.mcxiafeng.badger.pages.server


import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.network.core.ServerAddress
import top.mcxiafeng.badger.ui.components.DialogButtonRow
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.IconButton
import top.mcxiafeng.badger.ui.glass.Scaffold
import top.mcxiafeng.badger.ui.glass.TopAppBar
import top.mcxiafeng.badger.ui.glass.scrollTopFade
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAG = "ServerAddressesPageTester"

/** 连接地址管理页（移植自 dev 分支 page/server/ServerAddressesPage）：别名多入口 + 拖动排序 + 自动测试。 */
@Composable
fun ServerAddressesPage(
    onBack: () -> Unit,
    viewModel: ServerAddressesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showEditor by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ServerAddress?>(null) }

    LaunchedEffect(Unit) { Log.d(TAG, "进入连接地址页") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "连接地址",
                navigationIcon = {
                    IconButton(onClick = {
                        Log.d(TAG, "返回上一页")
                        onBack()
                    }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        Log.d(TAG, "打开新增地址弹窗")
                        viewModel.cancelEdit()
                        showEditor = true
                    }) {
                        Icon(imageVector = Icons.Outlined.Add, contentDescription = "添加连接地址")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().consumeWindowInsets(padding)) {
            val scrollState = rememberScrollState()
            val hasAddresses = uiState.activeBase != null || uiState.servers.isNotEmpty()
            // Insets live inside the scrolling viewport so rows pass behind the blurred top bar.
            Column(
                modifier = Modifier.fillMaxSize()
                    .then(if (hasAddresses) Modifier.scrollTopFade().verticalScroll(scrollState) else Modifier)
                    .padding(horizontal = BadgerSpacing.lg),
            ) {
                Spacer(modifier = Modifier.height(padding.calculateTopPadding() + BadgerSpacing.md +
                    if (hasAddresses) BadgerSpacing.scrollContentTopGap else 0.dp))
                uiState.activeBase?.let { CurrentAddressCard(it) }
                if (uiState.servers.isNotEmpty()) {
                    ConnectionOrderHeader(canReorder = uiState.servers.size > 1)
                }
                AddressListSection(
                    servers = uiState.servers,
                    activeBase = uiState.activeBase,
                    testing = uiState.testing,
                    testResults = uiState.testResults,
                    onReorder = viewModel::reorderAddresses,
                    onEdit = { address ->
                        Log.d(TAG, "打开地址编辑弹窗")
                        viewModel.startEdit(address)
                        showEditor = true
                    },
                    onRemove = { address ->
                        Log.d(TAG, "请求删除地址")
                        pendingDelete = address
                    },
                    onAdd = {
                        Log.d(TAG, "空列表打开新增地址弹窗")
                        viewModel.cancelEdit()
                        showEditor = true
                    },
                )
                Spacer(modifier = Modifier.height(BadgerSpacing.xl + padding.calculateBottomPadding()))
            }
            SnackbarHost(
                state = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = SNACKBAR_BOTTOM_CLEARANCE + padding.calculateBottomPadding()),
            )
        }
    }
    if (showEditor) {
        AddressEditorDialog(
            editingUrl = uiState.editingUrl,
            formName = uiState.formName,
            formUrl = uiState.formUrl,
            formHttps = uiState.formHttps,
            formUrlReason = uiState.formUrlReason,
            canSave = uiState.canSave,
            onName = viewModel::onFormNameChange,
            onUrl = viewModel::onFormUrlChange,
            onHttps = viewModel::onFormHttpsChange,
            onSave = {
                scope.launch {
                    val error = viewModel.saveForm()
                    if (error == null) {
                        Log.d(TAG, "地址已保存，关闭编辑弹窗")
                        showEditor = false
                    } else {
                        Log.w(TAG, "保存被拦：$error")
                        snackbarHostState.showSnackbar(error)
                    }
                }
            },
            onCancel = {
                Log.d(TAG, "取消地址编辑")
                showEditor = false
                viewModel.cancelEdit()
            },
        )
    }
    pendingDelete?.let { address ->
        // 删除确认：WindowDialog Pattern A，确认/取消/点外部三路径都复位 pendingDelete
        WindowDialog(
            show = true,
            title = "删除连接地址",
            summary = "确定删除「${address.name.ifBlank { address.url }}」？删除后将不再尝试这个地址。",
            onDismissRequest = { pendingDelete = null },
        ) {
            DialogButtonRow(
                negativeText = "取消",
                positiveText = "删除",
                isDestructive = true,
                onNegative = { pendingDelete = null },
                onPositive = {
                    pendingDelete = null
                    Log.d(TAG, "确认删除地址")
                    viewModel.removeAddress(address.url)
                },
            )
        }
    }
}

@Composable
private fun CurrentAddressCard(url: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(title = "当前地址", summary = url)
    }
}

@Composable
private fun ConnectionOrderHeader(canReorder: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmallTitle(text = "连接顺序", modifier = Modifier.weight(1f))
        if (canReorder) {
            Text(
                text = "拖动右侧手柄排序",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
    Spacer(modifier = Modifier.height(BadgerSpacing.sm))
}
