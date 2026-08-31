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
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

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
    val context = LocalContext.current
    val bgColor = remember(name) {
        avatarColors[Math.floorMod(name.hashCode(), avatarColors.size)]
    }

    // Keep the filesystem path as the actual Coil model and version the memory-cache key
    // separately. Appending a query string to a local path can turn the cache-busting suffix
    // into part of the filename instead of a cache key.
    val imageModel: Any? = remember(avatarPath, avatarUrl) {
        when {
            !avatarPath.isNullOrBlank() -> {
                val file = File(avatarPath)
                if (file.exists()) {
                    ImageRequest.Builder(context)
                        .data(file)
                        .memoryCacheKey("contact-avatar:${file.absolutePath}:${file.lastModified()}")
                        .build()
                } else {
                    null
                }
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
