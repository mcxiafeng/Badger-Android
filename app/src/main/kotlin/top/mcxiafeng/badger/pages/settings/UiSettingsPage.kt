package top.mcxiafeng.badger.pages.settings

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
    val blurSupported = NavBarConfig.isBlurSupported()
    val systemBlurEnabled by NavBarConfig.systemBlurEnabledFlow.collectAsState(initial = NavBarConfig.isSystemBlurEnabled(context))

    var floatingEnabled by remember { mutableStateOf(NavBarConfig.isFloatingEnabled(context)) }
    var blurEnabled by remember { mutableStateOf(NavBarConfig.isBlurEnabled(context)) }
    var liquidGlassEnabled by remember { mutableStateOf(NavBarConfig.isLiquidGlassEnabled(context)) }

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
                    SwitchPreference(
                        title = "模糊效果",
                        summary = when {
                            !blurSupported -> "当前系统不支持（需 Android 13+）"
                            !systemBlurEnabled -> "系统已禁用模糊（省电模式或\"减少模糊效果\"设置）"
                            else -> "毛玻璃背景模糊"
                        },
                        checked = blurEnabled,
                        onCheckedChange = { newValue ->
                            if (blurSupported) {
                                Log.d(TAG, "Blur effect: $newValue")
                                blurEnabled = newValue
                                NavBarConfig.saveBlurEnabled(context, newValue)
                                if (newValue && !systemBlurEnabled) {
                                    Toast.makeText(context, "系统已禁用模糊，效果可能不生效", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "需要 Android 13 及以上", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                    SwitchPreference(
                        title = "液态玻璃",
                        summary = when {
                            !blurSupported -> "当前系统不支持（需 Android 13+）"
                            !systemBlurEnabled -> "系统已禁用模糊（省电模式或\"减少模糊效果\"设置）"
                            else -> "镜头折射+高光+模糊效果"
                        },
                        checked = liquidGlassEnabled,
                        onCheckedChange = { newValue ->
                            if (blurSupported) {
                                Log.d(TAG, "Liquid glass: $newValue")
                                liquidGlassEnabled = newValue
                                NavBarConfig.saveLiquidGlassEnabled(context, newValue)
                                if (newValue && !systemBlurEnabled) {
                                    Toast.makeText(context, "系统已禁用模糊，效果可能不生效", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "需要 Android 13 及以上", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }
        }
    }
}