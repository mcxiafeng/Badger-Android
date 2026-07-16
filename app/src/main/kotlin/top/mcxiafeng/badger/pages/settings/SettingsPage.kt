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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storage
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
 * 头部展示账号头像 + 用户名 + 当前服务器;下面是三个短卡快速入口;再下面是分组菜单项。
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

            // ========== 三个短卡 ==========
            item(key = "short_cards") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ShortCard(
                        icon = Icons.Default.Forum,
                        title = "联系平台",
                        subtitle = "管理已添加的平台",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            Log.d(TAG, "Navigate to PlatformList")
                            onNavigateToSubPage(SettingsPageRoute.PlatformList)
                        },
                    )
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
                        icon = Icons.Default.Storage,
                        title = "服务器信息",
                        subtitle = if (homeState.serverUrl.isBlank()) "未连接" else "已配置",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            Log.d(TAG, "Navigate to ServerInfo → AccountAndBackup")
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

            // ========== 第二组:标签管理 ==========
            item(key = "group_tag") {
                Card(insideMargin = PaddingValues(0.dp)) {
                    ArrowPreference(
                        title = "标签管理",
                        summary = "管理全局标签库 / 色点显示",
                        onClick = { onNavigateToSubPage(SettingsPageRoute.TagManager) },
                    )
                }
            }

            // ========== 第三组:关于 ==========
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
 * 头部下方的三个短卡:圆形图标 + 标题 + 一行说明,点击整张卡都触发 onClick。
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