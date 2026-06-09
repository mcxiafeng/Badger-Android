package top.mcxiafeng.badger.pages.social

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ui.components.PlatformIcon
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
 * 未设置信息时的引导卡片
 */
@Composable
fun SetupGuideCard(onNavigateToProfile: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        insideMargin = PaddingValues(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().clickable {
                Log.d("SocialPage", "Create card clicked (needSetup), calling onNavigateToProfile()")
                onNavigateToProfile()
            },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "创建你的名片",
                style = MiuixTheme.textStyles.title3,
                color = MiuixTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "添加社交账号，生成二维码分享给朋友",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                text = "开始设置",
                onClick = {
                    Log.d("SocialPage", "Start setup clicked, calling onNavigateToProfile()")
                    onNavigateToProfile()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 蓝色名片卡片（含背景图/头像/NFC 状态指示器/长按菜单）
 *
 * @param cardBitmap 名片背景图片
 * @param profileName 用户昵称
 * @param profileBio 用户签名
 * @param showNfcMenu 是否显示长按菜单
 * @param linkUpdateState 链接更新状态
 * @param nfcSupported 是否支持 NFC
 * @param onShowNfcWriteDialog 点击卡片回调
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
    onShowNfcWriteDialog: () -> Unit,
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
                    Log.d("SocialPage", "Blue card clicked, calling onShowNfcWriteDialog()")
                    onShowNfcWriteDialog()
                },
                onLongClick = onShowNfcMenu
            )
            .height(160.dp),
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
            if (profileName != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Text(
                        text = profileName, color = Color.White,
                        style = MiuixTheme.textStyles.title2,
                        modifier = Modifier.graphicsLayer { shadowElevation = 4.dp.toPx() }
                    )
                    if (profileBio != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = profileBio, color = Color.White.copy(alpha = 0.85f), style = MiuixTheme.textStyles.body2,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.graphicsLayer { shadowElevation = 2.dp.toPx() }
                        )
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (profileName != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = profileName, color = Color.White, style = MiuixTheme.textStyles.title2)
                        if (profileBio != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = profileBio, color = Color.White.copy(alpha = 0.85f), style = MiuixTheme.textStyles.body2,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Text(
                        text = "创建名片",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MiuixTheme.textStyles.title2,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

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

/**
 * 社交平台图标横排行
 *
 * @param platforms 平台列表 (fieldKey, entry)
 * @param selectedPlatformIndex 当前选中的平台索引
 * @param onSelectPlatform 选择平台回调
 */
@Composable
fun PlatformSwitchRow(
    platforms: List<Pair<String, *>>,
    selectedPlatformIndex: Int,
    onSelectPlatform: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
    ) {
        platforms.forEachIndexed { index, (fieldKey, _) ->
            val isSelected = index == selectedPlatformIndex
            val displayName = FIELD_DEF_MAP[fieldKey]?.displayName ?: fieldKey
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onSelectPlatform(index) }
            ) {
                PlatformIcon(
                    fieldKey = fieldKey,
                    color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.3f),
                    sizeDp = 36f
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = displayName,
                    style = MiuixTheme.textStyles.footnote2,
                    color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1
                )
            }
        }
    }
}
