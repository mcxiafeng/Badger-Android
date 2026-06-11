package top.mcxiafeng.badger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import coil3.compose.AsyncImage
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import kotlin.math.abs

private val avatarColors = listOf(
    Color(0xFF4A90D9), Color(0xFFE74C3C), Color(0xFF2ECC71),
    Color(0xFFF39C12), Color(0xFF9B59B6), Color(0xFF1ABC9C),
)

@Composable
fun AvatarPlaceholder(
    name: String,
    size: Int = 80,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.take(1).ifBlank { "?" },
            style = if (size >= 60) MiuixTheme.textStyles.title1 else MiuixTheme.textStyles.subtitle,
            color = MiuixTheme.colorScheme.primary
        )
    }
}

@Composable
fun ContactAvatar(
    name: String = "",
    avatarUrl: String? = null,
    avatarPath: String? = null,
    size: Int = 40,
    modifier: Modifier = Modifier,
    transparentBackground: Boolean = false
) {
    val bgColor = remember(name) {
        avatarColors[abs(name.hashCode()) % avatarColors.size]
    }

    // [修复防御]: 把 avatarPath + avatarUrl 一起拼成 model key，
    // 同时用 file.lastModified() 当作 cacheKey 后缀——Coil 会以 (model, cacheKey) 二元组
    // 做内存缓存键。详情页同步后覆盖了原文件但路径不变时，Coil 默认会复用旧 Bitmap，
    // 这里通过 cacheKey 强制失效。
    val imageModel: Any? = remember(avatarPath, avatarUrl) {
        when {
            !avatarPath.isNullOrBlank() -> {
                val f = File(avatarPath)
                if (f.exists()) f.absolutePath + "?" + f.lastModified() else avatarPath
            }
            !avatarUrl.isNullOrBlank() -> avatarUrl
            else -> null
        }
    }

    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (transparentBackground) Color.Transparent else bgColor),
        contentAlignment = Alignment.Center
    ) {
        if (imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = "头像",
                modifier = Modifier.size(size.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = name.take(1).ifBlank { "?" },
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = if (size >= 60) 24.sp else 16.sp
            )
        }
    }
}
