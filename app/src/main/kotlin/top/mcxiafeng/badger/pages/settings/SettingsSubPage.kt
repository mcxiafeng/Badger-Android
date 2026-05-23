package top.mcxiafeng.badger.pages.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAG = "SettingsSubPage"

@Composable
fun SettingsSubPage(page: String, onBack: () -> Unit) {
    when (page) {
        "short_link" -> ShortLinkSettingsPage(onBack)
        "ai_ocr" -> AiOcrSettingsPage(onBack)
        "ui_settings" -> UiSettingsPage(onBack)
        "cloud_sync_settings" -> CloudSyncSettingsPage(onBack)
        "about" -> AboutPage(onBack)
        else -> {
            val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
            Scaffold(
                topBar = { TopAppBar(title = "设置", scrollBehavior = topAppBarScrollBehavior, navigationIcon = { IconButton(onClick = onBack) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } }) },
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("未知页面", color = MiuixTheme.colorScheme.onBackgroundVariant)
                }
            }
        }
    }
}
