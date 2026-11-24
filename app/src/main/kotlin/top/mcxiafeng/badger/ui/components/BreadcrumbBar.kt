package top.mcxiafeng.badger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 面包屑条目。
 *
 * @property path 条目完整路径值（通常是行政区名称）
 * @property text 显示文案，为 null 时显示 [path]
 */
@Immutable
data class BreadcrumbItem(
    val path: String,
    val text: String? = null,
)

/**
 * 层级路径面包屑：展示当前所处层级，点击任一级可回退到该级。
 *
 * **为什么自建**：miuix 锁版本 0.9.3 / miuix-nav 0.9.4 均未提供 BreadcrumbBar，
 * 仅在更高版本源码中存在；按项目规范在 ui/components 提供同语义实现，
 * 保持业务中立、可跨页面复用。
 *
 * 交互：条目标记为按钮语义；高亮项自动滚动到可见区域；路径过长时横向滚动，
 * 不做多行铺开（避免挤压列表首屏）。
 */
@Composable
fun BreadcrumbBar(
    items: List<BreadcrumbItem>,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    highlightIndex: Int = items.lastIndex,
    enabled: Boolean = true,
    itemMaxWidth: Dp = DEFAULT_ITEM_MAX_WIDTH,
) {
    val colors = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val listState = rememberLazyListState()

    // 下钻后把当前层级滚到可见位置,避免长路径里高亮项被截在屏幕外。
    LaunchedEffect(highlightIndex) {
        if (highlightIndex in items.indices) {
            listState.animateScrollToItem(highlightIndex)
        }
    }

    LazyRow(
        modifier = modifier,
        state = listState,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SPACING),
    ) {
        itemsIndexed(items) { index, item ->
            if (index > 0) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.onSurfaceVariantActions,
                    modifier = Modifier.size(SEPARATOR_SIZE),
                )
            }
            val highlighted = enabled && index == highlightIndex
            Box(
                modifier = Modifier
                    .widthIn(max = itemMaxWidth)
                    .clip(CircleShape)
                    .background(
                        if (highlighted) colors.primary.copy(alpha = HIGHLIGHT_ALPHA)
                        else colors.onBackground.copy(alpha = BACKGROUND_ALPHA),
                    )
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClickLabel = item.text ?: item.path,
                        onClick = { onItemClick(index) },
                    )
                    .padding(horizontal = ITEM_HORIZONTAL_PADDING, vertical = ITEM_VERTICAL_PADDING),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item.text ?: item.path,
                    style = textStyles.body2,
                    color = if (highlighted) colors.primary else colors.onBackground,
                    fontWeight = if (highlighted) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val DEFAULT_ITEM_MAX_WIDTH = 160.dp
private val SEPARATOR_SIZE = 16.dp
private val SPACING = 4.dp
private val ITEM_HORIZONTAL_PADDING = 10.dp
private val ITEM_VERTICAL_PADDING = 4.dp
private const val HIGHLIGHT_ALPHA = 0.2f
private const val BACKGROUND_ALPHA = 0.1f
