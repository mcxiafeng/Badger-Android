package top.mcxiafeng.badger.pages.card

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.mcxiafeng.badger.data.CollectionWithCount
import top.mcxiafeng.badger.ui.components.textContentColorForBitmap
import top.mcxiafeng.badger.ui.components.subTextColorFor
import top.mcxiafeng.badger.utils.Methods
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun CollectionCard(
    item: CollectionWithCount,
    selected: Boolean = false,
    isInSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val collection = item.collection
    var backgroundBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(collection.backgroundImagePath) {
        backgroundBitmap = Methods.loadBackgroundBitmap(collection.backgroundImagePath)
    }

    val hasBg = !collection.backgroundImagePath.isNullOrBlank()
    val isDark = isSystemInDarkTheme()

    Card(
        modifier = modifier.height(200.dp).then(
            if (selected) Modifier.border(2.dp, MiuixTheme.colorScheme.primary, RoundedCornerShape(16.dp))
            else Modifier
        ),
        cornerRadius = 16.dp,
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
        onLongPress = onLongClick,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Crossfade(
                targetState = backgroundBitmap,
                animationSpec = tween(300),
                label = "cardBgCrossfade"
            ) { bmp ->
                if (bmp != null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                        )
                        // 全图半透明遮罩，保证文字在任何背景上都有最低对比度
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)))
                        // 底部渐变，强化文字区域对比度
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                                    startY = 0f
                                )
                            )
                        )
                        if (isDark) {
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surfaceContainer)
                    )
                }
            }

            // 根据背景图底部区域实际像素亮度决定文字颜色
            val textColor = textContentColorForBitmap(
                backgroundBitmap, collection.dominantColor, MiuixTheme.colorScheme.onBackground
            )
            val subTextColor = subTextColorFor(textColor, Color(0xDE1C1B1FL))

            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                if (!hasBg || backgroundBitmap == null) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = if (hasBg) textColor else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(32.dp))
                }

                Column {
                    Text(
                        text = collection.name,
                        color = textColor,
                        style = MiuixTheme.textStyles.title4
                    )
                    if (!collection.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = collection.description,
                            color = subTextColor,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${item.contactCount} 位联系人",
                        color = subTextColor,
                        fontSize = 12.sp
                    )
                }
            }

            if (isInSelectionMode) {
                Icon(
                    imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = if (selected) "已选中" else "未选中",
                    tint = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(24.dp)
                )
            }
        }
    }
}
