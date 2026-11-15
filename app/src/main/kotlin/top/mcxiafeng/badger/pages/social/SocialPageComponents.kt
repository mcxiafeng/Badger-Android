package top.mcxiafeng.badger.pages.social

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ui.components.PlatformIcon
import top.mcxiafeng.badger.ui.designsystem.BadgerAlpha
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.mcxiafeng.badger.utils.miuixShape
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.ListPopupDefaults

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
    val cardShape = miuixShape(24.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .shadow(
                elevation = 16.dp, shape = cardShape,
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
            .height(160.dp)
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
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
        }
        BusinessCardText(
            name = profileName,
            bio = profileBio,
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
private fun BusinessCardText(name: String?, bio: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(
            text = name.orEmpty(),
            modifier = Modifier.testTag("social_business_card_name"),
            style = MiuixTheme.textStyles.title2,
            color = MiuixTheme.colorScheme.onPrimary,
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
                color = MiuixTheme.colorScheme.onPrimary.copy(alpha = BadgerAlpha.AVATAR_TEXT_ALPHA),
                textAlign = TextAlign.Start,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
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
    val scope = rememberCoroutineScope()
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        insideMargin = PaddingValues(vertical = 8.dp)
    ) {
        Column {
            // 手算水平 contentPadding 实现居中 snap（Start snap + 两侧 padding = 页中心对齐卡片中心），
            // 避免 SnapPosition.Center 在 pageCount 动态变化时的 offset 重排问题
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val pageWidth = 88.dp
                val horizontalPadding = ((maxWidth - pageWidth) / 2).coerceAtLeast(0.dp)
                HorizontalPager(
                    state = pagerState,
                    pageSize = PageSize.Fixed(pageWidth),
                    contentPadding = PaddingValues(horizontal = horizontalPadding),
                    modifier = Modifier.fillMaxWidth().testTag("social_platform_pager")
                ) { page ->
                    // 高亮跟随居中页（视觉焦点），与数据层选中（selectedPlatformIndex，决定二维码/NFC 目标）解耦：
                    // 停在"+"页时全部平台变灰、"+"亮起；滑回平台页恢复其高亮。
                    val isCentered = page == pagerState.currentPage
                    if (page < platforms.size) {
                        val (fieldKey, _) = platforms[page]
                        val displayName = FIELD_DEF_MAP[fieldKey]?.displayName ?: fieldKey
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable {
                                    Log.d("SocialPage", "Platform tapped: page=$page, key=$fieldKey")
                                    scope.launch { pagerState.animateScrollToPage(page) }
                                }
                        ) {
                            PlatformIcon(
                                fieldKey = fieldKey,
                                color = if (isCentered) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.3f),
                                sizeDp = 40f,
                                shape = CircleShape
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = displayName,
                                style = MiuixTheme.textStyles.footnote2,
                                color = if (isCentered) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 1
                            )
                        }
                    } else {
                        // "+" 添加平台入口页（普通页，可停留，居中显示；居中时亮起表示当前停在此页）
                        val isAddCentered = isCentered
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable {
                                    Log.d("SocialPage", "Platform add clicked, calling onAddPlatform()")
                                    onAddPlatform()
                                }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isAddCentered) MiuixTheme.colorScheme.primary
                                        else MiuixTheme.colorScheme.surfaceContainer
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "添加平台",
                                    tint = if (isAddCentered) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "添加",
                                style = MiuixTheme.textStyles.footnote2,
                                color = if (isAddCentered) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }
                }
            }
            // 中间一个点：卡片中央总有一页被选中（平台或"+"），圆点常亮
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primary)
                )
            }
        }
    }
}
