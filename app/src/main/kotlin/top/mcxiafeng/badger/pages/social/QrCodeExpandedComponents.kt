package top.mcxiafeng.badger.pages.social

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerShapes
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.designsystem.continuousClip
import top.mcxiafeng.badger.ui.glass.LocalGlassBackdrop
import top.mcxiafeng.badger.ui.glass.LocalGlassStyle
import top.mcxiafeng.badger.ui.glass.PopupEnterEasing
import top.mcxiafeng.badger.ui.glass.PopupExitEasing
import top.mcxiafeng.badger.ui.glass.PopupMotionState
import top.mcxiafeng.badger.ui.glass.glassSurface
import top.mcxiafeng.badger.ui.glass.menuShadow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs

private const val QR_MINIMUM_CONTRAST = 4.5
// ZXing's block thresholding also needs a large 8-bit luminance gap for solid modules.
private const val QR_MINIMUM_LUMINANCE_GAP = 192
private const val QR_GREEN_LUMINANCE_WEIGHT = 2
private const val QR_LUMINANCE_DIVISOR = 4
private const val QR_CONTRAST_STEPS = 10
private const val QR_AVATAR_SIZE = 72
private const val QR_INVERTED_DEGREES = 180f
private val QR_DARK_BACKGROUND = Color(0xFF242424)
private val QR_DIRECTION_FADE_SPEC = tween<Float>(BadgerMotion.DURATION_TOUCH_FEEDBACK, easing = FastOutSlowInEasing)

@Immutable
internal data class QrDisplayPalette(val foreground: Color, val background: Color)

/** Keep every cycled colour legible; the application theme, rather than system night mode, selects the background. */
internal fun qrDisplayPalette(dark: Boolean, candidate: Color): QrDisplayPalette {
    val background = if (dark) QR_DARK_BACKGROUND else Color.White
    val target = if (dark) Color.White else Color.Black
    val initial = if (dark && candidate == Color.Black) Color.White else candidate
    val foreground = (0..QR_CONTRAST_STEPS).firstNotNullOf { step ->
        val adjusted = ColorUtils.blendARGB(initial.toArgb(), target.toArgb(), step.toFloat() / QR_CONTRAST_STEPS)
        adjusted.takeIf { ColorUtils.calculateContrast(it, background.toArgb()) >= QR_MINIMUM_CONTRAST &&
            abs(qrScannerLuminance(it) - qrScannerLuminance(background.toArgb())) >= QR_MINIMUM_LUMINANCE_GAP }
    }
    Log.d(QR_DISPLAY_LOG_TAG, "二维码对比度已校验；appDark=$dark")
    return QrDisplayPalette(Color(foreground), background)
}

private fun qrScannerLuminance(argb: Int): Int =
    (AndroidColor.red(argb) + QR_GREEN_LUMINANCE_WEIGHT * AndroidColor.green(argb) + AndroidColor.blue(argb)) / QR_LUMINANCE_DIVISOR

@Composable
internal fun QrDisplayPanel(
    bitmap: Bitmap, inverted: Boolean, userName: String?, platformName: String?, platformValue: String?,
    avatarPath: String?, onDismiss: () -> Unit, onInvert: () -> Unit, onCycleColor: () -> Unit,
    motion: PopupMotionState? = null,
) {
    Box(Modifier.fillMaxSize().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
        Log.d(QR_DISPLAY_LOG_TAG, "点击空白关闭二维码")
        onDismiss()
    }) {
        AnimatedContent(targetState = inverted, modifier = Modifier.fillMaxSize(), transitionSpec = {
            // The full-screen gesture layer stays still; only each measured panel animates.
            (EnterTransition.None togetherWith ExitTransition.None).using(null)
        }, label = "QrInvertTransition") { direction ->
            Box(Modifier.fillMaxSize(), contentAlignment = if (direction) Alignment.TopCenter else Alignment.BottomCenter) {
                val panelTransition = Modifier.animateEnterExit(
                    enter = slideInVertically(tween(BadgerMotion.DURATION_TOUCH_FEEDBACK, easing = PopupEnterEasing)) {
                        if (direction) -it else it
                    } + fadeIn(QR_DIRECTION_FADE_SPEC),
                    exit = slideOutVertically(tween(BadgerMotion.DURATION_TOUCH_FEEDBACK, easing = PopupExitEasing)) {
                        if (direction) -it else it
                    } + fadeOut(QR_DIRECTION_FADE_SPEC),
                )
                QrDisplayPanelSurface(direction, motion, panelTransition) {
                    QrDisplayIdentity(userName, platformName, platformValue, avatarPath, onInvert)
                    Spacer(Modifier.height(BadgerSpacing.lg))
                    QrDisplayImage(bitmap, onCycleColor)
                    Spacer(Modifier.height(BadgerSpacing.sm))
                    Text("点击头像可切换方向 · 长按二维码换色", style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }
    }
}

@Composable
private fun QrDisplayPanelSurface(
    inverted: Boolean, motion: PopupMotionState?, modifier: Modifier, content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val edgeInset = with(density) {
        if (inverted) WindowInsets.statusBars.getTop(this).toDp() else WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val shape = remember(inverted) {
        val top = if (inverted) 0.dp else BadgerRadius.menu
        val bottom = if (inverted) BadgerRadius.menu else 0.dp
        BadgerShapes.corners(top, top, bottom, bottom)
    }
    DisposableEffect(inverted) {
        Log.d(QR_DISPLAY_LOG_TAG, "二维码面板已挂载；inverted=$inverted")
        onDispose { Log.d(QR_DISPLAY_LOG_TAG, "二维码面板已卸载；inverted=$inverted") }
    }
    Column(
        modifier.fillMaxWidth().testTag("qr_display_panel").qrPanelMotion(motion, inverted)
            .menuShadow(shape).continuousClip(shape)
            .qrPanelBackground(inverted, shape)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
            .padding(top = if (inverted) edgeInset else 0.dp, bottom = if (inverted) 0.dp else edgeInset)
            .padding(horizontal = BadgerSpacing.xl, vertical = BadgerSpacing.lg),
    ) {
        // Rotate only the contents: screen-space backdrop coordinates and the outer clip stay stable.
        Column(Modifier.fillMaxWidth().graphicsLayer { rotationZ = if (inverted) QR_INVERTED_DEGREES else 0f },
            horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

private fun Modifier.qrPanelMotion(motion: PopupMotionState?, inverted: Boolean): Modifier {
    if (motion == null) return this
    return graphicsLayer {
        val progress = motion.progress.value
        translationY = (if (inverted) -size.height else size.height) * (1f - progress)
        alpha = progress.coerceIn(0f, 1f)
    }
}

@Composable
private fun QrDisplayIdentity(
    userName: String?, platformName: String?, platformValue: String?, avatarPath: String?, onInvert: () -> Unit,
) {
    Box(Modifier.testTag("qr_display_avatar").clickable(onClickLabel = "切换二维码方向", onClick = onInvert)) {
        ContactAvatar(name = userName ?: "?", avatarPath = avatarPath, size = QR_AVATAR_SIZE)
    }
    if (!userName.isNullOrBlank()) {
        Spacer(Modifier.height(BadgerSpacing.md))
        Text(userName, Modifier.testTag("qr_display_name"), style = MiuixTheme.textStyles.title3,
            color = MiuixTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val account = platformValue?.takeUnless { it.isBlank() || it == userName }
    if (!platformName.isNullOrBlank() || account != null) {
        Spacer(Modifier.height(BadgerSpacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.sm)) {
            if (!platformName.isNullOrBlank()) {
                Text(platformName, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onPrimary,
                    modifier = Modifier.continuousClip(BadgerShapes.rounded(BadgerRadius.chip))
                        .background(MiuixTheme.colorScheme.primary)
                        .padding(horizontal = BadgerSpacing.sm, vertical = BadgerSpacing.xs))
            }
            if (account != null) {
                Text(account, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onBackgroundVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun QrDisplayImage(bitmap: Bitmap, onCycleColor: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = BadgerSpacing.lg), contentAlignment = Alignment.Center) {
        Image(bitmap.asImageBitmap(), contentDescription = "二维码",
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).testTag("qr_display_image")
                .combinedClickable(onClick = {}, onLongClick = onCycleColor))
    }
}

@Composable
private fun Modifier.qrPanelBackground(inverted: Boolean, shape: Shape): Modifier {
    val style = LocalGlassStyle.current
    val backdrop = LocalGlassBackdrop.current
    val surface = MiuixTheme.colorScheme.surface
    LaunchedEffect(style, backdrop, inverted) {
        Log.d(QR_DISPLAY_LOG_TAG, "二维码外壳；radiusDp=${BadgerRadius.menu.value}；glass=${style.enabled}；material=${style.materialEnabled}；backdrop=${backdrop != null}；squareScreenEdge=${if (inverted) "top" else "bottom"}；shadowDp=${BadgerSize.menuShadowElevation.value}；contentsRotateIndependently=true")
    }
    return if (style.enabled) glassSurface(BadgerRadius.menu, style.menuTint, surface, backdrop,
        bounds = { qrPanelBounds(size.width, size.height, inverted, BadgerRadius.menu.toPx()) })
        else background(surface, shape)
}

/** Physical edge stays square; rotating the contents does not swap the outer panel's corners. */
internal fun qrPanelBounds(width: Float, height: Float, inverted: Boolean, radiusPx: Float): RoundRect {
    val top = CornerRadius(if (inverted) 0f else radiusPx)
    val bottom = CornerRadius(if (inverted) radiusPx else 0f)
    return RoundRect(0f, 0f, width, height, top, top, bottom, bottom)
}
