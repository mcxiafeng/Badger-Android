package top.mcxiafeng.badger.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/**
 * [KMP K13c] iOS actual 骨架：AppIcon bundle 图（K16 接 UIImage(named: "AppIcon60x60"))。
 * 当前圆角方块 + "B" 字母占位。
 */
@Composable
actual fun AppIcon(modifier: Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.clip(CircleShape).background(Color(0xFF3482FF)),
    ) {
        Text("B", color = Color.White, fontWeight = FontWeight.Bold)
    }
}
