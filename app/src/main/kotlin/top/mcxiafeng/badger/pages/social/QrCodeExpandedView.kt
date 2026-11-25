package top.mcxiafeng.badger.pages.social

import android.util.Log
import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import top.mcxiafeng.badger.ui.designsystem.continuousClip as clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.mcxiafeng.badger.utils.Methods
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerShapes
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.DialogLayout
import top.mcxiafeng.badger.ui.glass.LocalGlassStyle
import top.mcxiafeng.badger.ui.glass.LocalGlassBackdrop
import top.mcxiafeng.badger.ui.glass.LocalMenuBackdrop
import top.mcxiafeng.badger.ui.glass.LocalPageBackdrop
import top.mcxiafeng.badger.ui.glass.glassSurface

private const val TAG = "Tester"

/**
 * 二维码展开视图（弹层）
 *
 * 由父组件控制 visible，从页面右下角二维码按钮直接打开。
 * 弹层内容：头像（点击切换正/倒显示）→ 名字 → 平台标签 → 二维码（长按换色）。
 *
 * @param content 二维码内容
 * @param visible 是否显示弹层
 * @param onDismiss 关闭回调（点击空白处/系统返回键）
 * @param userName 展示名字
 * @param platformName 平台名标签
 * @param platformValue 平台账号值
 * @param avatarPath 头像路径
 */
@Composable
internal fun QrCodeExpandedView(
    content: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    userName: String? = null,
    platformName: String? = null,
    platformValue: String? = null,
    avatarPath: String? = null
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    var colorIndex by remember { mutableIntStateOf(0) }
    val currentColor = Methods.qrColors[colorIndex]
    // 深色模式：浅色码点；亮色模式：深色码点
    val qrForegroundColor = if (isDark) {
        if (colorIndex == 0) MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.3f) else currentColor
    } else {
        currentColor
    }
    val qrBackgroundColor = remember(isDark) {
        if (isDark) {
            AndroidColor.argb(255, 36, 36, 36) // #FF242424 与 Miuix dark background 一致
        } else {
            AndroidColor.WHITE
        }
    }
    val androidFgColor = remember(qrForegroundColor) {
        AndroidColor.argb(
            255, (qrForegroundColor.red * 255).toInt(), (qrForegroundColor.green * 255).toInt(), (qrForegroundColor.blue * 255).toInt()
        )
    }
    val qrBitmap = remember(content, colorIndex, isDark) { Methods.generateQRCode(content, 512, androidFgColor, qrBackgroundColor) }
    val qrContainerColor = Color.Transparent

    BackHandler(enabled = visible) {
        Log.d(TAG, "QrCode dialog dismiss by back")
        onDismiss()
    }

    val dialogVisible = remember { mutableStateOf(false) }
    SideEffect { dialogVisible.value = visible }
    // 正/倒显示模式：true=倒序从顶部弹出（给对方看），false=正序从底部弹出（自己看）
    var isInverted by remember {
        mutableStateOf(
            context.getSharedPreferences("social_prefs", 0).getBoolean("qr_inverted", true)
        )
    }

    // 弹窗内容（头像→名字→平台→二维码→提示）
    @Composable
    fun QrDialogContent(inverted: Boolean) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { Log.d(TAG, "QrCode dialog close"); onDismiss() },
            contentAlignment = if (inverted) Alignment.TopCenter else Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { rotationZ = if (inverted) 180f else 0f }
                    .clickable { }
                    .qrPanelBackground(inverted)
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                if (!inverted) {
                    val statusBarBottom = WindowInsets.navigationBars.getBottom(LocalDensity.current)
                    Spacer(modifier = Modifier.height(with(LocalDensity.current) { statusBarBottom.toDp() }))
                }
                Spacer(modifier = Modifier.height(16.dp))
                // 头像（点击切换正/倒显示）
                Box(
                    modifier = Modifier.clickable {
                        isInverted = !inverted
                        Log.d(TAG, "QrCode invert toggle: $inverted -> $isInverted")
                        context.getSharedPreferences("social_prefs", 0).edit().putBoolean("qr_inverted", isInverted).apply()
                    }
                ) {
                    ContactAvatar(
                        name = userName ?: "?",
                        avatarPath = avatarPath,
                        size = 72
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                // 名字
                if (!userName.isNullOrBlank()) {
                    Text(
                        text = userName,
                        style = MiuixTheme.textStyles.title3,
                        color = MiuixTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // 平台标识：平台名 + 值（蓝色底白字标签）
                if (!platformName.isNullOrBlank() || !platformValue.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!platformName.isNullOrBlank()) {
                            Text(
                                text = platformName,
                                style = MiuixTheme.textStyles.footnote1,
                                color = Color.White,
                                modifier = Modifier
                                    .clip(BadgerShapes.Surface)
                                    .background(MiuixTheme.colorScheme.primary)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (!platformValue.isNullOrBlank()) {
                            Text(
                                text = platformValue,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                // 二维码（长按换色）
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth().aspectRatio(1f)
                            .combinedClickable(
                                onClick = { },
                                onLongClick = {
                                    Log.d(TAG, "QrCode color cycle: $colorIndex -> ${(colorIndex + 1) % Methods.qrColors.size}")
                                    colorIndex = (colorIndex + 1) % Methods.qrColors.size
                                }
                            )
                            .background(qrContainerColor, BadgerShapes.Surface).padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier.fillMaxSize())
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "点击头像可切换方向 · 长按二维码换色", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Spacer(modifier = Modifier.height(16.dp))
                if (inverted) {
                    val statusBarTop = WindowInsets.statusBars.getTop(LocalDensity.current)
                    Spacer(modifier = Modifier.height(with(LocalDensity.current) { statusBarTop.toDp() }))
                }
            }
        }
    }

    // 单一弹窗，通过 AnimatedContent 切换正/倒显示
    DialogLayout(
        visible = dialogVisible, enableWindowDim = !LocalGlassStyle.current.enabled,
        enterTransition = fadeIn(tween(300)) + slideInVertically(tween(300)) { if (isInverted) -it else it },
        exitTransition = fadeOut(tween(200)) + slideOutVertically(tween(200)) { if (isInverted) -it else it },
        renderInRootScaffold = true,
    ) {
        AnimatedContent(
            targetState = isInverted,
            transitionSpec = {
                val direction = if (targetState) -1 else 1
                Log.d(TAG, "QrCode invert animate: direction=$direction (targetState=$targetState)")
                (slideInVertically(tween(300)) { direction * it } + fadeIn(tween(300))) togetherWith
                (slideOutVertically(tween(200)) { -direction * it } + fadeOut(tween(200)))
            },
            label = "QrInvertTransition"
        ) { inverted ->
            QrDialogContent(inverted = inverted)
        }
    }
}

@Composable
private fun Modifier.qrPanelBackground(inverted: Boolean): Modifier {
    val style = LocalGlassStyle.current
    val backdrop = LocalMenuBackdrop.current ?: LocalGlassBackdrop.current ?: LocalPageBackdrop.current?.value
    val surface = MiuixTheme.colorScheme.surface
    val topRadius = if (inverted) 0.dp else BadgerRadius.surface
    val bottomRadius = if (inverted) BadgerRadius.surface else 0.dp
    val shape = remember(topRadius, bottomRadius) {
        BadgerShapes.corners(topRadius, topRadius, bottomRadius, bottomRadius)
    }
    LaunchedEffect(inverted, style.enabled) {
        Log.d("QrCodeExpandedViewTester", "展示弹层按角 G2；inverted=$inverted；glass=${style.enabled}")
    }
    return if (style.enabled) glassSurface(
        radius = BadgerRadius.surface,
        tint = style.menuTint,
        underlay = surface,
        backdrop = backdrop,
        bounds = {
            val topCorner = CornerRadius(topRadius.toPx())
            val bottomCorner = CornerRadius(bottomRadius.toPx())
            RoundRect(0f, 0f, size.width, size.height, topCorner, topCorner, bottomCorner, bottomCorner)
        },
    ) else background(surface, shape)
}
