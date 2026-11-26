package top.mcxiafeng.badger.pages.social

import android.graphics.Color as AndroidColor
import android.graphics.Bitmap
import androidx.core.graphics.ColorUtils
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import top.mcxiafeng.badger.ui.designsystem.continuousClip as clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.components.PlatformIconPager
import top.mcxiafeng.badger.ui.designsystem.BadgerAlpha
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.glass.OverlayListPopup
import top.mcxiafeng.badger.ui.glass.TextButton
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.mcxiafeng.badger.ui.glass.ListPopupColumn
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.mcxiafeng.badger.ui.designsystem.BadgerShapes
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import kotlin.math.max

private const val CARD_TEXT_SAMPLE_GRID = 8
private const val CARD_TEXT_LOG_TAG = "SocialCardContrastTester"

/**
 * 蓝色名片卡片（含背景图/头像/NFC 状态指示器/长按菜单）
 *
 * @param cardBitmap 名片背景图片
 * @param profileName 用户昵称
 * @param profileBio 用户签名
 * @param showNfcMenu 是否显示长按菜单
 * @param linkUpdateState 链接更新状态
 * @param nfcSupported 是否支持 NFC
 * @param onCardClick 点击卡片回调（正常态打开 NFC 写入弹窗，占位态导航到编辑页）
 * @param onShowNfcMenu 长按回调
 * @param onDismissNfcMenu 关闭菜单回调
 * @param onPickImage 选择图片回调
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BlueBusinessCard(
    cardBitmap: Bitmap?,
    profileName: String?,
    profileBio: String?,
    showNfcMenu: Boolean,
    linkUpdateState: LinkUpdateState,
    nfcSupported: Boolean,
    onCardClick: () -> Unit,
    onShowNfcMenu: () -> Unit,
    onDismissNfcMenu: () -> Unit,
    onPickImage: () -> Unit
) {
    val cardShape = BadgerShapes.Surface
    var cardSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .shadow(
                elevation = 16.dp, shape = cardShape, clip = false,
                ambientColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.2f),
                spotColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
            .clip(cardShape)
            .background(MiuixTheme.colorScheme.primary, cardShape)
            .combinedClickable(
                onClick = {
                    Log.d("SocialPage", "Blue card clicked")
                    onCardClick()
                },
                onLongClick = onShowNfcMenu
            )
            .aspectRatio(BadgerSize.SOCIAL_CARD_ASPECT_RATIO)
            .onSizeChanged {
                cardSize = it
                Log.d("SocialPageTester", "名片显示比例5:3；widthPx=${it.width}；heightPx=${it.height}")
            }
            .testTag("social_business_card"),
        contentAlignment = Alignment.Center
    ) {
        if (cardBitmap != null) {
            Image(
                bitmap = cardBitmap.asImageBitmap(),
                contentDescription = "名片背景",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = BadgerAlpha.SOCIAL_CARD_IMAGE_SCRIM_ALPHA)))
        }
        BusinessCardText(
            name = profileName,
            bio = profileBio,
            cardBitmap = cardBitmap,
            cardSize = cardSize,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(BadgerSpacing.xl)
        )

        // 右上角状态指示器
        when (linkUpdateState) {
            LinkUpdateState.UPDATING -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(MiuixTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(size = 14.dp, strokeWidth = 2.dp)
                }
            }
            LinkUpdateState.SUCCESS -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(MiuixTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "更新成功",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            LinkUpdateState.ERROR -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(MiuixTheme.colorScheme.error, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "更新失败",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            LinkUpdateState.IDLE -> {
                if (nfcSupported) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(24.dp)
                            .background(MiuixTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "NFC 就绪",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // 长按菜单（仅图片相关）
        OverlayListPopup(
            show = showNfcMenu,
            alignment = PopupPositionProvider.Align.TopEnd,
            popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
            onDismissRequest = onDismissNfcMenu
        ) {
            ListPopupColumn {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismissNfcMenu()
                            onPickImage()
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Filled.Image, contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (cardBitmap != null) "更改图片" else "设置图片",
                            style = MiuixTheme.textStyles.body1, color = MiuixTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BusinessCardText(
    name: String?, bio: String?, cardBitmap: Bitmap?, cardSize: IntSize, modifier: Modifier = Modifier,
) {
    val background = MiuixTheme.colorScheme.primary
    val inset = with(LocalDensity.current) { BadgerSpacing.xl.toPx() }
    var textSize by remember { mutableStateOf(IntSize.Zero) }
    val foreground = remember(cardBitmap, background, cardSize, textSize, inset) {
        businessCardTextColor(cardBitmap, background, cardSize, textSize.height, inset)
    }
    Column(modifier = modifier.onSizeChanged { textSize = it }, horizontalAlignment = Alignment.Start) {
        Text(
            text = name.orEmpty(),
            modifier = Modifier.testTag("social_business_card_name"),
            style = MiuixTheme.textStyles.title2,
            color = foreground,
            textAlign = TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!bio.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(BadgerSpacing.xs))
            Text(
                text = bio,
                modifier = Modifier.testTag("social_business_card_signature"),
                style = MiuixTheme.textStyles.body2,
                color = foreground.copy(alpha = BadgerAlpha.AVATAR_TEXT_ALPHA),
                textAlign = TextAlign.Start,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Only visible photo pixels and the card's own scrim determine the foreground, never onPrimary. */
internal fun businessCardTextColor(
    bitmap: Bitmap?, background: Color, viewport: IntSize, textHeightPx: Int, insetPx: Float,
): Color {
    if (bitmap == null) {
        val foreground = contrastingCardText(background.toArgb())
        Log.d(CARD_TEXT_LOG_TAG, "Card text uses actual solid fill; lightText=${foreground == Color.White}")
        return foreground
    }
    if (bitmap.isRecycled) {
        Log.w(CARD_TEXT_LOG_TAG, "Background unavailable for sampling; fixed white fallback")
        return Color.White
    }
    var readable: Bitmap? = null
    return try {
        readable = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        val source = checkNotNull(readable) { "Cannot read card background pixels" }
        val foreground = contrastingCardText(sampleCardTextBackground(source, background, viewport, textHeightPx, insetPx))
        Log.d(CARD_TEXT_LOG_TAG, "Card text sampled after crop/scrim; lightText=${foreground == Color.White}; viewport=$viewport; textHeightPx=$textHeightPx")
        foreground
    } catch (error: Exception) {
        Log.e(CARD_TEXT_LOG_TAG, "Card contrast sampling failed; fixed white fallback", error)
        Color.White
    } finally {
        // Only the temporary software copy belongs to this calculation; never recycle the displayed image.
        if (readable !== bitmap) readable?.recycle()
    }
}

private fun contrastingCardText(background: Int): Color =
    if (ColorUtils.calculateContrast(AndroidColor.BLACK, background) >=
        ColorUtils.calculateContrast(AndroidColor.WHITE, background)) Color.Black else Color.White

private fun sampleCardTextBackground(
    bitmap: Bitmap, background: Color, viewport: IntSize, textHeightPx: Int, insetPx: Float,
): Int {
    val width = viewport.width.takeIf { it > 0 } ?: bitmap.width
    val height = viewport.height.takeIf { it > 0 } ?: bitmap.height
    val region = if (viewport != IntSize.Zero && textHeightPx > 0) {
        Rect(insetPx, (height - insetPx - textHeightPx).coerceAtLeast(0f), width - insetPx, height - insetPx)
    } else Rect(0f, height * 2f / 3f, width.toFloat(), height.toFloat())
    val scale = max(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
    val scrim = Color.Black.copy(alpha = BadgerAlpha.SOCIAL_CARD_IMAGE_SCRIM_ALPHA).toArgb()
    val fill = background.toArgb()
    var red = 0
    var green = 0
    var blue = 0
    repeat(CARD_TEXT_SAMPLE_GRID) { row ->
        repeat(CARD_TEXT_SAMPLE_GRID) { column ->
            val x = region.left + region.width * (column + 0.5f) / CARD_TEXT_SAMPLE_GRID
            val y = region.top + region.height * (row + 0.5f) / CARD_TEXT_SAMPLE_GRID
            val sourceX = ((x - width / 2f) / scale + bitmap.width / 2f).toInt().coerceIn(0, bitmap.width - 1)
            val sourceY = ((y - height / 2f) / scale + bitmap.height / 2f).toInt().coerceIn(0, bitmap.height - 1)
            val pixel = ColorUtils.compositeColors(scrim, ColorUtils.compositeColors(bitmap.getPixel(sourceX, sourceY), fill))
            red += AndroidColor.red(pixel)
            green += AndroidColor.green(pixel)
            blue += AndroidColor.blue(pixel)
        }
    }
    val count = CARD_TEXT_SAMPLE_GRID * CARD_TEXT_SAMPLE_GRID
    return AndroidColor.rgb(red / count, green / count, blue / count)
}

/**
 * 平台选择卡片
 *
 * Pager 轮选交互：选中的平台默认停在卡片中间（居中页），点击侧边图标或左右滑动切换；
 * 卡片底部中间一个圆点，代表居中的平台是当前选中（二维码/NFC 使用该平台）。
 * 最后一页是"+"添加入口，作为普通页可停留（居中显示，无回弹），圆点在其上隐藏；
 * 无平台时该页是唯一内容，自然居中。
 *
 * @param platforms 平台列表 (fieldKey, entry)
 * @param selectedPlatformIndex 当前选中的平台索引
 * @param onSelectPlatform 选择平台回调
 * @param onAddPlatform 添加平台回调（点击"+"时触发）
 */
@Composable
fun PlatformIconsCard(
    platforms: List<Pair<String, *>>,
    selectedPlatformIndex: Int,
    pagerState: PagerState,
    onSelectPlatform: (Int) -> Unit,
    onAddPlatform: () -> Unit
) {
    val pageCount = platforms.size + 1
    val currentSelected by rememberUpdatedState(selectedPlatformIndex)
    val currentOnSelect by rememberUpdatedState(onSelectPlatform)

    // 与上方名片共用 PagerState。仅外部选择不一致时校正，避免打断用户正在进行的翻页。
    LaunchedEffect(selectedPlatformIndex, pageCount) {
        val target = selectedPlatformIndex.coerceIn(0, pageCount - 1)
        if (pagerState.currentPage != target) pagerState.requestScrollToPage(target)
    }
    // 视觉内容跟随 currentPage；二维码/NFC 目标在停稳后更新，不为中途经过的页更新短链。
    LaunchedEffect(pagerState, platforms.size) {
        snapshotFlow { pagerState.settledPage to pagerState.isScrollInProgress }.collect { (page, scrolling) ->
            if (!scrolling && page in platforms.indices && page != currentSelected) {
                Log.d("SocialPageTester", "Platform settled: index=$page")
                currentOnSelect(page)
            }
        }
    }

    val platformKeys = remember(platforms) { platforms.map { it.first } }
    PlatformIconPager(
        platformKeys = platformKeys,
        pagerState = pagerState,
        modifier = Modifier.padding(horizontal = BadgerSpacing.lg),
        pagerModifier = Modifier.testTag("social_platform_pager"),
        onAddPlatform = {
            Log.d("SocialPageTester", "Opened add platform from carousel")
            onAddPlatform()
        },
    )
}
