package top.mcxiafeng.badger.pages.settings

import android.util.Log
import top.mcxiafeng.badger.platform.UrlOpener
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
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
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft

private const val TAG = "OpenSourceLicensePage"

@Composable
private fun LicenseBadge(license: String) {
    Text(
        text = license,
        style = MiuixTheme.textStyles.body2.copy(fontSize = 11.sp),
        color = MiuixTheme.colorScheme.onPrimary,
        modifier = Modifier
            .background(
                color = MiuixTheme.colorScheme.primary,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
internal fun OpenSourceLicensePage(onBack: () -> Unit) {
    val context = LocalContext.current
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current

    LaunchedEffect(Unit) {
        Log.d(TAG, "OpenSourceLicensePage loaded")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "开源许可",
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Lucide.ArrowLeft, contentDescription = "返回")
                    }
                }
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp + floatingBarBottomPadding),
        ) {
            item(key = "libs_ui") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "Miuix",
                        summary = "Xiaomi HyperOS 设计风格的组件库",
                        endActions = { LicenseBadge("Apache 2.0") },
                        onClick = {
                            UrlOpener.openUrl("https://compose-miuix-ui.github.io/miuix/zh_CN/")
                        }
                    )
                    ArrowPreference(
                        title = "Jetpack Compose",
                        summary = "Android 声明式 UI 框架",
                        endActions = { LicenseBadge("Apache 2.0") },
                        onClick = {
                            UrlOpener.openUrl("https://developer.android.com/compose")
                        }
                    )
                    ArrowPreference(
                        title = "Material Icons Extended",
                        summary = "Material Design 扩展图标库",
                        endActions = { LicenseBadge("Apache 2.0") },
                        onClick = {
                            UrlOpener.openUrl("https://developer.android.com/reference/kotlin/androidx/compose/material/icons/package-summary")
                        }
                    )
                }
            }

            item(key = "libs_qr") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "ZXing",
                        summary = "二维码生成",
                        endActions = { LicenseBadge("Apache 2.0") },
                        onClick = {
                            UrlOpener.openUrl("https://github.com/zxing/zxing")
                        }
                    )
                    ArrowPreference(
                        title = "WeChatQRCode",
                        summary = "二维码识别",
                        endActions = { LicenseBadge("Apache 2.0") },
                        onClick = {
                            UrlOpener.openUrl("https://github.com/jenly1314/WeChatQRCode")
                        }
                    )
                }
            }

            item(key = "libs_ml") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "ML Kit",
                        summary = "文字识别",
                        endActions = { LicenseBadge("Apache 2.0") },
                        onClick = {
                            UrlOpener.openUrl("https://developers.google.cn/ml-kit")
                        }
                    )
                }
            }

            item(key = "libs_network") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "OkHttp",
                        summary = "网络请求",
                        endActions = { LicenseBadge("Apache 2.0") },
                        onClick = {
                            UrlOpener.openUrl("https://github.com/square/okhttp")
                        }
                    )
                    ArrowPreference(
                        title = "kotlinx.serialization",
                        summary = "JSON 解析",
                        endActions = { LicenseBadge("Apache 2.0") },
                        onClick = {
                            UrlOpener.openUrl("https://github.com/Kotlin/kotlinx.serialization")
                        }
                    )
                }
            }

            item(key = "libs_storage") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "Room",
                        summary = "本地数据库",
                        endActions = { LicenseBadge("Apache 2.0") },
                        onClick = {
                            UrlOpener.openUrl("https://developer.android.com/training/data-storage/room")
                        }
                    )
                    ArrowPreference(
                        title = "Security Crypto",
                        summary = "加密存储",
                        endActions = { LicenseBadge("Apache 2.0") },
                        onClick = {
                            UrlOpener.openUrl("https://developer.android.com/reference/androidx/security/crypto/package-summary")
                        }
                    )
                }
            }

            item(key = "libs_camera") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "CameraX",
                        summary = "相机预览与拍照",
                        endActions = { LicenseBadge("Apache 2.0") },
                        onClick = {
                            UrlOpener.openUrl("https://developer.android.com/training/camerax")
                        }
                    )
                }
            }

            item(key = "libs_di") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "Hilt",
                        summary = "依赖注入框架",
                        endActions = { LicenseBadge("Apache 2.0") },
                        onClick = {
                            UrlOpener.openUrl("https://dagger.dev/hilt/")
                        }
                    )
                    ArrowPreference(
                        title = "Kotlin Coroutines",
                        summary = "异步编程",
                        endActions = { LicenseBadge("Apache 2.0") },
                        onClick = {
                            UrlOpener.openUrl("https://github.com/Kotlin/kotlinx.coroutines")
                        }
                    )
                }
            }

            item(key = "libs_image") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "ExifInterface",
                        summary = "图片 EXIF 信息读取",
                        endActions = { LicenseBadge("Apache 2.0") },
                        onClick = {
                            UrlOpener.openUrl("https://developer.android.com/reference/androidx/exifinterface/media/ExifInterface")
                        }
                    )
                    ArrowPreference(
                        title = "Palette",
                        summary = "图片颜色提取",
                        endActions = { LicenseBadge("Apache 2.0") },
                        onClick = {
                            UrlOpener.openUrl("https://developer.android.com/develop/ui/views/graphics/palette-colors")
                        }
                    )
                    ArrowPreference(
                        title = "Backdrop",
                        summary = "背景模糊效果",
                        endActions = { LicenseBadge("Apache 2.0") },
                        onClick = {
                            UrlOpener.openUrl("https://github.com/Kyant0/backdrop")
                        }
                    )
                    ArrowPreference(
                        title = "Capsule",
                        summary = "高斯模糊渲染",
                        endActions = { LicenseBadge("Apache 2.0") },
                        onClick = {
                            UrlOpener.openUrl("https://github.com/Kyant0/capsule")
                        }
                    )
                }
            }
        }
    }
}
