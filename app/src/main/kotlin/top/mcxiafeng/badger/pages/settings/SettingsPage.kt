package top.mcxiafeng.badger.pages.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.BuildConfig
import top.mcxiafeng.badger.R
import top.mcxiafeng.badger.ui.navigation.NavBarConfig
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.navigation.SettingsPage as SettingsPageRoute
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsPage(onNavigateToSubPage: (SettingsPageRoute) -> Unit = {}, devMode: Boolean = false, onDevModeChange: (Boolean) -> Unit = {}, onNavigateToAuth: () -> Unit = {}, onNavigateToServerAddresses: () -> Unit = {}) {
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = { TopAppBar(title = "设置", scrollBehavior = topAppBarScrollBehavior) },
    ) { innerPadding ->
        val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp + floatingBarBottomPadding),
        ) {
            // ========== APP 图标 + 版本号 ==========
            item(key = "app_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(R.mipmap.ic_launcher),
                        contentDescription = "Badger",
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Badger",
                        style = MiuixTheme.textStyles.subtitle
                    )
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onBackgroundVariant
                    )
                }
            }

            // ========== 全部设置在一个 Card 内 ==========
            item(key = "all_settings") {
                Card(insideMargin = PaddingValues(0.dp)) {
                    // --- 账号 ---
                    // 「登录」入口内含服务器分流：未配置过服务器会先进入服务器页，无需独立入口
                    ArrowPreference(
                        title = "登录",
                        summary = "登录 Badger 账号以同步云端数据",
                        onClick = { onNavigateToAuth() }
                    )
                    ArrowPreference(
                        title = "连接地址",
                        summary = "管理同一服务器的多个入口，支持拖动排序",
                        onClick = { onNavigateToServerAddresses() }
                    )

                    // --- 名片设置 ---
                    ArrowPreference(
                        title = "NFC设置",
                        summary = "配置 NFC 碰一碰",
                        onClick = { onNavigateToSubPage(SettingsPageRoute.NfcSettings) }
                    )
                    // [修复防御]: 入口移出 devMode，普通用户也能看到 AI 配置
                    ArrowPreference(
                        title = "AI 配置",
                        summary = "拍照识别名片 + 联系人标签推荐",
                        onClick = { onNavigateToSubPage(SettingsPageRoute.AiOcr) }
                    )

                    // --- 外观 ---
                    ArrowPreference(
                        title = "UI 设置",
                        summary = "导航栏样式与效果",
                        onClick = { onNavigateToSubPage(SettingsPageRoute.UiSettings) }
                    )

                    // --- 联系人 ---
                    ArrowPreference(
                        title = "标签管理",
                        summary = "管理全局标签库 / 色点显示",
                        onClick = { onNavigateToSubPage(SettingsPageRoute.TagManager) }
                    )

                    // --- 关于 ---
                    ArrowPreference(
                        title = "关于 Badger",
                        onClick = { onNavigateToSubPage(SettingsPageRoute.About) }
                    )
                }
            }
        }
    }
}