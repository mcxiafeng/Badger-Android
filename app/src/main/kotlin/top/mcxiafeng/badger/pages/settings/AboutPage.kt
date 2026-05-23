package top.mcxiafeng.badger.pages.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.BuildConfig
import top.mcxiafeng.badger.R
import top.mcxiafeng.badger.ui.components.ContactAvatar
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

private const val TAG = "AboutPage"

@Composable
internal fun AboutPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = { TopAppBar(title = "关于", scrollBehavior = topAppBarScrollBehavior, navigationIcon = { IconButton(onClick = onBack) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } }) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
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
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/mcxiafeng")))
                        }
                    )
                }
            }

            item(key = "about_details") {
                SmallTitle(text = "特别鸣谢", insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "Miuix",
                        summary = "提供 Xiaomi HyperOS 设计风格的组件库",
                        startAction = {
                            ContactAvatar(
                                avatarUrl = "https://compose-miuix-ui.github.io/miuix/Icon.webp",
                                size = 36,
                                transparentBackground = true,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        },
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://compose-miuix-ui.github.io/miuix/zh_CN/")))
                        }
                    )
                    ArrowPreference(
                        title = "ZXing",
                        summary = "二维码生成",
                        startAction = {
                            ContactAvatar(
                                avatarUrl = "https://camo.githubusercontent.com/5996b3e9878ee5cf613f471b9715bd8329aa9d17286be9c6304a6db40744678a/68747470733a2f2f7261772e6769746875622e636f6d2f77696b692f7a78696e672f7a78696e672f7a78696e672d6c6f676f2e706e67",
                                size = 36,
                                transparentBackground = true,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        },
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/zxing/zxing")))
                        }
                    )
                    ArrowPreference(
                        title = "WeChatQRCode",
                        summary = "二维码识别",
                        startAction = {
                            ContactAvatar(
                                avatarUrl = "https://avatars.githubusercontent.com/u/22694679?s=48&v=4",
                                size = 36,
                                transparentBackground = true,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        },
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/jenly1314/WeChatQRCode")))
                        }
                    )
                    ArrowPreference(
                    title = "ML Kit",
                    summary = "文字识别",
                    startAction = {
                        ContactAvatar(
                            avatarUrl = "https://developers.google.cn/static/ml-kit/images/homepage/hero_480.png?hl=zh-cn",
                            size = 36,
                            transparentBackground = true,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    },
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://developers.google.cn/ml-kit")))
                        }
                    )
                    ArrowPreference(
                    title = "OkHttp",
                    summary = "网络请求",
                    startAction = {
                        ContactAvatar(
                            avatarUrl = "https://avatars.githubusercontent.com/u/82592?s=48&v=4",
                            size = 36,
                            transparentBackground = true,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    },
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/square/okhttp")))
                    }
                    )
                    ArrowPreference(
                    title = "GLM-5.1",
                    summary = "低于5.0的我都不想用",
                    startAction = {
                        ContactAvatar(
                            avatarUrl = "https://www.zhipuai.cn/favicon.png",
                            size = 36,
                            transparentBackground = true,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    },
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.zhipuai.cn")))
                    }
                    )
                }
            }
            item {
                SmallTitle(text = "开源代码仓库", insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(title = "github.com/mcxiafeng/badger", summary = "点击复制仓库链接", onClick = {
                        Methods.copyToClipboard(context, "仓库链接", "https://github.com/mcxiafeng/badger")
                        Toast.makeText(context, "已复制仓库链接", Toast.LENGTH_SHORT).show()
                    })
                }
            }
        }
    }
}
