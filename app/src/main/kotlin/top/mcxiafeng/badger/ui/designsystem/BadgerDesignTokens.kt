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
 * 统一组件尺寸 Token。
 *
 * 这里放组件本身的几何尺寸，而不是布局间距，避免在共享组件里出现重复的裸 dp。
 */
object BadgerSize {
    val iconXs = 16.dp
    val iconSm = 20.dp
    val iconMd = 24.dp
    val avatarMd = 40.dp
    val avatarLg = 64.dp
    val avatarXl = 80.dp
    val controlMd = 36.dp
    val touchTarget = 48.dp
    val bioMinHeight = 96.dp
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
