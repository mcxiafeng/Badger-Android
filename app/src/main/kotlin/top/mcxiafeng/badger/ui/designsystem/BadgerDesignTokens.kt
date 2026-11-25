package top.mcxiafeng.badger.ui.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

object BadgerDesignUnits {
    const val PER_DP = 3.5f
    const val SURFACE_CORNER_PX = 73f
}

object BadgerRadius {
    val surface: Dp = (BadgerDesignUnits.SURFACE_CORNER_PX / BadgerDesignUnits.PER_DP).dp

    /** 弹出菜单/选项对话框跟随屏幕 R 角：139px ÷ 2.625 密度 ≈ 53dp。 */
    val menu: Dp = 53.dp
    val chip: Dp = 8.dp
    val inner: Dp = 12.dp
    val card: Dp = 16.dp
    val large: Dp = 24.dp

    /** Single icon size for every glass control so toolbar and standalone buttons match. */
    val icon: Dp = 24.dp

    val cardCornerRadius = 16.dp
}

object BadgerMotion {
    const val DURATION_FAST = 150
    const val DURATION_BASE = 300
    const val DURATION_SLOW = 500
}

object BadgerNavIndicator {
    val iconLabelGap = 2.dp
    const val IDLE_SCALE = 0.91f
    const val PRESSED_SCALE = 1.50f
    const val POINTER_BUFFER_MS = 32f
    const val POINTER_SNAP_DISTANCE = 0.002f
    const val MAX_WIDTH_STRETCH = 0.20f
    const val MAX_HEIGHT_SQUASH = 0.25f
    const val FULL_DEFORMATION_SLOTS_PER_SECOND = 12f
    const val IDLE_FILL_ALPHA = 0.19f
    const val ACTIVE_FILL_ALPHA = 0f

    /** 底栏触点光晕：浅浅一层，裁切在底栏胶囊内，位于图标之上、选中框之下。 */
    const val TOUCH_GLOW_ALPHA = 0.20f
    const val TOUCH_GLOW_RADIUS_FACTOR = 1.25f

    /** 选中框到位后的惯性形变：按到达速度变窄变高，再非线性回弹复位。 */
    const val INERTIA_MIN_SLOTS_PER_SECOND = 4f
    const val INERTIA_FULL_SLOTS_PER_SECOND = 14f
    const val INERTIA_WIDTH_SHRINK = 0.09f
    const val INERTIA_HEIGHT_GROWTH = 0.14f
    const val INERTIA_PEAK_MS = 120
}

object BadgerAlpha {
    const val SNACKBAR_SHADOW_ALPHA = 0.1f
    const val TOP_CONTENT_VEIL_ALPHA = 1f
    const val AVATAR_TEXT_ALPHA = 0.85f
    const val COVER_GRADIENT_END_ALPHA = 0.72f
}

object BadgerSize {
    const val COLOR_PALETTE_ROWS = 7
    const val COLOR_PALETTE_HUE_COLUMNS = 12
    const val COLOR_PICKER_CHANNELS = 4
    val colorSliderTrackHeight: Dp = 26.dp
    val colorSliderClipInset: Dp = 0.5.dp
    val colorPaletteGridHeight: Dp = 180.dp
    val colorPalettePreviewHeight: Dp = 26.dp
    val colorPaletteIndicatorRadius: Dp = 10.dp
    val switchTrackWidth: Dp = 49.dp
    val switchTrackHeight: Dp = 28.dp
    val snackbarShadowRadius: Dp = 10.dp
    val snackbarActionMinimum: Dp = 26.dp
    val snackbarDismissSize: Dp = 20.dp
    val snackbarActionTextSize = 15.sp
    val inputFocusBorderWidth: Dp = 2.dp
    val dialogClipSafetyPadding: Dp = 2.dp
    val floatingToolbarDividerWidth: Dp = 0.75.dp
    val contentMaxWidth: Dp = 720.dp
    val minimumTouchTarget: Dp = 48.dp
    val indexRailWidth: Dp = 28.dp
    val indexRowHeight: Dp = 14.dp
    val indexSymbolSize: Dp = 12.dp
    val indexPreviewMinSize: Dp = 64.dp
    val floatingActionClearance: Dp = 80.dp
    val topProgressiveBlurRadius: Dp = 64.dp
    val contactShareQrMaxSize: Dp = 288.dp
    const val contactShareQrHeightFraction = 0.60f
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
    val contactShareQrDarkBackground: Color = Color(0xFFE0E0E0)
}
