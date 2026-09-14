package top.mcxiafeng.badger.pages.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import top.mcxiafeng.badger.pages.settings.components.SettingsChipColors
import top.mcxiafeng.badger.pages.settings.components.SettingsGroupCard
import top.mcxiafeng.badger.pages.settings.components.SettingsGroupHeader
import top.mcxiafeng.badger.pages.settings.components.SettingsIconChip
import top.mcxiafeng.badger.pages.settings.components.homeSummary
import top.mcxiafeng.badger.pages.settings.components.settingsHomeGroups
import top.mcxiafeng.badger.ui.components.BadgerFloatingBarList
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.mcxiafeng.badger.ui.components.badgerListContentPadding
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.formatUnreadBadge
import top.mcxiafeng.badger.ui.navigation.SettingsPage
import top.mcxiafeng.badger.ui.navigation.SettingsPage as SettingsPageRoute
import top.mcxiafeng.badger.utils.BadgerLog
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.BadgedBox
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide

private const val TAG = "SettingsPage"

@Composable
fun SettingsPage(
    onNavigateToSubPage: (SettingsPageRoute) -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
) {
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    val homeViewModel: SettingsHomeViewModel = koinViewModel()
    val homeState by homeViewModel.state.collectAsState()

    val unreadBadge = formatUnreadBadge(homeState.unreadCount)

    Scaffold(
        topBar = {
            TopAppBar(
                title = "设置",
                scrollBehavior = topAppBarScrollBehavior,
                actions = {
                    IconButton(onClick = {
                        BadgerLog.d(TAG, "Navigate to Notifications (top bar)")
                        onNavigateToSubPage(SettingsPageRoute.Notifications)
                    }) {
                        if (unreadBadge != null) {
                            BadgedBox(badge = { Badge { Text(text = unreadBadge) } }) {
                                Icon(
                                    imageVector = Lucide.Bell,
                                    contentDescription = "通知",
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Lucide.Bell,
                                contentDescription = "通知",
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        BadgerFloatingBarList(
            modifier = Modifier.padding(innerPadding),
            contentPadding = badgerListContentPadding(
                topExtra = BadgerSpacing.sm,
                bottomExtra = BadgerSpacing.sm,
                start = BadgerSpacing.md,
                end = BadgerSpacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(BadgerSpacing.md),
        ) {
            
            item(key = "account_card") { AccountHeroCard(homeState, onNavigateToSubPage, onNavigateToLogin) }

            
            settingsHomeGroups.forEach { group ->
                item(key = "group_${group.title}") {
                    SettingsGroupHeader(text = group.title)
                    SettingsGroupCard(
                        rows = group.pages.map { page ->
                            { SettingsNavRow(page, onNavigateToSubPage) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountHeroCard(
    state: SettingsHomeState,
    onNavigateToSubPage: (SettingsPage) -> Unit,
    onNavigateToLogin: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = BadgerRadius.card,
        insideMargin = PaddingValues(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (state.isLoggedIn) {
                        BadgerLog.d(TAG, "Navigate to AccountProfile")
                        onNavigateToSubPage(SettingsPageRoute.AccountProfile)
                    } else {
                        BadgerLog.d(TAG, "Navigate to Login (from account card)")
                        onNavigateToLogin()
                    }
                }
                .padding(BadgerSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContactAvatar(
                name = state.username ?: "",
                avatarPath = state.profileAvatarPath,
                size = 64,
            )
            Spacer(modifier = Modifier.width(BadgerSpacing.lg))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (state.isLoggedIn) (state.username ?: "—") else "未登录",
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                Text(
                    text = if (state.isLoggedIn)
                        "管理账户与资料 · ${state.serverUrl.hostDisplay()}"
                    else
                        "登录后同步联系人与名片夹",
                    style = MiuixTheme.textStyles.footnote1,
                    color = if (state.isLoggedIn)
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    else
                        MiuixTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = BadgerSpacing.xxs),
                )
            }
            Icon(
                imageVector = Lucide.ChevronRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SettingsNavRow(
    page: SettingsPage,
    onNavigateToSubPage: (SettingsPage) -> Unit,
) {
    val summary = page.homeSummary
    ArrowPreference(
        title = page.title,
        summary = summary.ifEmpty { null },
        startAction = {
            SettingsIconChip(
                icon = page.icon,
                container = SettingsChipColors.colorFor(page),
                modifier = Modifier.padding(end = BadgerSpacing.md),
            )
        },
        onClick = {
            BadgerLog.d(TAG, "Navigate to ${page::class.simpleName}")
            onNavigateToSubPage(page)
        },
    )
}

private fun String.hostDisplay(): String =
    removePrefix("http://").removePrefix("https://").substringBefore('/')
