package top.mcxiafeng.badger.pages.settings

import android.util.Log
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.navigation.NavBarConfig
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.preference.SwitchPreference

private const val TAG = "Tester"

@Composable
fun UiSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current

    var floatingEnabled by remember { mutableStateOf(NavBarConfig.isFloatingEnabled(context)) }

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
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp + floatingBarBottomPadding),
        ) {
            item(key = "nav_bar_card") {
                Card(insideMargin = PaddingValues(0.dp)) {
                    SmallTitle(text = "导航栏", insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp))
                    SwitchPreference(
                        title = "悬浮导航栏",
                        summary = "胶囊式底部导航栏",
                        checked = floatingEnabled,
                        onCheckedChange = { newValue ->
                            Log.d(TAG, "Floating nav bar: $newValue")
                            floatingEnabled = newValue
                            NavBarConfig.saveFloatingEnabled(context, newValue)
                        },
                    )
                }
            }
        }
    }
}
