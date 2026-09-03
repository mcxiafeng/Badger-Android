package top.mcxiafeng.badger.pages.settings.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity as Tag
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun TagManagerTopActions(
    isMultiSelect: Boolean,
    onExitMultiSelect: () -> Unit,
    showSortMenu: Boolean,
    onOpenSortMenu: () -> Unit,
    onDismissSortMenu: () -> Unit,
    onSelectSort: (TagSortMode) -> Unit,
    currentSort: TagSortMode,
    onToggleSearch: () -> Unit,
) {
    if (isMultiSelect) {
        TextButton(text = "取消多选", onClick = onExitMultiSelect)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onToggleSearch, modifier = Modifier.size(40.dp)) {
            Icon(imageVector = Icons.Default.Search, contentDescription = "搜索标签")
        }
        IconButton(onClick = onOpenSortMenu, modifier = Modifier.size(40.dp)) {
            Icon(imageVector = Icons.AutoMirrored.Filled.Sort, contentDescription = "切换排序")
        }
    }

    // 排序菜单：用 Miuix OverlayListPopup 自动浮在 TopAppBar 下方、右对齐，
    // 点菜单外 / 系统返回键 / 选中项都会触发 onDismissRequest 关闭。
    val sortEntries = TagSortMode.entries
    OverlayListPopup(
        show = showSortMenu,
        alignment = PopupPositionProvider.Align.End,
        onDismissRequest = { onDismissSortMenu() },
    ) {
        ListPopupColumn {
            sortEntries.forEachIndexed { index, mode ->
                DropdownImpl(
                    text = mode.label,
                    optionSize = sortEntries.size,
                    isSelected = mode == currentSort,
                    index = index,
                    onSelectedIndexChange = {
                        onSelectSort(mode)
                    },
                )
            }
        }
    }
}

@Composable
internal fun TagManagerListRow(
    tag: Tag,
    dateText: String,
    multiSelect: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSetShowDot: (Boolean) -> Unit,
    onClickColor: () -> Unit,
    onClickDelete: () -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    val subtitle = buildString {
        append(if (tag.source == "ai") "AI 推荐 · " else "手动创建 · ")
        append(dateText)
    }

    val baseModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(if (selected) cs.primaryContainer else cs.surfaceVariant)
        .combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
            role = Role.Button,
        )
        .padding(horizontal = 12.dp, vertical = 10.dp)

    Row(
        modifier = baseModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(12.dp).clip(CircleShape).background(tag.colorCompose)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tag.name,
                style = MiuixTheme.textStyles.body1,
                color = cs.onSurface,
            )
            Text(
                text = subtitle,
                style = MiuixTheme.textStyles.footnote2,
                color = cs.onSurfaceVariantSummary,
            )
        }
        if (multiSelect) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onClick() },
                colors = CheckboxDefaults.colors(
                    checkedColor = cs.primary,
                ),
            )
        } else {
            // 紧凑色点开关 + 改色 + 删除
            Switch(
                checked = tag.showDot,
                onCheckedChange = onSetShowDot,
                modifier = Modifier.heightIn(min = 24.dp, max = 32.dp),
            )
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onClickColor,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ColorLens,
                    contentDescription = "改色 ${tag.name}",
                    tint = cs.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(
                onClick = onClickDelete,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除 ${tag.name}",
                    tint = cs.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
internal fun BatchActionBar(
    totalCount: Int,
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onColor: () -> Unit,
    onDelete: () -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    // [修复防御]: 注入 floatingBarBottomPadding，避免被 NavigationBar 浮动遮挡
    val floatingBarBottomPadding = top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.surfaceContainer)
            .padding(
                start = 12.dp,
                end = 12.dp,
                top = 10.dp,
                bottom = 10.dp + floatingBarBottomPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(
            text = if (selectedCount == totalCount) "取消全选" else "全选",
            onClick = if (selectedCount == totalCount) onClear else onSelectAll,
        )
        Spacer(Modifier.weight(1f))
        TextButton(
            text = "改色",
            enabled = selectedCount > 0,
            onClick = onColor,
        )
        TextButton(
            text = "删除",
            enabled = selectedCount > 0,
            onClick = onDelete,
            colors = ButtonDefaults.textButtonColors(
                color = cs.error,
                disabledColor = cs.disabledSecondaryVariant,
                textColor = cs.onError,
                disabledTextColor = cs.disabledOnSecondaryVariant,
            ),
        )
    }
}

@Composable
internal fun TagEmptyState() {
    // 中央只剩引导文字；新建标签交给右下角 FAB，二者不抢视觉。
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "还没有标签",
                style = MiuixTheme.textStyles.title4,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "标签用于在联系人列表中分类与快速识别。\n点击右下角 + 创建第一个标签。",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}
