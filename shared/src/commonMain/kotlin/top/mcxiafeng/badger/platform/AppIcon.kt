package top.mcxiafeng.badger.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * [KMP K13c] 应用图标边界（AboutPage 头像位）。
 * Android actual = shared mipmap ic_launcher；
 * iOS actual = bundle AppIcon 占位（K16 接 UIImage(named:)）。
 */
@Composable
expect fun AppIcon(modifier: Modifier = Modifier)
