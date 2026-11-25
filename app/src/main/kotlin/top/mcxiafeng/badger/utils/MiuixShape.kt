package top.mcxiafeng.badger.utils

import top.mcxiafeng.badger.ui.designsystem.BadgerShapes
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

/**
 * Miuix 主题风格圆角形状。
 * 替代已移除的 top.yukonga.miuix.kmp.theme.miuixShape。
 */
@Stable
fun miuixShape(radius: Dp): Shape = BadgerShapes.rounded(radius)