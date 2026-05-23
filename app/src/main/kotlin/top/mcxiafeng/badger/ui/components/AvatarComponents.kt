package top.mcxiafeng.badger.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import top.mcxiafeng.badger.utils.HttpUtil
import top.mcxiafeng.badger.utils.Methods
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
    var avatarBitmap by remember(avatarUrl, avatarPath) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(avatarUrl, avatarPath) {
        avatarBitmap = if (!avatarPath.isNullOrBlank()) {
            Methods.loadAvatarBitmap(avatarPath)
        } else if (!avatarUrl.isNullOrBlank()) {
            HttpUtil.downloadBitmap(avatarUrl, timeoutMs = 5000)
        } else null
    }

    val bgColor = remember(name) {
        avatarColors[abs(name.hashCode()) % avatarColors.size]
    }

    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (transparentBackground) Color.Transparent else bgColor),
        contentAlignment = Alignment.Center
    ) {
        if (avatarBitmap != null) {
            Image(
                bitmap = avatarBitmap!!.asImageBitmap(),
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
