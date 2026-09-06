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

/**
 * FAB / FloatingToolbar 底部避让（U07）。
 *
 * 统一「读取 LocalFloatingBarBottomPadding + 计算 bottom padding」的散落写法：
 * `Modifier.padding(bottom = LocalFloatingBarBottomPadding.current)` 的调用点全部换此处。
 */
@Composable
fun Modifier.badgerBottomBarPadding(): Modifier =
    this.padding(bottom = LocalFloatingBarBottomPadding.current)

/**
 * 主页列表 contentPadding 统一计算（U07）。
 *
 * bottom 恒定追加 LocalFloatingBarBottomPadding（84dp 浮动形态 / 0 经典形态），
 * 页面侧不再手算浮动栏补偿。经典形态（0dp）与浮动形态（84dp）下列表均可滚到底。
 *
 * @param scaffoldTop Scaffold 内容区上内边距（通常 paddingValues.calculateTopPadding()）
 * @param scaffoldBottom Scaffold 内容区下内边距（经典形态下为底栏高度）
 * @param topExtra 页面自己的顶部附加间距
 * @param bottomExtra 页面自己的底部附加间距
 */
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

/**
 * 主页 LazyColumn 容器（U07）。
 *
 * 统一承接列表的 contentPadding 计算（配合 [badgerListContentPadding]），
 * 页面侧不再手算浮动栏补偿。
 */
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
