package top.mcxiafeng.badger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ContactAvatar(
    name: String,
    avatarPath: String? = null,
    size: Int = 72,
    color: Color = MiuixTheme.colorScheme.primary.copy(alpha = 0.28f),
    modifier: Modifier = Modifier
) {
    val sizeDp = size.dp
    val cs = MiuixTheme.colorScheme

    Box(
        modifier = modifier
            .size(sizeDp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarPath.isNullOrBlank()) {
            AsyncImage(
                model = avatarPath,
                contentDescription = name,
                modifier = Modifier.size(sizeDp)
            )
        } else {
            Text(
                text = name.firstOrNull()?.uppercase() ?: "?",
                style = MiuixTheme.textStyles.title3,
                color = cs.primary
            )
        }
    }
}
