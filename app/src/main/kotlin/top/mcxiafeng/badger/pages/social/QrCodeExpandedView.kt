package top.mcxiafeng.badger.pages.social

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import top.mcxiafeng.badger.ui.glass.LocalAppDarkTheme
import top.mcxiafeng.badger.ui.glass.LocalGlassBackdrop
import top.mcxiafeng.badger.ui.glass.LocalGlassStyle
import top.mcxiafeng.badger.ui.glass.LocalMenuBackdrop
import top.mcxiafeng.badger.ui.glass.LocalPageBackdrop
import top.mcxiafeng.badger.ui.glass.PopupDim
import top.mcxiafeng.badger.ui.glass.PopupMotionState
import top.mcxiafeng.badger.ui.glass.PopupWindow
import top.mcxiafeng.badger.ui.glass.RetainedPopup
import top.mcxiafeng.badger.utils.Methods
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal const val QR_DISPLAY_LOG_TAG = "QrCodeExpandedViewTester"
private const val QR_BITMAP_SIZE = 512
private const val QR_QUIET_ZONE_MODULES = 4

/** One popup keeps its caller's theme and glass source when content moves to the root host. */
@Composable
internal fun QrCodeExpandedView(
    content: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    userName: String? = null,
    platformName: String? = null,
    platformValue: String? = null,
    avatarPath: String? = null,
) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("social_prefs", 0) }
    var inverted by remember { mutableStateOf(preferences.getBoolean("qr_inverted", true)) }
    var colorIndex by remember { mutableIntStateOf(0) }
    val dark = LocalAppDarkTheme.current
    val palette = remember(dark, colorIndex) { qrDisplayPalette(dark, Methods.qrColors[colorIndex]) }
    val bitmap = remember(content, palette) {
        Log.d(QR_DISPLAY_LOG_TAG, "生成展示二维码；appDark=$dark；colorIndex=$colorIndex；quietZone=$QR_QUIET_ZONE_MODULES")
        Methods.generateQRCode(content, QR_BITMAP_SIZE, palette.foreground.toArgb(), palette.background.toArgb(), QR_QUIET_ZONE_MODULES)
    }
    BackHandler(enabled = visible) {
        Log.d(QR_DISPLAY_LOG_TAG, "返回关闭二维码")
        onDismiss()
    }
    QrPopupHost(visible, onDismiss) { motion ->
        QrDisplayPanel(bitmap, inverted, userName, platformName, platformValue, avatarPath,
            onDismiss = onDismiss,
            onInvert = {
                inverted = !inverted
                Log.d(QR_DISPLAY_LOG_TAG, "切换二维码方向；inverted=$inverted")
                preferences.edit().putBoolean("qr_inverted", inverted).apply()
            },
            onCycleColor = {
                colorIndex = (colorIndex + 1) % Methods.qrColors.size
                Log.d(QR_DISPLAY_LOG_TAG, "切换二维码颜色；index=$colorIndex")
            }, motion = motion)
    }
}

@Composable
private fun QrPopupHost(visible: Boolean, onDismiss: () -> Unit, content: @Composable (PopupMotionState) -> Unit) {
    val style = LocalGlassStyle.current
    val colors = MiuixTheme.colorScheme
    val backdrop = LocalMenuBackdrop.current ?: LocalGlassBackdrop.current ?: LocalPageBackdrop.current?.value
    RetainedPopup(show = visible) { motion ->
        PopupWindow(motion, onDismissRequest = onDismiss) {
            MiuixTheme(colors = colors) {
                CompositionLocalProvider(LocalGlassStyle provides style, LocalGlassBackdrop provides backdrop) {
                    PopupDim(motion, enabled = !style.enabled)
                    content(motion)
                }
            }
        }
    }
}
