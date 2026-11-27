package top.mcxiafeng.badger.ui.components

import top.yukonga.miuix.kmp.basic.Card

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import top.mcxiafeng.badger.ui.designsystem.continuousClip as clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val PlatformPageWidth = 88.dp
private val PlatformIconSpacing = 6.dp
private val SelectedPlatformDotSize = 6.dp
private val AddIconSize = 22.dp
private const val PlatformIconSize = 40f
private const val UnselectedIconAlpha = 0.3f

@Composable
internal fun PlatformIconPager(
    platformKeys: List<String>,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    pagerModifier: Modifier = Modifier,
    onAddPlatform: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    Card(modifier = modifier.fillMaxWidth(), insideMargin = PaddingValues(vertical = BadgerSpacing.sm)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Symmetric padding centers the first and last icons as well as the interior pages.
            val pageWidth = minOf(PlatformPageWidth, maxWidth)
            val sidePadding = (maxWidth - pageWidth) / 2
            HorizontalPager(
                state = pagerState,
                pageSize = PageSize.Fixed(pageWidth),
                contentPadding = PaddingValues(horizontal = sidePadding),
                modifier = pagerModifier.fillMaxWidth(),
            ) { page ->
                val fieldKey = platformKeys.getOrNull(page)
                val isCentered = page == pagerState.currentPage
                if (fieldKey != null) {
                    PlatformPagerItem(fieldKey, isCentered) {
                        scope.launch { pagerState.animateScrollToPage(page) }
                    }
                } else if (onAddPlatform != null) {
                    PlatformPagerItem(null, isCentered, onClick = onAddPlatform)
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = BadgerSpacing.xs), contentAlignment = Alignment.Center) {
            Box(Modifier.size(SelectedPlatformDotSize).background(MiuixTheme.colorScheme.primary, CircleShape))
        }
    }
}

@Composable
private fun PlatformPagerItem(fieldKey: String?, isCentered: Boolean, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val label = if (fieldKey == null) "添加" else FIELD_DEF_MAP[fieldKey]?.displayName ?: fieldKey
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).semantics { selected = isCentered },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PlatformIconSpacing),
    ) {
        if (fieldKey == null) {
            Box(
                modifier = Modifier.size(PlatformIconSize.dp).clip(CircleShape)
                    .background(if (isCentered) colors.primary else colors.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加平台", modifier = Modifier.size(AddIconSize),
                    tint = if (isCentered) colors.onPrimary else colors.onSurfaceVariantSummary)
            }
        } else {
            PlatformIcon(fieldKey, sizeDp = PlatformIconSize, shape = CircleShape,
                color = if (isCentered) colors.primary else colors.onSurfaceVariantSummary.copy(alpha = UnselectedIconAlpha))
        }
        Text(label, style = MiuixTheme.textStyles.footnote2, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (isCentered) colors.primary else colors.onSurfaceVariantSummary)
    }
}
