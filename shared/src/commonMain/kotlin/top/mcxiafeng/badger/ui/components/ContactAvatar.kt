package top.mcxiafeng.badger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 联系人 / 用户头像通用组件
 * 优先根据传入的图片 (model / avatarPath / avatarUrl) 渲染自定义图片，
 * 如果未提供图片或图片加载失败，自动降级为显示首字。
 */
@Composable
fun ContactAvatar(
    name: String,
    avatarPath: String? = null,
    avatarUrl: String? = null,
    model: Any? = null,
    size: Int = 72,
    color: Color = MiuixTheme.colorScheme.primary.copy(alpha = 0.28f),
    textColor: Color = MiuixTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val sizeDp = size.dp
    val textStyle = when {
        size < 40 -> MiuixTheme.textStyles.footnote1
        size < 56 -> MiuixTheme.textStyles.title4
        size < 80 -> MiuixTheme.textStyles.title3
        size < 112 -> MiuixTheme.textStyles.title2
        else -> MiuixTheme.textStyles.title1
    }

    val imageModel: Any? = remember(model, avatarPath, avatarUrl) {
        model
            ?: avatarPath.takeIf { !it.isNullOrBlank() }
            ?: avatarUrl.takeIf { !it.isNullOrBlank() }
    }

    var isError by remember(imageModel) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(sizeDp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        // 首字 Text 作为占位与失败回落 UI
        Text(
            text = name.trim().firstOrNull()?.uppercase() ?: "?",
            style = textStyle,
            color = textColor
        )

        // 图片未出错且模型非空时，在上方绘制 AsyncImage
        if (imageModel != null && !isError) {
            AsyncImage(
                model = imageModel,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(sizeDp)
                    .clip(CircleShape),
                onState = { state ->
                    if (state is AsyncImagePainter.State.Error) {
                        isError = true
                    }
                }
            )
        }
    }
}

