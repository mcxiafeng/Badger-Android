package top.mcxiafeng.badger.pages.card

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity as CardCollection
import top.mcxiafeng.badger.ui.components.subTextColorFor
import top.mcxiafeng.badger.ui.components.textContentColorForBitmap
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 名片夹详情页 — Hero 头部区域（背景图 + 名称 + 描述）
 */
@Composable
internal fun CollectionDetailHeroHeader(collection: CardCollection?) {
    val hasBg = !collection?.backgroundImagePath.isNullOrBlank()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .animateContentSize(animationSpec = tween(300))
    ) {
        val headerHeight = if (hasBg) 200.dp else 80.dp
        Box(modifier = Modifier.fillMaxWidth().height(headerHeight)) {
            var bgBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
            LaunchedEffect(collection?.backgroundImagePath) {
                bgBitmap = top.mcxiafeng.badger.utils.Methods.loadBackgroundBitmap(collection?.backgroundImagePath)
            }
            val isDark = isSystemInDarkTheme()
            Crossfade(targetState = bgBitmap, animationSpec = tween(300), label = "heroBgCrossfade") { bmp ->
                if (bmp != null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)))
                        Box(modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                                startY = 0f
                            )
                        ))
                        if (isDark) {
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surfaceContainer))
                }
            }
            val heroTextColor = textContentColorForBitmap(
                bgBitmap, collection?.dominantColor, MiuixTheme.colorScheme.onBackground
            )
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = collection?.name ?: "",
                    color = heroTextColor,
                    style = MiuixTheme.textStyles.title3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!collection?.description.isNullOrBlank()) {
                    Text(
                        text = collection?.description.orEmpty(),
                        color = subTextColorFor(heroTextColor, MiuixTheme.colorScheme.onSurfaceVariantSummary),
                        style = MiuixTheme.textStyles.body2,
                        maxLines = if (hasBg) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
