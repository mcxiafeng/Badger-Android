package top.mcxiafeng.badger.pages.settings

import top.mcxiafeng.badger.platform.UrlOpener
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.mcxiafeng.badger.pages.settings.components.SettingsGroupCard
import top.mcxiafeng.badger.pages.settings.components.SettingsGroupHeader
import top.mcxiafeng.badger.pages.settings.components.SettingsListScaffold
import top.mcxiafeng.badger.ui.navigation.SettingsPage
import top.mcxiafeng.badger.utils.BadgerLog
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAG = "OpenSourceLicensePage"

/** 开源库条目。 */
private data class LicenseEntry(
    val name: String,
    val summary: String,
    val license: String,
    val url: String,
)

// 分组（数据驱动，替代原先 8 张手写 Card × 重复 ArrowPreference）。
private val UI_LIBS = listOf(
    LicenseEntry("Miuix", "Xiaomi HyperOS 设计风格组件库", "Apache 2.0", "https://compose-miuix-ui.github.io/miuix/zh_CN/"),
    LicenseEntry("Jetpack Compose", "声明式 UI 框架（KMP）", "Apache 2.0", "https://developer.android.com/compose"),
    LicenseEntry("Lucide", "图标体系（替代 material-icons-extended）", "ISC", "https://lucide.dev"),
)
private val QR_LIBS = listOf(
    LicenseEntry("ZXing", "二维码生成", "Apache 2.0", "https://github.com/zxing/zxing"),
    LicenseEntry("qrcode-kotlin", "iOS 端二维码生成", "MIT", "https://github.com/g0dkar/qrcode-kotlin"),
    LicenseEntry("WeChatQRCode", "二维码识别", "Apache 2.0", "https://github.com/jenly1314/WeChatQRCode"),
)
private val ML_LIBS = listOf(
    LicenseEntry("ML Kit", "文字识别", "Apache 2.0", "https://developers.google.cn/ml-kit"),
)
private val NETWORK_LIBS = listOf(
    LicenseEntry("OkHttp", "Android 网络请求", "Apache 2.0", "https://github.com/square/okhttp"),
    LicenseEntry("Ktor", "iOS 网络请求（Darwin）", "Apache 2.0", "https://github.com/ktorio/ktor"),
    LicenseEntry("kotlinx.serialization", "JSON 解析", "Apache 2.0", "https://github.com/Kotlin/kotlinx.serialization"),
)
private val STORAGE_LIBS = listOf(
    LicenseEntry("Room", "本地数据库（KMP）", "Apache 2.0", "https://developer.android.com/training/data-storage/room"),
    LicenseEntry("Security Crypto", "加密存储", "Apache 2.0", "https://developer.android.com/reference/androidx/security/crypto/package-summary"),
)
private val CAMERA_LIBS = listOf(
    LicenseEntry("CameraX", "相机预览与拍照", "Apache 2.0", "https://developer.android.com/training/camerax"),
)
private val DI_LIBS = listOf(
    LicenseEntry("Koin", "依赖注入框架（替代 Hilt）", "Apache 2.0", "https://github.com/InsertKoinIO/koin"),
    LicenseEntry("Kotlin Coroutines", "异步编程", "Apache 2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
)
private val IMAGE_LIBS = listOf(
    LicenseEntry("Coil", "图片加载（KMP）", "Apache 2.0", "https://github.com/coil-kt/coil"),
    LicenseEntry("ExifInterface", "图片 EXIF 信息读取", "Apache 2.0", "https://developer.android.com/reference/androidx/exifinterface/media/ExifInterface"),
    LicenseEntry("Palette", "图片颜色提取", "Apache 2.0", "https://developer.android.com/develop/ui/views/graphics/palette-colors"),
)
private val LICENSE_GROUPS: List<Pair<String, List<LicenseEntry>>> = listOf(
    "界面" to UI_LIBS,
    "二维码" to QR_LIBS,
    "机器学习" to ML_LIBS,
    "网络" to NETWORK_LIBS,
    "存储" to STORAGE_LIBS,
    "相机" to CAMERA_LIBS,
    "依赖注入与异步" to DI_LIBS,
    "图片" to IMAGE_LIBS,
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
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * 开源许可页（重写：数据驱动 + 共享脚手架 + 修正过时条目）。
 *
 * 修正：Hilt → Koin（§14.2 已迁移）、Material Icons Extended → Lucide（K13 选型）；
 * 补 Ktor/qrcode-kotlin/Coil 等 KMP 迁移后实际在用的库。
 */
@Composable
internal fun OpenSourceLicensePage(onBack: () -> Unit) {
    LaunchedEffect(Unit) { BadgerLog.d(TAG, "OpenSourceLicensePage loaded") }

    SettingsListScaffold(title = SettingsPage.OpenSourceLicense.title, onBack = onBack) {
        LICENSE_GROUPS.forEachIndexed { idx, (groupTitle, entries) ->
            item(key = "group_$idx") {
                SettingsGroupHeader(text = groupTitle)
                SettingsGroupCard(
                    rows = entries.map { e ->
                        {
                            ArrowPreference(
                                title = e.name,
                                summary = e.summary,
                                endActions = { LicenseBadge(e.license) },
                                onClick = {
                                    BadgerLog.d(TAG, "Open ${e.name} URL")
                                    UrlOpener.openUrl(e.url)
                                },
                            )
                        }
                    },
                )
            }
        }
    }
}
