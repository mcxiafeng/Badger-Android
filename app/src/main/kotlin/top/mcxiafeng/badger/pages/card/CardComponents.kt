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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.mcxiafeng.badger.data.CardCollectionWithCount as CollectionWithCount
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
    var backgroundBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(item.backgroundImagePath) {
        backgroundBitmap = Methods.loadBackgroundBitmap(item.backgroundImagePath)
    }

    // DisposableEffect(Unit) 只创建一次，因此使用 rememberUpdatedState 确保 onDispose 看到的是最终 Bitmap。
    val latestBackgroundBitmap by rememberUpdatedState(backgroundBitmap)
    DisposableEffect(Unit) {
        onDispose {
            latestBackgroundBitmap?.takeIf { !it.isRecycled }?.recycle()
        }
    }

    val hasBg = !item.backgroundImagePath.isNullOrBlank()
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
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)))
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

            // 该计算会读取 Bitmap 像素；selection/recomposition 期间无需重复执行。
            val textColor = remember(
                backgroundBitmap,
                item.dominantColor,
                MiuixTheme.colorScheme.onBackground
            ) {
                textContentColorForBitmap(
                    backgroundBitmap,
                    item.dominantColor,
                    MiuixTheme.colorScheme.onBackground
                )
            }
            val subTextColor = subTextColorFor(textColor, MiuixTheme.colorScheme.onSurfaceVariantSummary)

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
                        text = item.name,
                        color = textColor,
                        style = MiuixTheme.textStyles.title4
                    )
                    if (!item.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.description,
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
