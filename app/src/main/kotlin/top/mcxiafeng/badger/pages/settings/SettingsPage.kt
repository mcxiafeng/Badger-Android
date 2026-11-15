package top.mcxiafeng.badger.pages.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
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

@Composable
fun SettingsPage(onNavigateToSubPage: (SettingsPageRoute) -> Unit = {}, devMode: Boolean = false, onDevModeChange: (Boolean) -> Unit = {}, onNavigateToAuth: () -> Unit = {}, onNavigateToServerAddresses: () -> Unit = {}) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val accountState by viewModel.accountState.collectAsStateWithLifecycle()
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = { TopAppBar(title = "设置", scrollBehavior = topAppBarScrollBehavior) },
    ) { innerPadding ->
        val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp + floatingBarBottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ========== 账号区：头像 + 昵称；未登录时点击进入登录（内含服务器分流） ==========
            item(key = "account_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !accountState.loggedIn) { onNavigateToAuth() }
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AccountAvatar(
                        loggedIn = accountState.loggedIn,
                        avatarInitial = accountState.avatarInitial,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = accountState.displayName,
                            style = MiuixTheme.textStyles.title3
                        )
                        Text(
                            text = accountState.subtitle,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onBackgroundVariant
                        )
                    }
                }
            }

            // ========== 账号：服务器入口管理 ==========
            item(key = "server_card") {
                Card(insideMargin = PaddingValues(0.dp)) {
                    ArrowPreference(
                        title = "连接地址",
                        summary = "管理同一服务器的多个地址，支持拖动排序",
                        onClick = { onNavigateToServerAddresses() }
                    )
                }
            }

            // ========== 名片：NFC 碰一碰 + AI 识别 ==========
            item(key = "card_tools_card") {
                Card(insideMargin = PaddingValues(0.dp)) {
                    ArrowPreference(
                        title = "NFC 设置",
                        summary = "配置 NFC 碰一碰",
                        onClick = { onNavigateToSubPage(SettingsPageRoute.NfcSettings) }
                    )
                    // [修复防御]: 入口移出 devMode，普通用户也能看到 AI 配置
                    ArrowPreference(
                        title = "AI 配置",
                        summary = "拍照识别名片 + 联系人标签推荐",
                        onClick = { onNavigateToSubPage(SettingsPageRoute.AiOcr) }
                    )
                }
            }

            // ========== 通用：外观 + 联系人标签 ==========
            item(key = "general_card") {
                Card(insideMargin = PaddingValues(0.dp)) {
                    ArrowPreference(
                        title = "UI 设置",
                        summary = "导航栏样式与效果",
                        onClick = { onNavigateToSubPage(SettingsPageRoute.UiSettings) }
                    )
                    ArrowPreference(
                        title = "标签管理",
                        summary = "管理全局标签库 / 色点显示",
                        onClick = { onNavigateToSubPage(SettingsPageRoute.TagManager) }
                    )
                }
            }

            // ========== 关于 ==========
            item(key = "about_card") {
                Card(insideMargin = PaddingValues(0.dp)) {
                    ArrowPreference(
                        title = "关于 Badger",
                        summary = "版本信息与开源许可",
                        onClick = { onNavigateToSubPage(SettingsPageRoute.About) }
                    )
                }
            }
        }
    }
}

/** 账号头像：未登录（或缺用户名的旧会话）→ 默认人形；已登录 → 用户名首字母。 */
@Composable
private fun AccountAvatar(loggedIn: Boolean, avatarInitial: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (loggedIn && avatarInitial != null) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MiuixTheme.colorScheme.surfaceContainer
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (loggedIn && avatarInitial != null) {
            Text(
                text = avatarInitial,
                style = MiuixTheme.textStyles.title2,
                color = MiuixTheme.colorScheme.primary
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = "默认头像",
                modifier = Modifier.size(32.dp),
                tint = MiuixTheme.colorScheme.onBackgroundVariant
            )
        }
    }
}
