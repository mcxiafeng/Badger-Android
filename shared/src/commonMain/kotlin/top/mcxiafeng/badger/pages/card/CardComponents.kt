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
import top.mcxiafeng.badger.data.model.CardCollectionWithCount as CollectionWithCount
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.components.subTextColorFor
import top.mcxiafeng.badger.ui.components.collectionTextContentColor
import top.mcxiafeng.badger.platform.PlatformImage
import top.mcxiafeng.badger.platform.loadDecodedImage
import top.mcxiafeng.badger.utils.Methods
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import androidx.compose.foundation.shape.RoundedCornerShape
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Circle
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Folder

@Composable
fun CollectionCard(
    item: CollectionWithCount,
    selected: Boolean = false,
    isInSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val collection = item
    // [KMP K13c] 背景图：路径直渲染（Coil）；像素采样走边界
    var bgSampleImage by remember { mutableStateOf<PlatformImage?>(null) }
    LaunchedEffect(collection.backgroundImagePath) {
        bgSampleImage = loadDecodedImage(collection.backgroundImagePath)
    }

    val hasBg = !collection.backgroundImagePath.isNullOrBlank()
    val isDark = isSystemInDarkTheme()

    Card(
        modifier = modifier.height(200.dp).then(
            if (selected) Modifier.border(2.dp, MiuixTheme.colorScheme.primary, RoundedCornerShape(BadgerRadius.card))
            else Modifier
        ),
        cornerRadius = BadgerRadius.card,
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
        onLongPress = onLongClick,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Crossfade(
                targetState = collection.backgroundImagePath,
                animationSpec = tween(BadgerMotion.DURATION_BASE),
                label = "cardBgCrossfade"
            ) { bgPath ->
                if (bgPath != null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        coil3.compose.AsyncImage(
                            model = bgPath,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(BadgerRadius.card))
                        )
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)))
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0f to Color.Black.copy(alpha = 0.40f),
                                        0.28f to Color.Transparent,
                                        0.62f to Color.Transparent,
                                        1f to Color.Black.copy(alpha = 0.62f),
                                    ),
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
            val textColor = collectionTextContentColor(
                bgSampleImage, collection.dominantColor, MiuixTheme.colorScheme.onBackground
            )
            val subTextColor = subTextColorFor(textColor, MiuixTheme.colorScheme.onSurfaceVariantSummary)

            Column(
                modifier = Modifier.fillMaxSize().padding(BadgerSpacing.lg),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                if (!hasBg || bgSampleImage == null) {
                    Icon(
                        imageVector = Lucide.Folder,
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
                        style = MiuixTheme.textStyles.title3,
                        maxLines = 1,
                    )
                    val description = collection.description
                    if (!description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(BadgerSpacing.xxs))
                        Text(
                            text = description,
                            color = subTextColor,
                            style = MiuixTheme.textStyles.footnote1,
                            maxLines = 1,
                        )
                    }
                    Spacer(modifier = Modifier.height(BadgerSpacing.sm))
                    Text(
                        text = "${item.contactCount} 位联系人",
                        color = subTextColor.copy(alpha = 0.85f),
                        style = MiuixTheme.textStyles.footnote2,
                    )
                }
            }

            if (isInSelectionMode) {
                Icon(
                    imageVector = if (selected) Lucide.CircleCheck else Lucide.Circle,
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
