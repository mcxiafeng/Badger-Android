package top.mcxiafeng.badger.ui.glass

import android.util.Log
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import top.mcxiafeng.badger.ui.designsystem.BadgerAlpha
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.ListPopupColumn as MiuixListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider

/** The library's list already owns vertical scrolling; put its gap inside that content. */
@Composable
internal fun ListPopupColumn(content: @Composable () -> Unit) {
    LaunchedEffect(Unit) { Log.d(GLASS_LOG_TAG, "Menu scroll content top gapDp=${BadgerSpacing.scrollContentTopGap.value}") }
    MiuixListPopupColumn {
        Spacer(Modifier.height(BadgerSpacing.scrollContentTopGap))
        content()
    }
}

/** Shadow sits outside the surface's own clip; host padding reserves its soft outer pixels. */
internal fun Modifier.menuShadow(shape: Shape): Modifier = shadow(
    elevation = BadgerSize.menuShadowElevation,
    shape = shape,
    clip = false,
    ambientColor = Color.Black.copy(alpha = BadgerAlpha.MENU_SHADOW_AMBIENT_ALPHA),
    spotColor = Color.Black.copy(alpha = BadgerAlpha.MENU_SHADOW_SPOT_ALPHA),
)

/** Position the visible menu, rather than its transparent shadow guard, eight dp from the anchor. */
internal class MenuPopupPositionProvider(
    private val shadowOutsetPx: Int,
    private val delegate: PopupPositionProvider = ListPopupDefaults.dropdownPositionProvider(verticalMargin = BadgerSpacing.menuAnchorGap),
) : PopupPositionProvider {
    override fun getMargins(): PaddingValues = delegate.getMargins()

    override fun calculatePosition(
        anchorBounds: IntRect, windowBounds: IntRect, layoutDirection: LayoutDirection,
        popupContentSize: IntSize, popupMargin: IntRect, alignment: PopupPositionProvider.Align,
    ): IntOffset {
        val visibleSize = IntSize((popupContentSize.width - shadowOutsetPx * 2).coerceAtLeast(0),
            (popupContentSize.height - shadowOutsetPx * 2).coerceAtLeast(0))
        val safeWindow = IntRect(windowBounds.left + shadowOutsetPx, windowBounds.top + shadowOutsetPx,
            (windowBounds.right - shadowOutsetPx).coerceAtLeast(windowBounds.left + shadowOutsetPx),
            (windowBounds.bottom - shadowOutsetPx).coerceAtLeast(windowBounds.top + shadowOutsetPx))
        val horizontalAlignment = when (alignment) {
            PopupPositionProvider.Align.End, PopupPositionProvider.Align.TopEnd, PopupPositionProvider.Align.BottomEnd -> PopupPositionProvider.Align.End
            else -> PopupPositionProvider.Align.Start
        }
        val visiblePosition = delegate.calculatePosition(anchorBounds, safeWindow, layoutDirection,
            visibleSize, popupMargin, horizontalAlignment)
        return visiblePosition - IntOffset(shadowOutsetPx, shadowOutsetPx)
    }
}
