package top.mcxiafeng.badger.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.ui.designsystem.BadgerDesignColors
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.utils.Methods
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private const val QR_SHARE_LOG_TAG = "QrShareDialogTester"
private const val QR_SHARE_BITMAP_SIZE_PX = 512
private const val QR_SHARE_QUIET_ZONE_MODULES = 4
private const val INVERTED_ROTATION_DEGREES = 180f
private const val SHARE_HEADER_AVATAR_SIZE = 48
private const val SHARE_NAME_MAX_LINES = 2

/** 平台轮选二维码弹窗的共用数据模型，联系人分享与首页出示二维码都映射到它。 */
@Immutable
data class QrSharePlatform(
    val key: String,
    val label: String,
    val qrContent: String,
    val accountId: String?,
    /** 覆盖头部姓名（如首页各平台昵称）；为空时回退到弹窗的 name 参数。 */
    val displayName: String? = null,
)

/** 纯渲染：黑码配浅底（暗色主题用深浅适中的灰底），失败返回 Result.failure 并记录日志。 */
internal suspend fun renderQrShareBitmap(content: String, backgroundColor: Int): Result<Bitmap> =
    withContext(Dispatchers.Default) {
        try {
            val bitmap = Methods.generateQRCode(
                content, QR_SHARE_BITMAP_SIZE_PX, AndroidColor.BLACK,
                backgroundColor = backgroundColor,
                quietZoneModules = QR_SHARE_QUIET_ZONE_MODULES,
            )
            Log.d(QR_SHARE_LOG_TAG, "Generated share QR; whiteBackground=${backgroundColor == AndroidColor.WHITE}")
            Result.success(bitmap)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(QR_SHARE_LOG_TAG, "Could not generate share QR (${error.javaClass.simpleName})")
            Result.failure(error)
        }
    }

/**
 * 出示二维码弹窗（联系人分享与首页共用）。
 *
 * 结构：头部（头像 + 姓名 + 账号 + 分享按钮）+ 平台轮选 + 二维码；
 * 点击头像整体旋转 180°，便于把屏幕对面的对方看清。
 *
 * @param name 头部兜底姓名；平台未提供 displayName 时显示，并作为系统分享文案的名字
 * @param platforms 平台列表；null 表示加载中，空列表显示 emptyText
 * @param paneTitle 无障碍面板标题
 */
@Composable
fun QrShareDialog(
    name: String,
    avatarUrl: String?,
    avatarPath: String?,
    platforms: List<QrSharePlatform>?,
    onDismiss: () -> Unit,
    paneTitle: String,
    emptyText: String = "暂无可分享的平台资料",
    initialPlatformIndex: Int = 0,
    pagerTestTag: String = "qr_share_platform_pager",
) {
    var inverted by remember { mutableStateOf(false) }
    // WindowDialog 的内容在独立窗口组合，上下文取自打开弹窗的页面，头像加载等沿用页面账户。
    val accountContext = LocalContext.current
    val colors = MiuixTheme.colorScheme
    val dark = colors.background.luminance() < colors.onBackground.luminance()
    val qrBackground = if (dark) BadgerDesignColors.contactShareQrDarkBackground else Color.White
    LaunchedEffect(Unit) {
        Log.d(QR_SHARE_LOG_TAG, "QrShareDialog mounted; platforms=${platforms?.size}")
    }
    WindowDialog(
        show = true,
        modifier = Modifier
            .fillMaxHeight()
            .padding(top = BadgerSpacing.xs)
            .wrapContentHeight(if (inverted) Alignment.Top else Alignment.Bottom)
            .graphicsLayer { rotationZ = if (inverted) INVERTED_ROTATION_DEGREES else 0f },
        outsideMargin = DpSize(BadgerSpacing.md, BadgerSpacing.xs),
        insideMargin = DpSize(BadgerSpacing.lg, BadgerSpacing.md),
        onDismissRequest = onDismiss,
    ) {
        // 参数名与 semantics 的 paneTitle 扩展同名，需显式接收器赋值。
        BoxWithConstraints(
            Modifier.fillMaxWidth().semantics { this.paneTitle = paneTitle },
        ) {
            val qrSize = minOf(maxWidth, maxHeight * BadgerSize.contactShareQrHeightFraction, BadgerSize.contactShareQrMaxSize)
            CompositionLocalProvider(LocalContext provides accountContext) {
                QrShareContent(
                    name = name,
                    avatarUrl = avatarUrl,
                    avatarPath = avatarPath,
                    platforms = platforms,
                    qrSize = qrSize,
                    qrBackground = qrBackground,
                    emptyText = emptyText,
                    initialPlatformIndex = initialPlatformIndex,
                    pagerTestTag = pagerTestTag,
                    onRotate = {
                        inverted = !inverted
                        Log.d(QR_SHARE_LOG_TAG, "Rotated share dialog; inverted=$inverted")
                    },
                )
            }
        }
    }
}

@Composable
private fun QrShareContent(
    name: String,
    avatarUrl: String?,
    avatarPath: String?,
    platforms: List<QrSharePlatform>?,
    qrSize: Dp,
    qrBackground: Color,
    emptyText: String,
    initialPlatformIndex: Int,
    pagerTestTag: String,
    onRotate: () -> Unit,
) {
    val resolved = platforms.orEmpty()
    // 停稳后再切换二维码与账号文案，避免中途经过的页闪烁。
    val pagerState = rememberPagerState(
        initialPage = initialPlatformIndex.coerceIn(0, resolved.lastIndex.coerceAtLeast(0)),
        pageCount = { resolved.size },
    )
    val platform = resolved.getOrNull(pagerState.settledPage)
    val platformKeys = remember(resolved) { resolved.map { it.key } }
    LaunchedEffect(platform) {
        if (platform != null) Log.d(QR_SHARE_LOG_TAG, "Share platform settled; key=${platform.key}")
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BadgerSpacing.md)) {
        QrShareHeader(name, avatarUrl, avatarPath, platform, onRotate)
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
                .padding(top = BadgerSpacing.scrollContentTopGap),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BadgerSpacing.sm),
        ) {
            when {
                platforms == null -> CircularProgressIndicator()
                platform == null -> Text(emptyText)
                else -> {
                    PlatformIconPager(
                        platformKeys = platformKeys,
                        pagerState = pagerState,
                        pagerModifier = Modifier.testTag(pagerTestTag),
                    )
                    QrShareQr(platform, qrSize, qrBackground)
                }
            }
        }
    }
}

@Composable
private fun QrShareHeader(
    name: String,
    avatarUrl: String?,
    avatarPath: String?,
    platform: QrSharePlatform?,
    onRotate: () -> Unit,
) {
    val context = LocalContext.current
    val accountId = platform?.accountId
    val displayName = platform?.displayName ?: name
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.sm),
    ) {
        ContactAvatar(
            name = displayName,
            avatarUrl = avatarUrl,
            avatarPath = avatarPath,
            size = SHARE_HEADER_AVATAR_SIZE,
            modifier = Modifier.clickable(onClickLabel = "旋转分享卡片", onClick = onRotate),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BadgerSpacing.xs)) {
            Text(displayName, style = MiuixTheme.textStyles.title4, maxLines = SHARE_NAME_MAX_LINES,
                overflow = TextOverflow.Ellipsis)
            if (accountId != null) SelectionContainer {
                Text(accountId, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
        IconButton(
            onClick = {
                platform?.let { current ->
                    Log.d(QR_SHARE_LOG_TAG, "Share button clicked; key=${current.key}")
                    shareQrPlatform(context, displayName, current)
                }
            },
            enabled = platform != null,
            minWidth = BadgerSize.minimumTouchTarget,
            minHeight = BadgerSize.minimumTouchTarget,
        ) {
            Icon(
                Icons.Default.Share,
                contentDescription = "分享联系方式",
                tint = if (platform != null) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.disabledOnSurface,
            )
        }
    }
}

private fun shareQrPlatform(context: Context, name: String, platform: QrSharePlatform) {
    val text = buildString {
        appendLine(name)
        append("${platform.label}：${platform.accountId ?: platform.qrContent}")
        if (platform.accountId != null && platform.accountId != platform.qrContent) {
            append('\n')
            append(platform.qrContent)
        }
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        context.startActivity(Intent.createChooser(send, null))
        Log.d(QR_SHARE_LOG_TAG, "Opened Android chooser for platform ${platform.key}")
    } catch (error: ActivityNotFoundException) {
        Log.w(QR_SHARE_LOG_TAG, "Android sharing chooser is unavailable", error)
        Toast.makeText(context, "无法打开系统分享", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun QrShareQr(platform: QrSharePlatform, size: Dp, backgroundColor: Color) {
    val result by produceState<Result<Bitmap>?>(null, platform, backgroundColor) {
        value = renderQrShareBitmap(platform.qrContent, backgroundColor.toArgb())
    }
    val bitmap = result?.getOrNull()
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        when {
            result == null -> CircularProgressIndicator()
            bitmap == null -> Text("无法生成二维码", textAlign = TextAlign.Center)
            else -> Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "${platform.label}二维码",
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
