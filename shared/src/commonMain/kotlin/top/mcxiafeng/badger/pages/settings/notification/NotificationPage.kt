package top.mcxiafeng.badger.pages.settings.notification

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import top.mcxiafeng.badger.network.UserNotification
import top.mcxiafeng.badger.pages.settings.components.BadgerSwipeRow
import top.mcxiafeng.badger.pages.settings.components.NotLoggedInState
import top.mcxiafeng.badger.pages.settings.components.SETTINGS_SNACKBAR_DURATION_MS
import top.mcxiafeng.badger.pages.settings.components.SettingsSubPageScaffold
import top.mcxiafeng.badger.ui.components.BadgerEmptyState
import top.mcxiafeng.badger.ui.navigation.SettingsPage
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.utils.Methods
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide

private const val TAG = "NotificationPage"

/**
 * 站内通知列表（重写：共享脚手架 + BadgerSwipeRow + NotLoggedInState + snackbar 常量）。
 *
 * - 点击未读行 → 标记已读；可跳转通知导航到关联实体
 * - 左滑删除（[BadgerSwipeRow]，失败行回弹 + snackbar）
 * - 全部/未读筛选 Tab + 下拉刷新
 */
@Composable
internal fun NotificationPage(
    onBack: () -> Unit,
    onNavigateToLogin: () -> Unit = {},
    onNavigateToContact: (Long) -> Unit = {},
) {
    val viewModel: NotificationViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) {
            BadgerLog.d(TAG, "NotificationPage: first load")
            viewModel.refresh()
        }
    }

    LaunchedEffect(uiState.error) {
        val msg = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = msg,
            duration = SnackbarDuration.Custom(SETTINGS_SNACKBAR_DURATION_MS),
        )
        viewModel.clearError()
    }

    SettingsSubPageScaffold(
        title = SettingsPage.Notifications.title,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (!uiState.isLoggedIn) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    NotLoggedInState(
                        onLogin = onNavigateToLogin,
                        title = "还没有通知",
                        subtitle = "登录账号后同步显示站内通知",
                    )
                }
            } else {
                // 双向绑定 HorizontalPager（与 TagManager/OperationHistory 同款）
                val scope = rememberCoroutineScope()
                val pagerState = rememberPagerState(
                    initialPage = NotificationFilter.entries.indexOf(uiState.filter).coerceAtLeast(0),
                ) { NotificationFilter.entries.size }
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.currentPage }.collect { page ->
                        viewModel.setFilter(NotificationFilter.entries[page])
                    }
                }

                NotificationFilterTab(
                    selectedTabIndex = pagerState.currentPage,
                    unreadCount = uiState.unreadCount,
                    onTabSelected = { idx ->
                        viewModel.setFilter(NotificationFilter.entries[idx])
                        scope.launch { pagerState.animateScrollToPage(idx) }
                    },
                )
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) {
                    val pullState = rememberPullToRefreshState()
                    PullToRefresh(
                        isRefreshing = uiState.loading,
                        onRefresh = {
                            BadgerLog.d(TAG, "NotificationPage: pull-to-refresh")
                            viewModel.refresh()
                    },
                    pullToRefreshState = pullState,
                    contentPadding = PaddingValues(top = 8.dp),
                ) {
                    if (uiState.items.isEmpty() && !uiState.loading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            val emptyTitle = if (uiState.filter == NotificationFilter.UNREAD) {
                                "还没有未读通知"
                            } else {
                                "还没有通知"
                            }
                            BadgerEmptyState(
                                icon = Lucide.Bell,
                                title = emptyTitle,
                                subtitle = "有新消息时会显示在这里，也可下拉刷新",
                                actionLabel = "刷新",
                                onAction = { viewModel.refresh() },
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(uiState.items, key = { it.uuid }) { item ->
                                key(item.uuid) {
                                    BadgerSwipeRow(
                                        onDelete = { viewModel.delete(item.uuid) },
                                        deleteText = "删除",
                                        content = {
                                            NotificationRow(item = item, onClick = {
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
                                                        else -> viewModel.markAsRead(item.uuid)
                                                    }
                                                } else {
                                                    viewModel.markAsRead(item.uuid)
                                                }
                                            })
                                        },
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
}

// ==================== 筛选 Tab ====================

@Composable
private fun NotificationFilterTab(
    selectedTabIndex: Int,
    unreadCount: Int,
    onTabSelected: (Int) -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    val primary = MiuixTheme.colorScheme.primary
    val tabs = NotificationFilter.entries.map { f ->
        when (f) {
            NotificationFilter.ALL -> "全部"
            NotificationFilter.UNREAD -> if (unreadCount > 0) "未读($unreadCount)" else "未读"
        }
    }
    TabRowWithContour(
        tabs = tabs,
        selectedTabIndex = selectedTabIndex.coerceIn(0, NotificationFilter.entries.lastIndex),
        onTabSelected = onTabSelected,
        colors = TabRowDefaults.tabRowColors(
            backgroundColor = cs.surface,
            contentColor = cs.onSurfaceVariantSummary,
            selectedBackgroundColor = cs.surface,
            selectedContentColor = primary,
        ),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun NotificationRow(
    item: UserNotification,
    onClick: () -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    val isNavigable = item.entityType != null && !item.entityId.isNullOrBlank()
    val time = formatNotificationTime(item.createTime)
    val subtitle = buildList {
        if (item.body.isNotBlank()) add(item.body)
        if (item.senderName.isNotBlank()) add(item.senderName)
    }.joinToString(" · ").ifBlank { null }

    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = item.title.ifBlank { "(无标题)" },
            titleColor = if (!item.read) {
                BasicComponentDefaults.titleColor(color = cs.primary)
            } else {
                BasicComponentDefaults.titleColor()
            },
            summary = subtitle,
            startAction = {
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
            },
            endActions = {
                if (time.isNotBlank()) {
                    Text(
                        text = time,
                        style = MiuixTheme.textStyles.footnote2,
                        color = cs.onSurfaceVariantSummary,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                if (isNavigable) {
                    Icon(
                        imageVector = Lucide.ChevronRight,
                        contentDescription = "查看详情",
                        tint = cs.onSurfaceVariantSummary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            },
            onClick = onClick,
        )
    }
}

/** ISO 字符串或 epoch millis → `yyyy-MM-dd HH:mm`；解析失败原样（截断）。 */
fun formatNotificationTime(raw: String?): String =
    Methods.formatDateTime(raw, raw?.take(16) ?: "") ?: ""
