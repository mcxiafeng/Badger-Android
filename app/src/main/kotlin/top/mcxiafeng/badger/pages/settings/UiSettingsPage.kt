package top.mcxiafeng.badger.pages.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.components.Card
import top.mcxiafeng.badger.ui.components.SwitchPreference
import top.mcxiafeng.badger.ui.glass.IconButton
import top.mcxiafeng.badger.ui.glass.Scaffold
import top.mcxiafeng.badger.ui.glass.TopAppBar
import top.mcxiafeng.badger.ui.glass.mergePagePadding
import top.mcxiafeng.badger.ui.glass.scrollTopFade
import top.mcxiafeng.badger.ui.glass.springOverscroll
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState

@Composable
fun UiSettingsPage(
    onBack: () -> Unit,
    viewModel: UiSettingsViewModel = hiltViewModel(),
) {
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current
    val floatingEnabled by viewModel.floatingEnabled.collectAsStateWithLifecycle()
    val glassMaterialEnabled by viewModel.glassMaterialEnabled.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = "UI 设置",
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
            modifier = Modifier.scrollTopFade().springOverscroll(),
            contentPadding = mergePagePadding(innerPadding,
                PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp + floatingBarBottomPadding)),
        ) {
            item(key = "nav_bar_card") {
                UiSettingsPreferences(
                    floatingEnabled = floatingEnabled,
                    glassMaterialEnabled = glassMaterialEnabled,
                    onFloatingChange = viewModel::setFloatingEnabled,
                    onGlassMaterialChange = viewModel::setGlassMaterialEnabled,
                )
            }
        }
    }
}

@Composable
private fun UiSettingsPreferences(
    floatingEnabled: Boolean,
    glassMaterialEnabled: Boolean,
    onFloatingChange: (Boolean) -> Unit,
    onGlassMaterialChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.padding(vertical = 6.dp),
        insideMargin = PaddingValues(0.dp),
    ) {
        SwitchPreference(
            title = "悬浮导航栏",
            summary = "一体式胶囊底栏",
            checked = floatingEnabled,
            onCheckedChange = onFloatingChange,
        )
        SwitchPreference(
            title = "玻璃材质",
            summary = "按钮和导航栏的玻璃效果",
            checked = glassMaterialEnabled,
            onCheckedChange = onGlassMaterialChange,
        )
    }
}
