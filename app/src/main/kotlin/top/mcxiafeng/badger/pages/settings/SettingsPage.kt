package top.mcxiafeng.badger.pages.settings

import android.widget.Toast
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.BuildConfig
import top.mcxiafeng.badger.R
import top.mcxiafeng.badger.data.CloudSyncConfig
import top.mcxiafeng.badger.data.NetworkConfig
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
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsPage(onNavigateToSubPage: (SettingsPageRoute) -> Unit = {}, devMode: Boolean = false, onDevModeChange: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    var syncEnabled by remember { mutableStateOf(CloudSyncConfig.isSyncEnabled(context)) }
    var allowInsecureHttp by remember { mutableStateOf(NetworkConfig.isAllowInsecureHttp(context)) }

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

                    // --- 数据与备份 ---
                    SwitchPreference(
                        title = "云端备份",
                        summary = "自动备份和恢复数据",
                        checked = syncEnabled,
                        onCheckedChange = { newValue ->
                            if (newValue && !CloudSyncConfig.isConfigured(context)) {
                                Toast.makeText(context, "请先配置备份服务器地址和凭据", Toast.LENGTH_SHORT).show()
                            } else {
                                syncEnabled = newValue
                                CloudSyncConfig.saveSyncEnabled(context, newValue)
                            }
                        }
                    )
                    ArrowPreference(
                        title = "云端备份设置",
                        summary = if (CloudSyncConfig.isConfigured(context)) "已配置" else "未配置",
                        onClick = { onNavigateToSubPage(SettingsPageRoute.CloudSync) }
                    )

                    // --- 网络安全 ---
                    SwitchPreference(
                        title = "允许不安全HTTP连接",
                        summary = "需要重启应用",
                        checked = allowInsecureHttp,
                        onCheckedChange = { newValue ->
                            allowInsecureHttp = newValue
                            NetworkConfig.saveAllowInsecureHttp(context, newValue)
                            Toast.makeText(context, "请重启应用以使设置生效", Toast.LENGTH_LONG).show()
                        }
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