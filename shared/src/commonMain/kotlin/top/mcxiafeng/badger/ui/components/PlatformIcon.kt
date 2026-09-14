package top.mcxiafeng.badger.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
expect fun PlatformIcon(fieldKey: String, color: Color, sizeDp: Float = 28f)
