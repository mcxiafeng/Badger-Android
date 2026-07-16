package top.mcxiafeng.badger.pages.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.navigation.SettingsPage
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

/**
 * 通用设置页。
 *
 * 按「易用性优先」原则，本页**不再暴露任何开关**：NFC / AI / 网络功能均默认开启。
 * 仅保留一个 NFC 高级配置入口给使用自定义短链接服务的进阶用户。
 *
 * 历史说明：旧版此页曾罗列 NFC 启停、自定义短链接、AI 名片识别、AI 标签推荐、
 * 允许不安全 HTTP 等 5 个开关。这些开关的运行时行为要么是「常开才有效」（关掉
 * 入口就不存在）、要么在 [top.mcxiafeng.badger.di.NetworkModule] 中根本没被读取，
 * 暴露给用户反而误导。现已全部下沉到服务端默认配置或代码默认值。
 */
@Composable
internal fun GeneralSettingsPage(
    onBack: () -> Unit,
    onNavigateToSubPage: (SettingsPage) -> Unit,
) {
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = "通用设置",
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 8.dp,
                bottom = 8.dp + floatingBarBottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "nfc_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    ArrowPreference(
                        title = "NFC 高级配置",
                        summary = "自定义短链接域名 / API Key / 自定义 endpoint",
                        onClick = { onNavigateToSubPage(SettingsPage.NfcSettings) },
                    )
                }
            }

            item(key = "help_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Text(
                        text = "NFC 写入、AI 名片识别、AI 标签推荐均为默认开启，由 Badger-Server 提供能力。「NFC 高级配置」仅在你需要更换短链接服务（如自建 Short.io）时使用。",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        lineHeight = 1.5.em,
                    )
                }
            }
        }
    }
}
