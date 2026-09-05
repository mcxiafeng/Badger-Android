package top.mcxiafeng.badger.pages.settings

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.formatUnreadBadge
import top.mcxiafeng.badger.ui.navigation.SettingsPage as SettingsPageRoute
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
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.LayoutDashboard
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.Nfc
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Tag

private const val TAG = "SettingsPage"

/**
 * 设置主页（重写版）。
 *
 * 结构（自上而下）：
 *   1. 账号大卡片（账号/未登录）— 已登录 → [SettingsPageRoute.AccountProfile]，未登录 → 登录页。
 *   2. 账户与数据卡：统计概览 / 同步状态 / 标签管理。
 *   3. 配置卡：NFC 配置 / 界面与导航。
 *   4. 关于卡：历史操作 / 关于 Badger。
 *
 * 通知入口位于 TopBar 右上角铃铛图标（含未读角标）。
 */
@Composable
fun SettingsPage(
    onNavigateToSubPage: (SettingsPageRoute) -> Unit = {},
    onNavigateToMyProfile: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    devMode: Boolean = false,
    onDevModeChange: (Boolean) -> Unit = {},
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
                    // [B2] 点击角标直达通知列表（NavigationBar 设置 Tab 上也有同源未读数）。
                    IconButton(onClick = {
                        Log.d(TAG, "Navigate to Notifications (top bar)")
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
        val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(start = BadgerSpacing.md, end = BadgerSpacing.md, top = BadgerSpacing.sm, bottom = BadgerSpacing.sm + floatingBarBottomPadding),
            verticalArrangement = Arrangement.spacedBy(BadgerSpacing.md),
        ) {
            // ========== 头部大卡片:账号 / 未登录 ==========
            item(key = "account_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    ArrowPreference(
                        title = if (homeState.isLoggedIn) (homeState.username ?: "—") else "未登录",
                        summary = if (homeState.isLoggedIn)
                            "账户名:${homeState.username ?: "—"}"
                        else
                            "点击登录",
                        startAction = {
                            ContactAvatar(
                                name = homeState.username ?: "",
                                size = 44,
                                modifier = Modifier.padding(end = BadgerSpacing.md),
                            )
                        },
                        onClick = {
                            if (homeState.isLoggedIn) {
                                Log.d(TAG, "Navigate to AccountProfile")
                                onNavigateToSubPage(SettingsPageRoute.AccountProfile)
                            } else {
                                Log.d(TAG, "Navigate to Login (from account card)")
                                onNavigateToLogin()
                            }
                        },
                    )
                }
            }

            // ========== 账户与数据卡 ==========
            item(key = "data_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    // [C1] Dashboard 统计概览入口
                    ArrowPreference(
                        title = "统计概览",
                        summary = "联系人 / 标签 / 名片夹统计",
                        startAction = {
                            Icon(
                                imageVector = Lucide.LayoutDashboard,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(end = BadgerSpacing.md),
                            )
                        },
                        onClick = {
                            Log.d(TAG, "Navigate to Dashboard")
                            onNavigateToSubPage(SettingsPageRoute.Dashboard)
                        },
                    )
                    // [V2-P9] 同步状态:抗 OEM 兜底入口。
                    ArrowPreference(
                        title = "同步状态",
                        summary = homeState.pendingHint,
                        startAction = {
                            Icon(
                                imageVector = Lucide.RefreshCw,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(end = BadgerSpacing.md),
                            )
                        },
                        onClick = {
                            Log.d(TAG, "Navigate to SyncStatus")
                            onNavigateToSubPage(SettingsPageRoute.SyncStatus)
                        },
                    )
                    ArrowPreference(
                        title = "标签管理",
                        summary = "管理全局标签库 / 色点显示",
                        startAction = {
                            Icon(
                                imageVector = Lucide.Tag,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(end = BadgerSpacing.md),
                            )
                        },
                        onClick = { onNavigateToSubPage(SettingsPageRoute.TagManager) },
                    )
                    // 自建短链管理入口
                    ArrowPreference(
                        title = "自建短链",
                        summary = "管理服务端自建短链接",
                        startAction = {
                            Icon(
                                imageVector = Lucide.Link,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(end = BadgerSpacing.md),
                            )
                        },
                        onClick = {
                            Log.d(TAG, "Navigate to ServerShortLinks")
                            onNavigateToSubPage(SettingsPageRoute.ServerShortLinks)
                        },
                    )
                }
            }

            // ========== 配置卡 ==========
            item(key = "config_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    ArrowPreference(
                        title = "NFC 高级配置",
                        summary = "短链接服务 / 自定义 endpoint / API Key",
                        startAction = {
                            Icon(
                                imageVector = Lucide.Nfc,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(end = BadgerSpacing.md),
                            )
                        },
                        onClick = {
                            Log.d(TAG, "Navigate to NfcSettings")
                            onNavigateToSubPage(SettingsPageRoute.NfcSettings)
                        },
                    )
                    ArrowPreference(
                        title = "界面与导航",
                        summary = "悬浮导航栏 / 模糊 / 液态玻璃",
                        startAction = {
                            Icon(
                                imageVector = Lucide.Palette,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(end = BadgerSpacing.md),
                            )
                        },
                        onClick = {
                            Log.d(TAG, "Navigate to UiSettings")
                            onNavigateToSubPage(SettingsPageRoute.UiSettings)
                        },
                    )
                }
            }

            // ========== 关于卡 ==========
            item(key = "about_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    ArrowPreference(
                        title = "历史操作",
                        summary = "查看历史操作记录",
                        startAction = {
                            Icon(
                                imageVector = Lucide.History,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(end = BadgerSpacing.md),
                            )
                        },
                        onClick = {
                            Log.d(TAG, "Navigate to OperationHistory")
                            onNavigateToSubPage(SettingsPageRoute.OperationHistory)
                        },
                    )
                    ArrowPreference(
                        title = "关于 Badger",
                        startAction = {
                            Icon(
                                imageVector = Lucide.Info,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(end = BadgerSpacing.md),
                            )
                        },
                        onClick = { onNavigateToSubPage(SettingsPageRoute.About) },
                    )
                }
            }
        }
    }
}
