package top.mcxiafeng.badger.ui.designsystem

import androidx.compose.ui.unit.dp

/**
 * 统一间距 Token
 *
 * 基于 4dp 基准网格，覆盖 Badger 全场景间距需求。
 * 用于替代散落在各页面中的硬编码 dp 值。
 * 特别说明：lgx(20dp) 用于字母标题左侧缩进等需要 16dp 与 24dp 之间的场景。
 */
object BadgerSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val lgx = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

/**
 * 统一圆角 Token
 *
 * 与 miuix Card / Surface 组件的默认圆角对齐。
 */
object BadgerRadius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

/**
 * 统一阴影层级 Token
 *
 * 对应 miuix Surface 的 elevation 语义。
 */
object BadgerElevation {
    val none = 0.dp
    val low = 2.dp
    val medium = 4.dp
    val high = 8.dp
}
