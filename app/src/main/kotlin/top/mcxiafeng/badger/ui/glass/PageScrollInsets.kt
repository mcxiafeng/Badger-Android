package top.mcxiafeng.badger.ui.glass

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing

/** Merges insets inside a scrolling viewport and adds its single shared content-top gap. */
@Composable
internal fun mergePagePadding(chrome: PaddingValues, extra: PaddingValues = PaddingValues()): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = chrome.calculateStartPadding(direction) + extra.calculateStartPadding(direction),
        end = chrome.calculateEndPadding(direction) + extra.calculateEndPadding(direction),
        top = chrome.calculateTopPadding() + extra.calculateTopPadding() + BadgerSpacing.scrollContentTopGap,
        bottom = chrome.calculateBottomPadding() + extra.calculateBottomPadding(),
    )
}
