package top.mcxiafeng.badger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val IOS_FALLBACK_TAG = "PlatformIcon.ios"

/**
 * [KMP K13c] iOS actual 骨架：圆角方块底色 + 字段 key 首字母占位。
 * 平台图标 bundle 图片（按 PlatformFieldDef.iconName 映射）在 K16 接线。
 */
@Composable
actual fun PlatformIcon(fieldKey: String, color: Color, sizeDp: Float) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(color)
    ) {
        Text(
            text = fieldKey.take(1).uppercase(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (sizeDp * 0.42f).sp,
        )
    }
}
