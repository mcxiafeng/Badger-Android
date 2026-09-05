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
import top.mcxiafeng.badger.ocr.ALL_FIELDS
import top.mcxiafeng.badger.shared.R

// [KMP K13c] PlatformFieldDef.iconName（资源名）→ shared R.drawable ID 的显式映射。
// 新增平台字段时在此登记对应 drawable 名。
private val iconResByName: Map<String, Int> = mapOf(
    "ic_phone" to R.drawable.ic_phone,
    "ic_email" to R.drawable.ic_email,
    "ic_wechat" to R.drawable.ic_wechat,
    "ic_qq" to R.drawable.ic_qq,
    "ic_bilibili" to R.drawable.ic_bilibili,
    "ic_xiaohongshu" to R.drawable.ic_xiaohongshu,
    "ic_douyin" to R.drawable.ic_douyin,
    "ic_weibo" to R.drawable.ic_weibo,
    "ic_github" to R.drawable.ic_github,
    "ic_telegram" to R.drawable.ic_telegram,
    "ic_facebook" to R.drawable.ic_facebook,
    "ic_x" to R.drawable.ic_x,
    "ic_website" to R.drawable.ic_website,
)

private val fieldIconMap: Map<String, Int> = buildMap {
    ALL_FIELDS.forEach { def ->
        val id = iconResByName[def.iconName]
        if (id != null) put(def.fieldKey, id)
    }
    put("qqGroup", R.drawable.ic_qq_group)
    put("telegramGroup", R.drawable.ic_telegram)
}

/** [KMP K13c] Android actual：painterResource + shared 模块 drawable。 */
@Composable
actual fun PlatformIcon(fieldKey: String, color: Color, sizeDp: Float) {
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
