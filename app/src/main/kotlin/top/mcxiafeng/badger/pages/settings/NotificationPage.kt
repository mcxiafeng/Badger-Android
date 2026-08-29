package top.mcxiafeng.badger.pages.settings

import android.util.Log
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import top.mcxiafeng.badger.network.UserNotification
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.components.EmptyStateView
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "NotificationPage"

/**
 * [B2] 站内通知列表。
 *
 * - 点击未读行 → 标记已读（已读行只展示）
 * - 左滑删除（失败不落库、行回弹；有 snackbar）
 * - 下拉刷新全量列表 + 未读数
 *
 * [C4] 新增：全部/未读筛选 Tab + 点击通知跳转到关联实体（联系人/标签/名片夹）。
 */
@Composable
internal fun NotificationPage(
    onBack: () -> Unit,
    onNavigateToLogin: () -> Unit = {},
    onNavigateToContact: (Long) -> Unit = {},
) {
    val viewModel: NotificationViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) {
            Log.d(TAG, "NotificationPage: first load")
            viewModel.refresh()
        }
    }

    LaunchedEffect(uiState.error) {
        val msg = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = msg,
            duration = SnackbarDuration.Custom(1800),
        )
        viewModel.clearError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "通知",
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                !uiState.isLoggedIn -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = floatingBarBottomPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyStateView(
                            icon = Icons.Outlined.Notifications,
                            title = "登录后查看通知",
                            subtitle = "站内通知需要登录账号后同步。",
                            actionLabel = "去登录",
                            onAction = onNavigateToLogin,
                        )
                    }
                }
                else -> {
                    // [C4] 全部/未读筛选 Tab
                    NotificationFilterTab(
                        currentFilter = uiState.filter,
                        unreadCount = uiState.unreadCount,
                        onFilterChange = { viewModel.setFilter(it) },
                    )
                    val pullState = rememberPullToRefreshState()
                    PullToRefresh(
                        isRefreshing = uiState.loading,
                        onRefresh = {
                            Log.d(TAG, "NotificationPage: pull-to-refresh")
                            viewModel.refresh()
                        },
                        pullToRefreshState = pullState,
                        contentPadding = PaddingValues(top = 8.dp),
                    ) {
                        if (uiState.items.isEmpty() && !uiState.loading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = floatingBarBottomPadding),
                                contentAlignment = Alignment.Center,
                            ) {
                                val emptyTitle = if (uiState.filter == NotificationFilter.UNREAD) {
                                    "没有未读通知"
                                } else {
                                    "暂无通知"
                                }
                                EmptyStateView(
                                    icon = Icons.Outlined.Notifications,
                                    title = emptyTitle,
                                    subtitle = "有新消息时会显示在这里，也可下拉刷新。",
                                    actionLabel = "刷新",
                                    onAction = { viewModel.refresh() },
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 12.dp,
                                    end = 12.dp,
                                    top = 8.dp,
                                    bottom = 8.dp + floatingBarBottomPadding,
                                ),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(uiState.items, key = { it.uuid }) { item ->
                                    NotificationSwipeRow(
                                        item = item,
                                        onClick = {
                                            // [C4] 有 entityType+entityId → 导航；否则仅标记已读
                                            val eType = item.entityType
                                            val eId = item.entityId
                                            if (eType != null && !eId.isNullOrBlank()) {
                                                when (eType) {
                                                    "person" -> {
                                                        viewModel.markAsRead(item.uuid)
                                                        viewModel.navigateToPerson(eId) { localId ->
                                                            onNavigateToContact(localId)
                                                        }
                                                    }
                                                    // 未来可扩展 "tag" / "collection"
                                                    else -> viewModel.markAsRead(item.uuid)
                                                }
                                            } else {
                                                viewModel.markAsRead(item.uuid)
                                            }
                                        },
                                        onDelete = { viewModel.delete(item.uuid) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== [C4] 筛选 Tab ====================

/**
 * 全部 / 未读 切换 Tab。
 *
 * 使用 [TabRowWithContour]（与 OperationHistoryPage 同组件），未读标签后追加数字角标。
 */
@Composable
private fun NotificationFilterTab(
    currentFilter: NotificationFilter,
    unreadCount: Int,
    onFilterChange: (NotificationFilter) -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    val primary = MiuixTheme.colorScheme.primary
    val tabs = NotificationFilter.entries.map { f ->
        when (f) {
            NotificationFilter.ALL -> "全部"
            NotificationFilter.UNREAD -> if (unreadCount > 0) "未读($unreadCount)" else "未读"
        }
    }
    val index = NotificationFilter.entries.indexOf(currentFilter).coerceAtLeast(0)
    TabRowWithContour(
        tabs = tabs,
        selectedTabIndex = index,
        onTabSelected = { idx -> onFilterChange(NotificationFilter.entries[idx]) },
        colors = TabRowDefaults.tabRowColors(
            backgroundColor = cs.surface,
            contentColor = cs.onSurfaceVariantSummary,
            selectedBackgroundColor = cs.surface,
            selectedContentColor = primary,
        ),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationSwipeRow(
    item: UserNotification,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    // [修复防御]: confirm 返回 false —— 等仓库删行后再离开 composition；
    // API 失败时行仍在列表，避免视觉上滑走但数据还在。
    key(item.uuid) {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value == SwipeToDismissBoxValue.EndToStart) {
                    onDelete()
                }
                false
            },
        )
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = false,
            backgroundContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 2.dp)
                        .background(
                            color = MiuixTheme.colorScheme.error,
                            shape = top.mcxiafeng.badger.utils.miuixShape(12.dp),
                        )
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text(
                        text = "删除",
                        color = MiuixTheme.colorScheme.onError,
                        style = MiuixTheme.textStyles.body2,
                        fontWeight = FontWeight.Medium,
                    )
                }
            },
        ) {
            NotificationRow(item = item, onClick = onClick)
        }
    }
}

@Composable
private fun NotificationRow(
    item: UserNotification,
    onClick: () -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    val isNavigable = item.entityType != null && !item.entityId.isNullOrBlank()
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (!item.read) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp, end = 10.dp)
                        .size(8.dp)
                        .background(cs.primary, CircleShape),
                )
            } else {
                Spacer(Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.title.ifBlank { "(无标题)" },
                        style = MiuixTheme.textStyles.body1,
                        color = cs.onSurface,
                        fontWeight = if (item.read) FontWeight.Normal else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    val time = formatNotificationTime(item.createTime)
                    if (time.isNotBlank()) {
                        Text(
                            text = time,
                            style = MiuixTheme.textStyles.footnote2,
                            color = cs.onSurfaceVariantSummary,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                if (item.body.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.body,
                        style = MiuixTheme.textStyles.footnote1,
                        color = cs.onSurfaceVariantSummary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.senderName.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.senderName,
                        style = MiuixTheme.textStyles.footnote2,
                        color = cs.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // [C4] 可跳转通知显示箭头指示
            if (isNavigable) {
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = "查看详情",
                    tint = cs.onSurfaceVariantSummary,
                    modifier = Modifier
                        .padding(start = 8.dp, top = 4.dp)
                        .size(16.dp),
                )
            }
        }
    }
}

/** ISO 字符串或 epoch millis → `yyyy-MM-dd HH:mm`；解析失败原样（截断）。 */
internal fun formatNotificationTime(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    raw.toLongOrNull()?.let { epoch ->
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epoch))
        }.getOrDefault(raw)
    }
    return runCatching {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val trimmed = raw.substringBefore('.').substringBefore('+').substringBefore('Z')
        val date: Date = parser.parse(trimmed) ?: return raw
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(date)
    }.getOrDefault(raw.take(16))
}
