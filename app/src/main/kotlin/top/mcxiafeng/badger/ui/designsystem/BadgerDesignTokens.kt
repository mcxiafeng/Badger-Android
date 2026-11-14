package top.mcxiafeng.badger.ui.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Badger 设计 token（移植自 dev 分支 shared/ui/designsystem/BadgerDesignTokens）。
 * 与 [docs/ui-spec.md] 的视觉语义保持一致；后续服务器相关页面移植共用。
 */
object BadgerSpacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp

    val cardPadding: Dp = 16.dp
    val cardGap: Dp = 12.dp
    val coverAvatarGap: Dp = 12.dp
}

object BadgerRadius {
    val chip: Dp = 8.dp
    val inner: Dp = 12.dp
    val card: Dp = 16.dp
    val large: Dp = 24.dp

    val cardCornerRadius = 16.dp
}

object BadgerMotion {
    const val DURATION_FAST = 150
    const val DURATION_BASE = 300
    const val DURATION_SLOW = 500
}

object BadgerAlpha {
    const val AVATAR_TEXT_ALPHA = 0.85f
    const val COVER_GRADIENT_END_ALPHA = 0.72f
}

object BadgerSize {
    val coverHeight: Dp = 148.dp
    val coverAvatarSize: Dp = 56.dp
    val coverSmallAvatarSize: Dp = 40.dp
    val coverIconSize: Dp = 64.dp
}

/**
 * 设计系统扩展色（Miuix colorScheme 没有成功/警告语义）。
 * Light/Dark 取值与 dev 分支 BadgerDesignColors 一致；需要时按色板扩展其余字段。
 */
object BadgerDesignColors {
    val successLight: Color = Color(0xFF16A34A)
    val successDark: Color = Color(0xFF22C55E)
}
