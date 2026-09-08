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
import top.mcxiafeng.badger.pages.settings.components.NotLoggedInState
import top.mcxiafeng.badger.pages.settings.components.SettingsSubPageScaffold
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.navigation.SettingsPage
import top.mcxiafeng.badger.utils.BadgerLog
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAG = "DashboardPage"

/**
 * 统计概览页（重写：共享脚手架 + 未登录空态 + onNavigateToContact 接通）。
 *
 * - 三张 stat cards（联系人 / 标签 / 名片夹）
 * - 最近添加联系人横向滚动列表（点击 → ContactDetail）
 * - 下拉刷新
 */
@Composable
internal fun DashboardPage(
    onBack: () -> Unit,
    onNavigateToLogin: () -> Unit = {},
    onNavigateToContact: (Long) -> Unit = {},
) {
    val viewModel: DashboardViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) {
            BadgerLog.d(TAG, "DashboardPage: first load")
            viewModel.refresh()
        }
    }

    SettingsSubPageScaffold(
        title = SettingsPage.Dashboard.title,
        onBack = onBack,
    ) { innerPadding ->
        Box(
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
                        title = "还没有统计数据",
                        subtitle = "登录账号后同步显示",
                    )
                }
            } else {
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
                            start = BadgerSpacing.md,
                            end = BadgerSpacing.md,
                            top = BadgerSpacing.sm,
                            bottom = BadgerSpacing.sm,
                        ),
                        verticalArrangement = Arrangement.spacedBy(BadgerSpacing.md),
                    ) {
                        item(key = "stats") {
                            StatCardsRow(
                                contactCount = uiState.contactCount,
                                tagCount = uiState.tagCount,
                                collectionCount = uiState.collectionCount,
                            )
                        }

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
                                    horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.md),
                                ) {
                                    items(
                                        uiState.recentContacts,
                                        // key 必须唯一：本地来源 id>0；API 来源本地无匹配时 id=0 会撞车，
                                        // 用服务端 uuid 兜底（Key "0" 崩溃根因）
                                        key = { it.serverUuid ?: "local-${it.id}" },
                                    ) { item ->
                                        RecentContactCard(
                                            item = item,
                                            onClick = {
                                                if (item.id > 0) {
                                                    BadgerLog.d(TAG, "Navigate to contact ${item.id}")
                                                    onNavigateToContact(item.id)
                                                }
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

@Composable
private fun StatCardsRow(
    contactCount: Int,
    tagCount: Int,
    collectionCount: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(BadgerSpacing.sm)) {
        StatCard(
            label = "联系人",
            count = contactCount,
            featured = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.sm),
        ) {
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
}

@Composable
private fun StatCard(
    label: String,
    count: Int,
    modifier: Modifier = Modifier,
    featured: Boolean = false,
) {
    Card(modifier = modifier, cornerRadius = BadgerRadius.card) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (featured) BadgerSpacing.xl else BadgerSpacing.md,
                    vertical = if (featured) BadgerSpacing.xl else BadgerSpacing.lg,
                ),
            horizontalAlignment = if (featured) Alignment.Start else Alignment.CenterHorizontally,
        ) {
            Text(
                text = count.toString(),
                style = if (featured) MiuixTheme.textStyles.title1 else MiuixTheme.textStyles.headline1,
                color = MiuixTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(BadgerSpacing.xs))
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
