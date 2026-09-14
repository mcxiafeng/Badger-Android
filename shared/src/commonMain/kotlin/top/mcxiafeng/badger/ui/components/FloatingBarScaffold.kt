package top.mcxiafeng.badger.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding

@Composable
fun Modifier.badgerBottomBarPadding(): Modifier =
    this.padding(bottom = LocalFloatingBarBottomPadding.current)

@Composable
fun badgerListContentPadding(
    scaffoldTop: Dp = 0.dp,
    scaffoldBottom: Dp = 0.dp,
    topExtra: Dp = 0.dp,
    bottomExtra: Dp = 0.dp,
    start: Dp = 0.dp,
    end: Dp = 0.dp,
): PaddingValues {
    val floatingPadding = LocalFloatingBarBottomPadding.current
    return PaddingValues(
        start = start,
        end = end,
        top = scaffoldTop + topExtra,
        bottom = scaffoldBottom + bottomExtra + floatingPadding,
    )
}

@Composable
fun BadgerFloatingBarList(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues,
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        userScrollEnabled = userScrollEnabled,
        content = content,
    )
}
