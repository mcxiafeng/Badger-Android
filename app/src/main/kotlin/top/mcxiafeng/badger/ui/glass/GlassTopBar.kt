package top.mcxiafeng.badger.ui.glass

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val TopBarTitleWeight = FontWeight.SemiBold

/** Vertical breathing room between the status bar and the pinned action row. */
internal val TopBarTopOffset = BadgerSpacing.md

/** One dp-based tail for both effects; the reference device ends at screen y=439px. */
internal val TopBarBlurTail = BadgerSize.topProgressiveBlurTail

/** The progressive blur needs the bar itself to stay transparent even with flat content buttons. */
internal val LocalTransparentTopBar = staticCompositionLocalOf { false }

@Composable
internal fun TopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surface,
    subtitle: String = "",
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: ScrollBehavior? = null,
    bottomContent: @Composable () -> Unit = {},
) = SmallTopAppBar(title, modifier, color, subtitle, navigationIcon, actions, scrollBehavior, bottomContent)

@Composable
internal fun SmallTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surface,
    subtitle: String = "",
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: ScrollBehavior? = null,
    bottomContent: @Composable () -> Unit = {},
) {
    SideEffect { scrollBehavior?.state?.heightOffsetLimit = 0f }
    Column(modifier.topBarInsets(color)) {
        PinnedTitleRow(navigationIcon, actions) { PinnedTitle(title, subtitle, prominent = false) }
        bottomContent()
    }
}

@Composable
internal fun PageActionBar(
    title: String = "",
    subtitle: String = "",
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    LaunchedEffect(title) { Log.d("GlassTopBarTester", "Pinned title shares action row; title=$title; scrollIndependent=true") }
    Column(Modifier.topBarInsets(Color.Transparent)) {
        PinnedTitleRow(navigationIcon, actions, startAligned = true) {
            PinnedTitle(title, subtitle, prominent = true)
        }
    }
}

@Composable
private fun Modifier.topBarInsets(color: Color): Modifier = this
    .background(if (LocalGlassStyle.current.enabled || LocalTransparentTopBar.current) Color.Transparent else color)
    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
    .padding(top = TopBarTopOffset)
    .fillMaxWidth()

@Composable
private fun PinnedTitleRow(
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    startAligned: Boolean = false,
    title: @Composable () -> Unit,
) {
    Layout(content = {
        Box(Modifier.padding(start = TopAppBarDefaults.NavigationIconPadding)) { navigationIcon() }
        Row(Modifier.padding(end = TopAppBarDefaults.ActionIconPadding),
            horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically,
            content = actions)
        Box(Modifier.padding(horizontal = if (startAligned) BadgerSpacing.sm else TopAppBarDefaults.TitlePadding)) { title() }
    }) { measurables, constraints ->
        val width = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val navigation = measurables[0].measure(loose)
        val actionsPlaceable = measurables[1].measure(loose)
        val titleWidth = (width - navigation.width - actionsPlaceable.width).coerceAtLeast(0)
        val titlePlaceable = measurables[2].measure(loose.copy(maxWidth = titleWidth))
        val height = maxOf(TopAppBarDefaults.CollapsedHeight.roundToPx(), navigation.height,
            actionsPlaceable.height, titlePlaceable.height)
        layout(width, height) {
            navigation.placeRelative(0, (height - navigation.height) / 2)
            actionsPlaceable.placeRelative(width - actionsPlaceable.width, (height - actionsPlaceable.height) / 2)
            val titleX = if (startAligned) navigation.width else ((width - titlePlaceable.width) / 2)
                .coerceIn(navigation.width, maxOf(navigation.width, width - actionsPlaceable.width - titlePlaceable.width))
            titlePlaceable.placeRelative(titleX, (height - titlePlaceable.height) / 2)
        }
    }
}

@Composable
private fun PinnedTitle(title: String, subtitle: String, prominent: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.xs)) {
        Text(title, Modifier.weight(1f, fill = false),
            style = if (prominent) MiuixTheme.textStyles.title1 else MiuixTheme.textStyles.title3,
            fontWeight = TopBarTitleWeight, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle.isNotEmpty()) Text(subtitle, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
