package top.mcxiafeng.badger.pages.settings

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.mcxiafeng.badger.ui.navigation.SettingsPage as SettingsPageRoute
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAG = "SettingsPage"

/**
 * 设置主页。
 *
 * 设计原则（重做后的版本）：
 *   1. 顶部 = 头像 + 用户名 + 当前服务器；底部 3 个长卡覆盖"个人信息 / 联系平台 / 服务器"
 *      这 3 个最高频入口,点击直达对应页面。
 *   2. 下半部分是分组列表，按"账号与备份 / 通用 / 标签 / 关于"分组；
 *      通用组聚合了 NFC 设置 / UI 视觉配置两类非账号类入口;
 *      标签、关于、账号与备份各自独立。
 *   3. 不展示任何"常开"性质的开关(允许不安全 HTTP 等),这些已经下沉到默认值,不应再
 *      让用户接触以免误关。
 */
@Composable
fun SettingsPage(
    onNavigateToSubPage: (SettingsPageRoute) -> Unit = {},
    onNavigateToMyProfile: () -> Unit = {},
    devMode: Boolean = false,
    onDevModeChange: (Boolean) -> Unit = {},
) {
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    val homeViewModel: SettingsHomeViewModel = hiltViewModel()
    val homeState by homeViewModel.state.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = "设置", scrollBehavior = topAppBarScrollBehavior) },
    ) { innerPadding ->
        val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp + floatingBarBottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ========== 头部:账号 + 服务器 ==========
            item(key = "account_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ContactAvatar(
                        name = homeState.username ?: "",
                        size = 64,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (homeState.isLoggedIn) (homeState.username ?: "—") else "未登录",
                        style = MiuixTheme.textStyles.subtitle,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = homeState.serverUrl,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // ========== 三个长卡:高频直达 ==========
            item(key = "short_cards") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ShortCard(
                        icon = Icons.Default.Person,
                        title = "个人信息",
                        subtitle = "我的名片",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            Log.d(TAG, "Navigate to MyProfile (ContactDetail(-1))")
                            onNavigateToMyProfile()
                        },
                    )
                    ShortCard(
                        icon = Icons.Default.Forum,
                        title = "联系平台",
                        subtitle = "管理已添加",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            Log.d(TAG, "Navigate to PlatformList")
                            onNavigateToSubPage(SettingsPageRoute.PlatformList)
                        },
                    )
                    ShortCard(
                        icon = Icons.Default.Storage,
                        title = "服务器",
                        subtitle = if (homeState.serverUrl.isBlank()) "未连接" else "已配置",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            Log.d(TAG, "Navigate to AccountAndBackup (server inline)")
                            onNavigateToSubPage(SettingsPageRoute.AccountAndBackup)
                        },
                    )
                }
            }

            // ========== 第一组:账号与备份 ==========
            item(key = "group_account") {
                Card(insideMargin = PaddingValues(0.dp)) {
                    ArrowPreference(
                        title = "账号与备份",
                        summary = if (homeState.isLoggedIn)
                            "已登录:${homeState.username ?: "—"}"
                        else
                            "未登录",
                        onClick = {
                            Log.d(TAG, "Navigate to AccountAndBackup")
                            onNavigateToSubPage(SettingsPageRoute.AccountAndBackup)
                        },
                    )
                }
            }

            // ========== 第二组:通用(NFC、UI视觉) ==========
            item(key = "group_general") {
                Card(insideMargin = PaddingValues(0.dp)) {
                    ArrowPreference(
                        title = "NFC 高级配置",
                        summary = "短链接服务 / 自定义 endpoint / API Key",
                        startAction = {
                            Icon(
                                imageVector = Icons.Default.Tune,
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
                }
            }

            // ========== 第三组:标签管理 ==========
            item(key = "group_tag") {
                Card(insideMargin = PaddingValues(0.dp)) {
                    ArrowPreference(
                        title = "标签管理",
                        summary = "管理全局标签库 / 色点显示",
                        onClick = { onNavigateToSubPage(SettingsPageRoute.TagManager) },
                    )
                }
            }

            // ========== 第四组:关于 ==========
            item(key = "group_about") {
                Card(insideMargin = PaddingValues(0.dp)) {
                    ArrowPreference(
                        title = "关于 Badger",
                        onClick = { onNavigateToSubPage(SettingsPageRoute.About) },
                    )
                }
            }
        }
    }
}

/**
 * 高频直达卡：圆形图标 + 标题 + 一行说明,点击整张卡触发 onClick。
 */
@Composable
private fun ShortCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        insideMargin = PaddingValues(12.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MiuixTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
