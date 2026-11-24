package top.mcxiafeng.badger.pages.settings

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.navigation.SettingsPage as SettingsPageRoute
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.preference.ArrowPreference

private const val TAG = "SettingsPageTester"

@Composable
fun SettingsPage(
    onBack: () -> Unit,
    onNavigateToSubPage: (SettingsPageRoute) -> Unit,
    onNavigateToServerAddresses: () -> Unit,
    configuredServerUrl: String? = null,
) {
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    Scaffold(topBar = {
        TopAppBar(title = "设置", scrollBehavior = scrollBehavior, navigationIcon = {
            IconButton(onClick = {
                Log.d(TAG, "返回设置页来源")
                onBack()
            }, modifier = Modifier.size(BadgerSize.minimumTouchTarget)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        })
    }) { innerPadding ->
        SettingsList(
            onNavigateToSubPage = onNavigateToSubPage,
            onNavigateToServerAddresses = onNavigateToServerAddresses,
            configuredServerUrl = configuredServerUrl,
            modifier = Modifier.padding(innerPadding).nestedScroll(scrollBehavior.nestedScrollConnection),
        )
    }
}

@Composable
private fun SettingsList(
    onNavigateToSubPage: (SettingsPageRoute) -> Unit,
    onNavigateToServerAddresses: () -> Unit,
    configuredServerUrl: String?,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = BadgerSpacing.md, vertical = BadgerSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(BadgerSpacing.cardGap),
    ) {
        item(key = "server_card") {
            Card(insideMargin = PaddingValues()) {
                ArrowPreference(
                    title = "连接地址",
                    summary = configuredServerUrl ?: "尚未配置服务器",
                    onClick = {
                        Log.d(TAG, "打开连接地址")
                        onNavigateToServerAddresses()
                    },
                )
            }
        }
        item(key = "card_tools_card") {
            Card(insideMargin = PaddingValues()) {
                SettingsEntry("NFC 设置", "配置 NFC 碰一碰", SettingsPageRoute.NfcSettings, onNavigateToSubPage)
                SettingsEntry("AI 配置", "拍照识别名片 + 联系人标签推荐", SettingsPageRoute.AiOcr, onNavigateToSubPage)
                SettingsEntry("高德地图配置", "可选的联网行政区划查询", SettingsPageRoute.Gaode, onNavigateToSubPage)
            }
        }
        item(key = "general_card") {
            Card(insideMargin = PaddingValues()) {
                SettingsEntry("UI 设置", "导航栏样式与效果", SettingsPageRoute.UiSettings, onNavigateToSubPage)
            }
        }
    }
}

@Composable
private fun SettingsEntry(
    title: String,
    summary: String,
    page: SettingsPageRoute,
    onNavigate: (SettingsPageRoute) -> Unit,
) {
    ArrowPreference(title = title, summary = summary, onClick = {
        Log.d(TAG, "打开设置子页面：$page")
        onNavigate(page)
    })
}
