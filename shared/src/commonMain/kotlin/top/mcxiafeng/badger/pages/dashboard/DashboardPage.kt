package top.mcxiafeng.badger.pages.dashboard

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.components.BadgerEmptyState
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.LayoutDashboard
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "DashboardPage"

/**
 * [C1] Dashboard 统计概览页。
 *
 * - 三张 stat cards（联系人 / 标签 / 名片夹）
 * - 最近添加联系人横向滚动列表
 * - 下拉刷新
 * - 未登录空态引导
 */
@Composable
internal fun DashboardPage(
    onBack: () -> Unit,
    onNavigateToLogin: () -> Unit = {},
    onNavigateToContact: (Long) -> Unit = {},
) {
    val viewModel: DashboardViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current

    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) {
            BadgerLog.d(TAG, "DashboardPage: first load")
            viewModel.refresh()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "统计概览",
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Lucide.ArrowLeft,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
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
                        BadgerEmptyState(
                            icon = Lucide.LayoutDashboard,
                            title = "还没有统计数据",
                            subtitle = "登录账号后同步显示。",
                            actionLabel = "去登录",
                            onAction = onNavigateToLogin,
                        )
                    }
                }
                else -> {
                    val pullState = rememberPullToRefreshState()
                    PullToRefresh(
                        isRefreshing = uiState.loading,
                        onRefresh = {
                            BadgerLog.d(TAG, "DashboardPage: pull-to-refresh")
                            viewModel.refresh()
                        },
                        pullToRefreshState = pullState,
                        contentPadding = PaddingValues(top = 8.dp),
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 12.dp,
                                end = 12.dp,
                                top = 8.dp,
                                bottom = 8.dp + floatingBarBottomPadding,
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // ===== Stat Cards =====
                            item(key = "stats") {
                                StatCardsRow(
                                    contactCount = uiState.contactCount,
                                    tagCount = uiState.tagCount,
                                    collectionCount = uiState.collectionCount,
                                )
                            }

                            // ===== 最近添加 =====
                            if (uiState.recentContacts.isNotEmpty()) {
                                item(key = "recent_header") {
                                    Text(
                                        text = "最近添加",
                                        style = MiuixTheme.textStyles.headline2,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                item(key = "recent_list") {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        items(uiState.recentContacts, key = { it.id }) { item ->
                                            RecentContactCard(
                                                item = item,
                                                onClick = {
                                                    if (item.id > 0) onNavigateToContact(item.id)
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
}

@Composable
private fun StatCardsRow(
    contactCount: Int,
    tagCount: Int,
    collectionCount: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatCard(
            label = "联系人",
            count = contactCount,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = "标签",
            count = tagCount,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = "名片夹",
            count = collectionCount,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = count.toString(),
                style = MiuixTheme.textStyles.headline1,
                color = MiuixTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun RecentContactCard(
    item: DashboardRecentItem,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.width(100.dp),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ContactAvatar(
                name = item.name,
                avatarUrl = item.avatarUrl,
                avatarPath = item.avatarPath,
                size = 48,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = item.name.ifBlank { "—" },
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
