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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.BuildConfig
import top.mcxiafeng.badger.R
import top.mcxiafeng.badger.ui.components.ContactAvatar
import android.net.Uri
import top.mcxiafeng.badger.utils.Methods
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.core.net.toUri

private const val TAG = "Tester"

@Composable
internal fun AboutPage(onBack: () -> Unit, onNavigateToSubPage: (String) -> Unit) {
    val context = LocalContext.current
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current

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
                    Text("v${BuildConfig.VERSION_NAME}", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onBackgroundVariant)
                }
            }

            item(key = "actor") {
                SmallTitle(text = "作者", insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp))
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
                SmallTitle(text = "特别鸣谢", insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp))
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
                SmallTitle(text = "软件信息", insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "版本号",
                        summary = BuildConfig.VERSION_NAME,
                        onClick = {}
                    )
                    ArrowPreference(
                        title = "构建日期",
                        summary = BuildConfig.BUILD_DATE,
                        onClick = {}
                    )
                    ArrowPreference(
                        title = "安卓版本",
                        summary = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                        onClick = {}
                    )
                    ArrowPreference(
                        title = "软件日志",
                        summary = "查看应用日志",
                        onClick = {
                            onNavigateToSubPage("app_log")
                        }
                    )
                }
            }
            item {
                SmallTitle(text = "开源代码仓库", insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(title = "本项目仓库", summary = "点击复制仓库链接", onClick = {
                        Methods.copyToClipboard(context, "仓库链接", "https://github.com/mcxiafeng/Badger-Android")
                        Toast.makeText(context, "已复制仓库链接", Toast.LENGTH_SHORT).show()
                    })
                    ArrowPreference(title = "开源许可", summary = "查看使用的开源库", onClick = {
                        onNavigateToSubPage("open_source_license")
                    })
                }
            }
        }
    }
}
