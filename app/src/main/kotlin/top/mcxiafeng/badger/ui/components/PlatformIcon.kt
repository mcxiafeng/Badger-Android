package top.mcxiafeng.badger.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.R
import top.mcxiafeng.badger.ocr.ALL_FIELDS

private val fieldIconMap = mapOf(
    *ALL_FIELDS.map { it.fieldKey to it.iconRes }.toTypedArray(),
    "qqGroup" to R.drawable.ic_qq_group,
    "telegramGroup" to R.drawable.ic_telegram,
)

/**
 * 平台图标组件：1:1 圆角方块底色 + 白色矢量图标
 *
 * @param fieldKey 字段 key（用于匹配图标资源）
 * @param color 平台主题色
 * @param sizeDp 图标尺寸（默认 28dp）
 */
@Composable
fun PlatformIcon(fieldKey: String, color: Color, sizeDp: Float = 28f) {
    val iconRes = fieldIconMap[fieldKey]
        ?: fieldIconMap.entries.firstOrNull { fieldKey.startsWith(it.key) }?.value
        ?: R.drawable.ic_website
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(color)
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = fieldKey,
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier.size((sizeDp * 0.58f).dp)
        )
    }
}
