package top.mcxiafeng.badger.pages.settings.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Link
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import top.mcxiafeng.badger.network.ServerShortLink
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.components.BadgerEmptyState
import top.mcxiafeng.badger.ui.components.DialogButtonRow
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.BasicComponent
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
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 自建短链管理页。
 *
 * 列表 + 创建 + 编辑 + 删除。与 short.io 代理不同，本页走 `/api/shortlinks/` 路径。
 */
@Composable
internal fun ServerShortLinkPage(
    onBack: () -> Unit,
    onNavigateToLogin: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: ServerShortLinkViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var editingLink by remember { mutableStateOf<ServerShortLink?>(null) }
    var deletingLink by remember { mutableStateOf<ServerShortLink?>(null) }

    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) viewModel.refresh()
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
                title = "自建短链",
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        floatingActionButton = {
            if (uiState.isLoggedIn) {
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "创建短链",
                        tint = MiuixTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            when {
                !uiState.isLoggedIn -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(bottom = floatingBarBottomPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        BadgerEmptyState(
                            icon = Icons.Outlined.Link,
                            title = "还没有短链",
                            subtitle = "登录账号后即可管理自建短链。",
                            actionLabel = "去登录",
                            onAction = onNavigateToLogin,
                        )
                    }
                }
                else -> {
                    val pullState = rememberPullToRefreshState()
                    PullToRefresh(
                        isRefreshing = uiState.loading,
                        onRefresh = { viewModel.refresh() },
                        pullToRefreshState = pullState,
                        contentPadding = PaddingValues(top = BadgerSpacing.sm),
                    ) {
                        if (uiState.links.isEmpty() && !uiState.loading) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(bottom = floatingBarBottomPadding),
                                contentAlignment = Alignment.Center,
                            ) {
                                BadgerEmptyState(
                                    icon = Icons.Outlined.Link,
                                    title = "还没有短链",
                                    subtitle = "点击右下角按钮创建第一个短链。",
                                    actionLabel = "刷新",
                                    onAction = { viewModel.refresh() },
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = BadgerSpacing.md, end = BadgerSpacing.md,
                                    top = BadgerSpacing.sm, bottom = BadgerSpacing.sm + floatingBarBottomPadding,
                                ),
                                verticalArrangement = Arrangement.spacedBy(BadgerSpacing.sm),
                            ) {
                                uiState.config?.let { config ->
                                    item(key = "config") {
                                        Card(modifier = Modifier.fillMaxWidth()) {
                                            BasicComponent(
                                                title = "自建短链",
                                                summary = if (config.serverEnabled) "已启用" else "未启用",
                                            )
                                        }
                                    }
                                }
                                items(uiState.links, key = { it.uuid }) { link ->
                                    ServerShortLinkRow(
                                        link = link,
                                        onEdit = { editingLink = link },
                                        onDelete = { deletingLink = link },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 对话框 ──────────────────────────────────────────────────────

    if (showCreateDialog) {
        ShortLinkFormDialog(
            title = "创建短链",
            submitText = "创建",
            initialURL = "",
            initialCode = "",
            onConfirm = { url, code ->
                if (url != null) viewModel.createLink(url, code)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    editingLink?.let { link ->
        ShortLinkFormDialog(
            title = "编辑短链",
            submitText = "保存",
            initialURL = link.originalURL,
            initialCode = link.code ?: "",
            onConfirm = { url, code ->
                viewModel.updateLink(link.uuid, url, code)
                editingLink = null
            },
            onDismiss = { editingLink = null },
        )
    }

    deletingLink?.let { link ->
        DeleteConfirmDialog(
            link = link,
            onConfirm = { viewModel.deleteLink(link.uuid); deletingLink = null },
            onDismiss = { deletingLink = null },
        )
    }
}

// ── 私有组件 ────────────────────────────────────────────────────────

@Composable
private fun ServerShortLinkRow(
    link: ServerShortLink,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(BadgerSpacing.md)) {
            Text(
                text = link.shortURL ?: link.code ?: link.uuid.take(8),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(BadgerSpacing.xxs))
            Text(
                text = link.originalURL,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(BadgerSpacing.sm))
            Row {
                Button(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("编辑") }
                Spacer(Modifier.width(BadgerSpacing.sm))
                Button(onClick = onDelete, modifier = Modifier.weight(1f)) { Text("删除") }
            }
        }
    }
}

/**
 * 创建 / 编辑统一表单。
 *
 * 创建时 [initialURL] 和 [initialCode] 为空；编辑时预填原值。
 * [onConfirm] 仅传递变更后的值（编辑场景中未修改的字段返回 null）。
 */
@Composable
private fun ShortLinkFormDialog(
    title: String,
    submitText: String,
    initialURL: String,
    initialCode: String,
    onConfirm: (url: String?, code: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf(initialURL) }
    var code by remember { mutableStateOf(initialCode) }
    WindowDialog(show = true, title = title, summary = "", onDismissRequest = onDismiss) {
        val isEdit = initialURL.isNotEmpty()
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = "原始 URL", style = MiuixTheme.textStyles.body2)
            Spacer(Modifier.height(BadgerSpacing.xs))
            TextField(value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(BadgerSpacing.md))
            Text(text = if (isEdit) "短码" else "自定义短码（可选）", style = MiuixTheme.textStyles.body2)
            Spacer(Modifier.height(BadgerSpacing.xs))
            TextField(value = code, onValueChange = { code = it }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(BadgerSpacing.lg))
            DialogButtonRow(
                positiveText = submitText,
                onNegative = onDismiss,
                onPositive = {
                    onConfirm(
                        // 创建：始终传 url（VM 负责校验空值）；编辑：仅传变更值
                        if (isEdit) url.takeIf { it != initialURL } else url,
                        code.takeIf { it != initialCode }?.takeIf { it.isNotBlank() },
                    )
                },
            )
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    link: ServerShortLink,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    WindowDialog(show = true, title = "确认删除", summary = "删除后不可恢复，确定要删除这条短链吗？", onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = link.shortURL ?: link.code ?: link.uuid.take(8),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(BadgerSpacing.lg))
            DialogButtonRow(positiveText = "删除", onNegative = onDismiss, onPositive = onConfirm)
        }
    }
}
