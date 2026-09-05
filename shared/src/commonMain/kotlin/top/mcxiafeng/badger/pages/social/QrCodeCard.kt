package top.mcxiafeng.badger.pages.social

import top.mcxiafeng.badger.data.prefs.PrefsStore
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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.mcxiafeng.badger.platform.ImageCodec
import top.mcxiafeng.badger.platform.PlatformImage
import top.mcxiafeng.badger.platform.QrCodeGenerator
import top.mcxiafeng.badger.utils.Methods
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.mcxiafeng.badger.utils.miuixShape
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.DialogLayout
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.platform.BackHandler

private const val TAG = "QrCodeCard"

@Composable
internal fun QrCodeCard(
    content: String,
    userName: String? = null,
    platformName: String? = null,
    platformValue: String? = null,
    avatarPath: String? = null
) {
    val isDark = isSystemInDarkTheme()
    var colorIndex by remember { mutableIntStateOf(0) }
    var showQrDialog by remember { mutableStateOf(false) }
    val currentColor = Methods.qrColors[colorIndex]
    // 深色模式：浅色码点；亮色模式：深色码点
    val qrForegroundColor = if (isDark) {
        if (colorIndex == 0) MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.3f) else currentColor
    } else {
        currentColor
    }
    // 用 MiuixTheme surface 色消除环形边
    val surfaceColor = MiuixTheme.colorScheme.surfaceContainer
    val qrBackgroundColor = remember(isDark, surfaceColor) {
        argb(
            (surfaceColor.alpha * 255).toInt(),
            (surfaceColor.red * 255).toInt(),
            (surfaceColor.green * 255).toInt(),
            (surfaceColor.blue * 255).toInt()
        )
    }
    val androidFgColor = remember(qrForegroundColor) {
        argb(
            255, (qrForegroundColor.red * 255).toInt(), (qrForegroundColor.green * 255).toInt(), (qrForegroundColor.blue * 255).toInt()
        )
    }
    val qrImageBitmap = remember(content, colorIndex, isDark) {
        QrCodeGenerator.generate(content, 512, androidFgColor, qrBackgroundColor)?.let { img ->
            try { ImageCodec.encodePng(img)?.let { it.decodeToImageBitmap() } } finally { img.close() }
        }
    }
    val qrContainerColor = Color.Transparent

    BackHandler(enabled = showQrDialog) { showQrDialog = false }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).aspectRatio(1f),
        cornerRadius = 12.dp, insideMargin = PaddingValues(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(text = "扫码添加", style = MiuixTheme.textStyles.subtitle)
            Spacer(modifier = Modifier.height(12.dp))
            // 弹窗打开时隐藏小二维码，防止误扫
            if (!showQrDialog) {
                Box(
                    modifier = Modifier
                        .weight(1f).aspectRatio(1f)
                        .combinedClickable(onClick = { BadgerLog.d(TAG, "QrCode dialog open"); showQrDialog = true }, onLongClick = { BadgerLog.d(TAG, "QrCode color cycle: $colorIndex -> ${(colorIndex + 1) % Methods.qrColors.size}"); colorIndex = (colorIndex + 1) % Methods.qrColors.size })
                        .background(qrContainerColor, miuixShape(8.dp)).padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    qrImageBitmap?.let { bmp ->
                        Image(bitmap = bmp, contentDescription = "QR Code", modifier = Modifier.fillMaxSize())
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "点击查看 · 长按换色", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            } else {
                Text(text = "二维码展示中...", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
    }

    // 二维码放大弹窗
    val qrDialogVisible = remember { mutableStateOf(false) }
    SideEffect { qrDialogVisible.value = showQrDialog }
    // 正/倒显示模式：true=倒序从顶部弹出（给对方看），false=正序从底部弹出（自己看）
    // [KMP K05] DataStore（经 PrefsStore），原 social_prefs 文件
    var isInverted by remember { mutableStateOf(PrefsStore.readBoolean("qr_inverted", true)) }

    // 弹窗内容（头像→名字→平台→二维码→提示）
    @Composable
    fun QrDialogContent(inverted: Boolean) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { BadgerLog.d(TAG, "QrCode dialog close"); showQrDialog = false },
            contentAlignment = if (inverted) Alignment.TopCenter else Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { rotationZ = if (inverted) 180f else 0f }
                    .clickable { }
                    .background(
                        MiuixTheme.colorScheme.surface,
                        if (inverted) RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                        else RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
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
                        BadgerLog.d(TAG, "QrCode invert toggle: $inverted -> $isInverted")
                        PrefsStore.writeBoolean("qr_inverted", isInverted)
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
                                    .clip(RoundedCornerShape(4.dp))
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
                // 二维码
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(qrContainerColor, miuixShape(12.dp)).padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        qrImageBitmap?.let { bmp ->
                        Image(bitmap = bmp, contentDescription = "QR Code", modifier = Modifier.fillMaxSize())
                    }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "点击头像可切换方向", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
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
        visible = qrDialogVisible, enableWindowDim = true,
        enterTransition = fadeIn(tween(300)) + slideInVertically(tween(300)) { if (isInverted) -it else it },
        exitTransition = fadeOut(tween(200)) + slideOutVertically(tween(200)) { if (isInverted) -it else it },
        renderInRootScaffold = true,
    ) {
        AnimatedContent(
            targetState = isInverted,
            transitionSpec = {
                val direction = if (targetState) -1 else 1
                BadgerLog.d(TAG, "QrCode invert animate: direction=$direction (targetState=$targetState)")
                (slideInVertically(tween(300)) { direction * it } + fadeIn(tween(300))) togetherWith
                (slideOutVertically(tween(200)) { -direction * it } + fadeOut(tween(200)))
            },
            label = "QrInvertTransition"
        ) { inverted ->
            QrDialogContent(inverted = inverted)
        }
    }
}

/** [KMP K13c] ARGB 组装（替代 android.graphics.Color.argb）。 */
private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
    (alpha shl 24) or (red shl 16) or (green shl 8) or blue
