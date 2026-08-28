package top.mcxiafeng.badger.pages.settings

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import top.mcxiafeng.badger.data.AuthPrefs
import top.mcxiafeng.badger.data.CloudSyncConfig
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.components.ContactAvatar
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

private const val TAG = "SettingsPage"

/**
 * 设置主页（重写版）。
 *
 * 结构（自上而下）：
 *   1. 顶部大卡片（账号/未登录）— Card 内嵌 ArrowPreference，左头像 + 右上名字 + 右下账户名 + 箭头。
 *      已登录 → 跳 [SettingsPageRoute.AccountProfile]
 *      未登录 → 跳登录页（[onNavigateToLogin]）
 *   2. 合并设置卡：标签管理 + 服务器设置 + NFC + 界面与导航 + 关于 Badger，
 *      全部顺序排在同一张 Card 内（按需求统一容器、视觉一致）。
 *
 * 服务器地址/修改服务器地址迁到独立一级页 [ServerSettingsPage]。
 * 旧版 [SettingsPageRoute.AccountAndBackup] 已彻底删除；登录/登出/修改昵称等个人信息
 * 迁到独立的 [AccountProfilePage]。
 */
@Composable
fun SettingsPage(
    onNavigateToSubPage: (SettingsPageRoute) -> Unit = {},
    onNavigateToMyProfile: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    devMode: Boolean = false,
    onDevModeChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    val homeViewModel: SettingsHomeViewModel = koinViewModel()
    val homeState by homeViewModel.state.collectAsState()

    // [修复防御]: 把旧版本 CloudSyncConfig.server_url 的值一次性迁到 AuthPrefs,
    // 避免「用户登录后在客户端改过备份服务器、但 AuthPrefs 还是默认 10.0.2.2」
    // 的悄默丢配置场景。完成后立刻清掉旧字段,下次启动只看到 AuthPrefs。
    LaunchedEffect(Unit) {
        val legacy = CloudSyncConfig.readLegacyServerUrl(context)
        if (legacy.isNotBlank()) {
            val currentAuth = AuthPrefs.readServerUrl(context)
            val isDefault = currentAuth.isBlank() ||
                currentAuth == "http://10.0.2.2:8080"
            if (isDefault) {
                Log.d(TAG, "Migrate legacy cloud-sync server url → AuthPrefs: $legacy")
                AuthPrefs.writeServerUrl(context, legacy.trim().trimEnd('/'))
            }
            CloudSyncConfig.clearLegacyServerUrl(context)
        }
    }

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
                                    imageVector = Icons.Outlined.Notifications,
                                    contentDescription = "通知",
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
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
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp + floatingBarBottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                                modifier = Modifier.padding(end = 12.dp),
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

            // ========== 合并设置卡 ==========
            // 顺序:通知 → 同步状态 → 标签管理 → 历史操作 → 服务器设置 → NFC → UI → 关于
            item(key = "settings_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    ArrowPreference(
                        title = "通知",
                        summary = if (homeState.unreadCount > 0) {
                            "${homeState.unreadCount} 条未读"
                        } else {
                            "站内消息 / 已读 / 删除"
                        },
                        startAction = {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        },
                        endActions = {
                            unreadBadge?.let { label ->
                                Badge { Text(text = label) }
                            }
                        },
                        onClick = {
                            Log.d(TAG, "Navigate to Notifications")
                            onNavigateToSubPage(SettingsPageRoute.Notifications)
                        },
                    )
                    // [V2-P9] 同步状态:抗 OEM 兜底入口。
                    // summary 显示 SyncStatus 摘要(有项需要关注 / N 个待同步 / 同步正常)。
                    ArrowPreference(
                        title = "同步状态",
                        summary = homeState.pendingHint,
                        startAction = {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(end = 12.dp),
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
                                imageVector = Icons.AutoMirrored.Filled.Label,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        },
                        onClick = { onNavigateToSubPage(SettingsPageRoute.TagManager) },
                    )
                    ArrowPreference(
                        title = "历史操作",
                        summary = "查看 / 重发 / 撤销操作",
                        startAction = {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        },
                        onClick = {
                            Log.d(TAG, "Navigate to OperationHistory")
                            onNavigateToSubPage(SettingsPageRoute.OperationHistory)
                        },
                    )
                    ArrowPreference(
                        title = "服务器设置",
                        summary = "服务器地址 / 修改服务器地址",
                        startAction = {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        },
                        onClick = { onNavigateToSubPage(SettingsPageRoute.ServerSettings) },
                    )
                    // [§16] 云端备份独立页入口：list / upload / download / delete
                    ArrowPreference(
                        title = "云端备份",
                        summary = "查看 / 下载 / 删除服务端备份",
                        startAction = {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        },
                        onClick = {
                            Log.d(TAG, "Navigate to CloudBackup")
                            onNavigateToSubPage(SettingsPageRoute.CloudBackup)
                        },
                    )
                    ArrowPreference(
                        title = "NFC 高级配置",
                        summary = "短链接服务 / 自定义 endpoint / API Key",
                        startAction = {
                            Icon(
                                imageVector = Icons.Default.Nfc,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(end = 12.dp),
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
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        },
                        onClick = {
                            Log.d(TAG, "Navigate to UiSettings")
                            onNavigateToSubPage(SettingsPageRoute.UiSettings)
                        },
                    )
                    ArrowPreference(
                        title = "关于 Badger",
                        startAction = {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        },
                        onClick = { onNavigateToSubPage(SettingsPageRoute.About) },
                    )
                }
            }
        }
    }
}
