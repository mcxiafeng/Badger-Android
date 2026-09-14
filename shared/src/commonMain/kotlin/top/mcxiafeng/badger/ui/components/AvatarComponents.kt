package top.mcxiafeng.badger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import top.mcxiafeng.badger.platform.ImageFiles
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
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import top.yukonga.miuix.kmp.theme.MiuixTheme
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

    
    
    
    
    
    
    
    val fileStamp = if (avatarPath.isNullOrBlank() || avatarPath.startsWith("http")) 0L
        else ImageFiles.imageFileLastModified(avatarPath)
    val imageModel: Any? = remember(avatarPath, avatarUrl, fileStamp) {
        when {
            
            
            !avatarPath.isNullOrBlank() -> {
                if (ImageFiles.imageFileExists(avatarPath)) avatarPath else (avatarUrl ?: avatarPath)
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
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(imageModel)
                    .memoryCacheKey(imageModel?.let { "$it|$fileStamp" })
                    .build(),
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
