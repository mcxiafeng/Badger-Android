package top.mcxiafeng.badger.pages.settings

import android.os.Build
import android.util.Log
import android.content.Intent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.navigation.SettingsPage as SettingsPageRoute
import top.mcxiafeng.badger.BuildConfig
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.R
import top.mcxiafeng.badger.ui.components.ContactAvatar
import android.net.Uri
import top.mcxiafeng.badger.utils.Methods
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
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
import top.mcxiafeng.badger.data.isDeveloperMode
import top.mcxiafeng.badger.data.setDeveloperMode
import top.yukonga.miuix.kmp.preference.SwitchPreference
import androidx.core.net.toUri

private const val TAG = "Tester"

@Composable
internal fun AboutPage(onBack: () -> Unit, onNavigateToSubPage: (SettingsPageRoute) -> Unit, devMode: Boolean = false, onDevModeChange: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current

    // 开发者模式：连续点击版本号 7 次
    var devTapCount by remember { mutableIntStateOf(0) }
    var lastDevTapTime by remember { mutableLongStateOf(0L) }

    // 2 秒无操作 → 计数归零，summary 恢复版本号
    LaunchedEffect(devTapCount) {
        if (devTapCount == 0) return@LaunchedEffect
        snapshotFlow { devTapCount }.collect { count ->
            if (count > 0) {
                delay(2000)
                devTapCount = 0
            }
        }
    }

    LaunchedEffect(Unit) {
        Log.d(TAG, "AboutPage loaded, version=${BuildConfig.VERSION_NAME}")
    }

    Scaffold(
        topBar = { TopAppBar(title = "关于", scrollBehavior = topAppBarScrollBehavior, navigationIcon = { IconButton(onClick = onBack) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } }) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp + floatingBarBottomPadding),
        ) {

            item(key = "about_header") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(painter = painterResource(R.mipmap.ic_launcher), contentDescription = "Badger", modifier = Modifier.size(48.dp).clip(CircleShape))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Badger", style = MiuixTheme.textStyles.subtitle)
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            }

            item(key = "actor") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "夏枫大笨喵w",
                        summary = "请输入文本",
                        startAction = {
                            ContactAvatar(
                                avatarUrl = "https://avatars.githubusercontent.com/u/50166277?v=4",
                                size = 36,
                                transparentBackground = true,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        },
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                "https://github.com/mcxiafeng".toUri()))
                        }
                    )
                }
            }

            item(key = "about_details") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "懒猫的盒子",
                        summary = "有机会帮你测试一下",
                        startAction = {
                            ContactAvatar(
                                avatarUrl = "https://avatars.githubusercontent.com/u/287770688?v=4",
                                size = 36,
                                transparentBackground = true,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        },
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                "https://github.com/YuLan888".toUri()))
                        }
                    )
                }
            }

            item(key = "app_info") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    BasicComponent(
                        title = "版本号",
                        summary = when {
                            devMode -> "开发者模式已打开"
                            devTapCount >= 4 -> "已点击 $devTapCount/7"
                            else -> BuildConfig.VERSION_NAME
                        },
                        summaryColor = if (devMode || devTapCount >= 4) {
                            BasicComponentDefaults.summaryColor(color = MiuixTheme.colorScheme.primary)
                        } else {
                            BasicComponentDefaults.summaryColor()
                        },
                        onClick = {
                            Log.d(TAG, "版本号被点击")
                            if (devMode) {
                                Toast.makeText(context, "开发者模式已打开", Toast.LENGTH_SHORT).show()
                                return@BasicComponent
                            }
                            val now = System.currentTimeMillis()
                            if (now - lastDevTapTime > 2000) {
                                devTapCount = 0
                            }
                            lastDevTapTime = now
                            devTapCount++
                            Log.d(TAG, "开发者模式点击: $devTapCount/7")
                            if (devTapCount >= 7) {
                                devTapCount = 0
                                setDeveloperMode(context, true)
                                onDevModeChange(true)
                                Log.d(TAG, "开发者模式已开启")
                                Toast.makeText(context, "开发者模式已开启", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    BasicComponent(
                        title = "构建日期",
                        summary = BuildConfig.BUILD_DATE,
                    )
                    BasicComponent(
                        title = "数据库版本",
                        summary = "${BuildConfig.VERSION_CODE}",
                    )
                    if (devMode) {
                        SwitchPreference(
                            title = "开发者模式",
                            summary = "关闭后隐藏开发者专属功能",
                            checked = true,
                            onCheckedChange = { newValue ->
                                setDeveloperMode(context, newValue)
                                onDevModeChange(newValue)
                                Log.d(TAG, "开发者模式开关: -> $newValue")
                            }
                        )
                    }
                    if (devMode) {
                        ArrowPreference(
                            title = "软件日志",
                            summary = "查看应用日志",
                            onClick = {
                                onNavigateToSubPage(SettingsPageRoute.AppLog)
                            }
                        )
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(title = "本项目仓库", summary = "点击打开本项目仓库", onClick = {
                        Log.d(TAG, "Open Project URL: https://github.com/mcxiafeng/Badger-Android")
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/mcxiafeng/Badger-Android".toUri()))
                        }.onFailure { Log.w(TAG, "Open Project URL Failed", it) }
                    })
                    ArrowPreference(title = "开源许可", summary = "查看使用的开源库", onClick = {
                        onNavigateToSubPage(SettingsPageRoute.OpenSourceLicense)
                    })
                    ArrowPreference(title = "联系我们", summary = "QQ 群 / Telegram / Matrix", onClick = {
                        Log.d(TAG, "Navigate to ContactUs")
                        onNavigateToSubPage(SettingsPageRoute.ContactUs)
                    })
                }
            }
        }
    }
}
