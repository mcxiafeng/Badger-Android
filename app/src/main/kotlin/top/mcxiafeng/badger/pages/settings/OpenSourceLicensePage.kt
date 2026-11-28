package top.mcxiafeng.badger.pages.settings


import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import top.mcxiafeng.badger.ui.designsystem.BadgerShapes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.yukonga.miuix.kmp.basic.IconButton
import top.mcxiafeng.badger.ui.glass.Scaffold
import top.mcxiafeng.badger.ui.glass.TopAppBar
import top.mcxiafeng.badger.ui.components.mergePagePadding
import top.mcxiafeng.badger.ui.glass.scrollTopFade
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAG = "OpenSourceLicensePage"

private data class OpenSourceLib(
    val name: String,
    val summary: String,
    val url: String,
    val license: String = "Apache 2.0",
)

private val OPEN_SOURCE_LIBS = listOf(
    OpenSourceLib("Miuix", "Xiaomi HyperOS 设计风格的组件库", "https://compose-miuix-ui.github.io/miuix/zh_CN/"),
    OpenSourceLib("Jetpack Compose", "Android 声明式 UI 框架", "https://developer.android.com/compose"),
    OpenSourceLib("Material Icons Extended", "Material Design 扩展图标库", "https://developer.android.com/reference/kotlin/androidx/compose/material/icons/package-summary"),
    OpenSourceLib("ZXing", "二维码生成", "https://github.com/zxing/zxing"),
    OpenSourceLib("WeChatQRCode", "二维码识别", "https://github.com/jenly1314/WeChatQRCode"),
    OpenSourceLib("ML Kit", "文字识别", "https://developers.google.cn/ml-kit"),
    OpenSourceLib("OkHttp", "网络请求", "https://github.com/square/okhttp"),
    OpenSourceLib("Gson", "JSON 解析", "https://github.com/google/gson"),
    OpenSourceLib("Room", "本地数据库", "https://developer.android.com/training/data-storage/room"),
    OpenSourceLib("Security Crypto", "加密存储", "https://developer.android.com/reference/androidx/security/crypto/package-summary"),
    OpenSourceLib("CameraX", "相机预览与拍照", "https://developer.android.com/training/camerax"),
    OpenSourceLib("Hilt", "依赖注入框架", "https://dagger.dev/hilt/"),
    OpenSourceLib("Kotlin Coroutines", "异步编程", "https://github.com/Kotlin/kotlinx.coroutines"),
    OpenSourceLib("ExifInterface", "图片 EXIF 信息读取", "https://developer.android.com/reference/androidx/exifinterface/media/ExifInterface"),
    OpenSourceLib("Palette", "图片颜色提取", "https://developer.android.com/develop/ui/views/graphics/palette-colors"),
    OpenSourceLib("Backdrop", "背景模糊效果", "https://github.com/Kyant0/backdrop"),
    OpenSourceLib("Capsule", "高斯模糊渲染", "https://github.com/Kyant0/capsule"),
)

@Composable
private fun LicenseBadge(license: String) {
    Text(
        text = license,
        style = MiuixTheme.textStyles.body2.copy(fontSize = 11.sp),
        color = MiuixTheme.colorScheme.onPrimary,
        modifier = Modifier
            .background(
                color = MiuixTheme.colorScheme.primary,
                shape = BadgerShapes.Surface
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun OpenSourceLibRow(lib: OpenSourceLib, onOpenUrl: (String) -> Unit) {
    ArrowPreference(
        title = lib.name,
        summary = lib.summary,
        endActions = { LicenseBadge(lib.license) },
        onClick = { onOpenUrl(lib.url) }
    )
}

@Composable
internal fun OpenSourceLicensePage(onBack: () -> Unit) {
    val context = LocalContext.current
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current

    LaunchedEffect(Unit) {
        Log.d(TAG, "OpenSourceLicensePage loaded, ${OPEN_SOURCE_LIBS.size} libs in single card")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "开源许可",
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.scrollTopFade(),
            contentPadding = mergePagePadding(innerPadding,
                PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp + floatingBarBottomPadding)),
        ) {
            item(key = "libs_all") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    OPEN_SOURCE_LIBS.forEach { lib ->
                        OpenSourceLibRow(lib) { url ->
                            Log.d(TAG, "Open license link: $url")
                            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                        }
                    }
                }
            }
        }
    }
}
